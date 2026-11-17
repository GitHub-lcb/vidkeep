#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
vidkeep M1–M3 端到端验证脚本（本地开发用）。

覆盖链路：
  登录 → 分类/作品 → 生成样片 → 抽样指纹 → 秒传检查 → 断点续传（先传 1 片再查 status）
  → 并发补传 → complete → 轮询转码进度 → 公共列表/详情 → 播放地址 → HLS master 可访问
  → 播放进度上报/查询/恢复 → 秒传验证 → 级联删除验证

用法（仓库根目录）：
  python scripts/dev/e2e-video-flow.py
  python scripts/dev/e2e-video-flow.py --ffmpeg "C:/tools/ffmpeg/bin/ffmpeg.exe" --keep

依赖：Python 3.9+（标准库），本机 ffmpeg。
"""

import argparse
import concurrent.futures
import hashlib
import json
import os
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request
from typing import Any, Optional

DEFAULT_BASE = "http://localhost:8080"
DEFAULT_USER = "admin"
DEFAULT_PASSWORD = "admin123"
CHUNK_STEP = 2 * 1024 * 1024  # 抽样指纹的块大小


class ApiError(RuntimeError):
    def __init__(self, path: str, code: int, message: str):
        super().__init__(f"{path} -> code={code}, message={message}")
        self.code = code


def http_json(base: str, method: str, path: str, body: Optional[dict] = None,
              token: Optional[str] = None, raw_body: Optional[bytes] = None,
              content_type: str = "application/json", timeout: int = 120) -> Any:
    url = base + path
    data = None
    headers = {"Accept": "application/json"}
    if raw_body is not None:
        data = raw_body
        headers["Content-Type"] = content_type
    elif body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8", "replace")
        raise RuntimeError(f"{method} {path} -> HTTP {error.code}: {text}") from error
    if payload.get("code") != 0:
        raise ApiError(path, payload.get("code"), payload.get("message", ""))
    return payload.get("data")


def http_status(base: str, path: str) -> tuple[int, str]:
    with urllib.request.urlopen(base + path, timeout=30) as response:
        return response.status, response.read(512).decode("utf-8", "replace")


def http_code(base: str, path: str) -> int:
    """返回 HTTP 状态码，不抛异常（用于断言 403/404）。"""
    try:
        with urllib.request.urlopen(base + path, timeout=30) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def sample_fingerprint(path: str) -> str:
    """与文档 7.1.2 / 前端 utils/hash.ts 一致的抽样指纹。"""
    size = os.path.getsize(path)
    with open(path, "rb") as handle:
        head = handle.read(CHUNK_STEP)
        handle.seek(size // 2)
        middle = handle.read(CHUNK_STEP)
        handle.seek(max(0, size - CHUNK_STEP))
        tail = handle.read(CHUNK_STEP)
    digest = hashlib.sha256()
    digest.update(head)
    digest.update(middle)
    digest.update(tail)
    digest.update(struct.pack(">d", float(size)))
    return digest.hexdigest()


def locate_ffmpeg(explicit: Optional[str]) -> str:
    if explicit:
        return explicit
    env_path = os.environ.get("FFMPEG_PATH")
    if env_path and os.path.isfile(env_path):
        return env_path
    tools_root = os.environ.get("VIDKEEP_TOOLS", r"E:\develop-lcb\tools")
    if os.path.isdir(tools_root):
        for root, _dirs, files in os.walk(tools_root):
            if "ffmpeg.exe" in files:
                return os.path.join(root, "ffmpeg.exe")
    raise SystemExit("未找到 ffmpeg.exe，请用 --ffmpeg 指定，或在 .env 设置 FFMPEG_PATH")


def ensure_sample(ffmpeg: str, sample: str, duration: int, crf: str, size: str) -> None:
    if os.path.isfile(sample) and os.path.getsize(sample) > 1024:
        print(f"[sample] 复用已有样片 {sample} ({os.path.getsize(sample)} bytes)")
        return
    os.makedirs(os.path.dirname(sample), exist_ok=True)
    print(f"[sample] 生成 {duration}s / {size} / H.264 样片 ...")
    command = [
        ffmpeg, "-y",
        "-f", "lavfi", "-i", f"testsrc2=duration={duration}:size={size}:rate=25",
        "-f", "lavfi", "-i", f"sine=frequency=440:sample_rate=44100:duration={duration}",
        "-c:v", "libx264", "-preset", "veryfast", "-crf", crf, "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "128k", "-shortest",
        sample,
    ]
    result = subprocess.run(command, capture_output=True)
    if result.returncode != 0 or not os.path.isfile(sample):
        raise SystemExit("样片生成失败：" + result.stderr.decode("utf-8", "replace")[-500:])
    print(f"[sample] 已生成 {sample} ({os.path.getsize(sample)} bytes)")


def upload_chunks(base: str, token: str, upload_id: str, content: bytes, chunk_size: int, indices: list[int]) -> None:
    def put(index: int) -> None:
        start = index * chunk_size
        piece = content[start:start + chunk_size]
        http_json(base, "PUT", f"/api/admin/upload/{upload_id}/chunk/{index}",
                  token=token, raw_body=piece, content_type="application/octet-stream")

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        futures = [pool.submit(put, index) for index in indices]
        for future in concurrent.futures.as_completed(futures):
            future.result()


def poll_transcode(base: str, token: str, episode_id: int, timeout_seconds: int = 300) -> dict:
    deadline = time.time() + timeout_seconds
    last: dict = {}
    max_percent = -1
    while time.time() < deadline:
        progress = http_json(base, "GET", f"/api/admin/episodes/{episode_id}/transcode", token=token)
        if progress != last:
            print(f"[transcode] status={progress.get('status')} progress={progress.get('progress')}% "
                  f"mode={progress.get('mode')} err={progress.get('errorMsg')}")
            last = progress
        current_percent = int(progress.get("progress") or 0)
        if current_percent + 2 < max_percent:
            raise SystemExit(f"转码进度回退：{current_percent}% < 历史最高 {max_percent}%")
        max_percent = max(max_percent, current_percent)
        status = progress.get("status")
        if status in ("DONE", "FAILED"):
            return progress
        time.sleep(2)
    raise SystemExit(f"转码超时，最后状态：{last}")


def main() -> int:
    parser = argparse.ArgumentParser(description="vidkeep M1–M3 端到端验证")
    parser.add_argument("--base", default=os.environ.get("VIDKEEP_BASE", DEFAULT_BASE))
    parser.add_argument("--user", default=DEFAULT_USER)
    parser.add_argument("--password", default=DEFAULT_PASSWORD)
    parser.add_argument("--ffmpeg", default=None)
    parser.add_argument("--sample", default=os.path.join(os.getcwd(), "data", "e2e", "sample.mp4"))
    parser.add_argument("--duration", type=int, default=20, help="样片时长（秒）")
    parser.add_argument("--crf", default="18", help="样片 x264 CRF，越大码率越低")
    parser.add_argument("--size", default="1280x720", help="样片分辨率，如 640x360")
    parser.add_argument("--expect-mode", choices=["REMUX", "TRANSCODE"], default=None,
                        help="断言转码方式：REMUX 需 H.264+MP4+≤3Mbps，TRANSCODE 为其余情况")
    parser.add_argument("--keep", action="store_true", help="验证后不删除作品")
    args = parser.parse_args()

    started = time.time()
    ffmpeg = locate_ffmpeg(args.ffmpeg)
    ensure_sample(ffmpeg, args.sample, args.duration, args.crf, args.size)

    print("[1/9] 登录")
    login = http_json(args.base, "POST", "/api/auth/login",
                      body={"username": args.user, "password": args.password})
    token = login["token"]
    print(f"      token ok, role={login['user']['role']}")

    print("[2/9] 分类与作品")
    categories = http_json(args.base, "GET", "/api/public/categories")
    if categories:
        category_id = categories[0]["id"]
    else:
        category_id = http_json(args.base, "POST", "/api/admin/categories",
                                body={"name": "E2E 分类", "sort": 99}, token=token)
    title = f"E2E 测试视频 {time.strftime('%H:%M:%S')}"
    video_id = http_json(args.base, "POST", "/api/admin/videos",
                         body={"title": title, "description": "端到端验证脚本创建", "categoryId": category_id},
                         token=token)
    print(f"      videoId={video_id}, categoryId={category_id}")

    print("[3/9] 抽样指纹与秒传检查")
    fingerprint = sample_fingerprint(args.sample)
    file_size = os.path.getsize(args.sample)
    check = http_json(args.base, "POST", "/api/admin/upload/check",
                      body={"fingerprint": fingerprint, "fileName": "sample.mp4", "fileSize": file_size},
                      token=token)
    print(f"      exists={check.get('exists')}, uploadId={check.get('uploadId')}")
    if check.get("exists"):
        print("      第一次上传就命中秒传（可能是历史数据），先删除该剧集再继续")
        episode_id = check["episodeId"]
        http_json(args.base, "DELETE", f"/api/admin/episodes/{episode_id}", token=token)

    print("[4/9] 初始化上传会话")
    init = http_json(args.base, "POST", "/api/admin/upload/init",
                     body={"fileName": "sample.mp4", "fileSize": file_size, "fingerprint": fingerprint},
                     token=token)
    upload_id, chunk_size, total_chunks = init["uploadId"], init["chunkSize"], init["totalChunks"]
    print(f"      uploadId={upload_id}, chunkSize={chunk_size}, totalChunks={total_chunks}")

    print("[5/9] 断点续传：先传第 0 片，再查询 status")
    with open(args.sample, "rb") as handle:
        content = handle.read()
    upload_chunks(args.base, token, upload_id, content, chunk_size, [0])
    status = http_json(args.base, "GET", f"/api/admin/upload/{upload_id}/status", token=token)
    if status["uploadedChunks"] != [0]:
        raise SystemExit(f"断点续传校验失败，uploadedChunks={status['uploadedChunks']}")
    print(f"      status.uploadedChunks={status['uploadedChunks']} ok，并发补传其余分片")
    upload_chunks(args.base, token, upload_id, content, chunk_size, list(range(1, total_chunks)))

    print("[6/9] 完成上传（coverMode=AUTO）")
    complete = http_json(args.base, "POST", f"/api/admin/upload/{upload_id}/complete",
                         body={"videoId": video_id, "title": "E2E 第一集", "coverMode": "AUTO"}, token=token)
    episode_id = complete["episodeId"]
    print(f"      episodeId={episode_id}, transcodeStatus={complete['transcodeStatus']}")

    print("[7/9] 轮询转码进度")
    progress = poll_transcode(args.base, token, episode_id)
    if progress.get("status") != "DONE":
        raise SystemExit(f"转码失败：{progress}")
    if args.expect_mode and progress.get("mode") != args.expect_mode:
        raise SystemExit(f"转码方式不符合预期：期望 {args.expect_mode}，实际 {progress.get('mode')}")
    print(f"      转码完成，mode={progress.get('mode')}")

    print("[8/9] 前台列表/详情/播放/HLS/进度记忆")
    public_page = http_json(args.base, "GET", f"/api/public/videos?page=1&size=50&keyword=E2E")
    if not any(item["id"] == video_id for item in public_page["records"]):
        raise SystemExit("公共列表未找到刚创建的作品")
    detail = http_json(args.base, "GET", f"/api/public/videos/{video_id}")
    if not detail["episodes"] or detail["episodes"][0]["transcodeStatus"] != "DONE":
        raise SystemExit("详情接口中剧集转码状态异常")
    device_id = "web-e2e-" + str(int(time.time()))
    play = http_json(args.base, "GET",
                     f"/api/public/videos/{video_id}/episodes/{episode_id}/play?deviceId={device_id}")
    print(f"      play.url={play['url']}, duration={play['duration']}, lastPosition={play['lastPosition']}")
    if play["lastPosition"] != 0:
        raise SystemExit("首次播放 lastPosition 应为 0")

    print("      [P1] 校验播放地址签名与 HLS 全链路改写")
    if "expire=" not in play["url"] or "token=" not in play["url"]:
        raise SystemExit(f"播放地址未带签名：{play['url']}")
    unsigned_code = http_code(args.base, f"/hls/{episode_id}/master.m3u8")
    if unsigned_code != 403:
        raise SystemExit(f"未签名访问 master.m3u8 应返回 403，实际 {unsigned_code}")
    status_code, master_body = http_status(args.base, play["url"])
    if status_code != 200 or "#EXTM3U" not in master_body:
        raise SystemExit(f"签名 master 不可访问：HTTP {status_code}, body={master_body[:100]}")
    child_lines = [line.strip() for line in master_body.splitlines() if line.strip() and not line.startswith("#")]
    if not child_lines:
        raise SystemExit("master.m3u8 中没有子列表")
    print(f"      master 子列表已改写签名：{child_lines[0][:80]}...")
    child_code, child_body = http_status(args.base, child_lines[0])
    if child_code != 200 or "#EXTM3U" not in child_body:
        raise SystemExit(f"子列表不可访问：HTTP {child_code}")
    segment_lines = [line.strip() for line in child_body.splitlines() if line.strip() and not line.startswith("#")]
    if not segment_lines:
        raise SystemExit("子列表中没有切片")
    print(f"      切片地址已改写签名：{segment_lines[0][:80]}...")
    segment_code, _ = http_status(args.base, segment_lines[0])
    if segment_code != 200:
        raise SystemExit(f"切片不可访问：HTTP {segment_code}")

    print("      [P1] 校验播放量自增与后台统计")
    detail_before = http_json(args.base, "GET", f"/api/admin/videos/{video_id}", token=token)
    view_count = detail_before.get("viewCount") or 0
    stats = http_json(args.base, "GET", "/api/admin/stats", token=token)
    print(f"      viewCount={view_count}, totalViewCount={stats['totalViewCount']}, "
          f"watchSeconds={stats['totalWatchSeconds']}, transcoding={stats['transcodingCount']}")
    if view_count < 1:
        raise SystemExit("播放后 viewCount 未自增")
    if stats["totalViewCount"] < 1:
        raise SystemExit("统计接口 totalViewCount 异常")

    http_json(args.base, "POST", "/api/public/play/history",
              body={"deviceId": device_id, "videoId": video_id, "episodeId": episode_id,
                    "position": 5, "duration": play["duration"] or 20})
    history = http_json(args.base, "GET", f"/api/public/play/history?deviceId={device_id}&videoId={video_id}")
    if not history or history[0]["position"] != 5:
        raise SystemExit(f"播放进度查询异常：{history}")
    play_again = http_json(args.base, "GET",
                           f"/api/public/videos/{video_id}/episodes/{episode_id}/play?deviceId={device_id}")
    if play_again["lastPosition"] != 5:
        raise SystemExit(f"进度恢复失败：{play_again['lastPosition']}")
    print(f"      进度上报/查询/恢复 ok（lastPosition={play_again['lastPosition']}）")

    admin_detail = http_json(args.base, "GET", f"/api/admin/videos/{video_id}", token=token)
    cover_url = admin_detail.get("coverUrl")
    if not cover_url:
        raise SystemExit("未生成自动封面（coverMode=AUTO）")
    cover_status, _ = http_status(args.base, cover_url)
    if cover_status != 200:
        raise SystemExit(f"封面不可访问：{cover_url} -> HTTP {cover_status}")
    print(f"      自动封面 ok：{cover_url}")

    print("[9/9] 秒传验证、操作日志与级联删除")
    check_again = http_json(args.base, "POST", "/api/admin/upload/check",
                            body={"fingerprint": fingerprint, "fileName": "sample.mp4", "fileSize": file_size},
                            token=token)
    if not check_again.get("exists") or check_again.get("episodeId") != episode_id:
        raise SystemExit(f"秒传验证失败：{check_again}")
    print(f"      秒传命中 episodeId={check_again['episodeId']}")

    logs = http_json(args.base, "GET", "/api/admin/operation-logs?page=1&size=20", token=token)
    actions = [item["action"] for item in logs.get("records", [])]
    if not actions:
        raise SystemExit("操作日志为空（P1 F-19 审计未生效）")
    print(f"      [P1] 操作日志 {len(actions)} 条，最近动作：{actions[:5]}")

    if args.keep:
        print(f"      保留作品 videoId={video_id}（--keep）")
    else:
        http_json(args.base, "DELETE", f"/api/admin/videos/{video_id}", token=token)
        try:
            http_json(args.base, "GET", f"/api/admin/videos/{video_id}", token=token)
            raise SystemExit("删除后仍能查到作品")
        except ApiError as error:
            if error.code != 3001:
                raise
        print("      级联删除 ok（作品 3001）")
        logs_after = http_json(args.base, "GET", "/api/admin/operation-logs?page=1&size=20", token=token)
        if not any(item["action"] == "删除视频" for item in logs_after.get("records", [])):
            raise SystemExit("删除操作未写入审计日志")
        print("      [P1] 删除视频已写入审计日志")

    elapsed = time.time() - started
    print(f"\n✅ E2E PASS，用时 {elapsed:.1f}s")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ApiError as error:
        print(f"\n❌ API 失败：{error}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
