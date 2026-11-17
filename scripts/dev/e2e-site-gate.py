#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
vidkeep 站点访问口令（P1 F-18）专项验证脚本。

需要先启动一个开启了 SITE_ACCESS_PASSWORD 的后端实例（建议备用端口，勿影响正在使用的实例）：
  $env:SITE_ACCESS_PASSWORD = "dev-gate"
  java -jar video-server/target/app.jar --server.port=8082

用法：
  python scripts/dev/e2e-site-gate.py --base http://localhost:8082 --password dev-gate
"""

import argparse
import json
import sys
import urllib.error
import urllib.request

DEFAULT_BASE = "http://localhost:8082"
DEFAULT_PASSWORD = "dev-gate"


class HttpResult:
    def __init__(self, status: int, body: bytes, headers: dict):
        self.status = status
        self.body = body
        self.headers = headers

    @property
    def text(self) -> str:
        return self.body.decode("utf-8", "replace")

    @property
    def json(self):
        return json.loads(self.text)


def request(base: str, method: str, path: str, body: bytes = None,
            content_type: str = "application/json", cookie: str = None,
            token: str = None) -> HttpResult:
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = content_type
    if cookie:
        headers["Cookie"] = cookie
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return HttpResult(response.status, response.read(), dict(response.headers))
    except urllib.error.HTTPError as error:
        return HttpResult(error.code, error.read(), dict(error.headers))


def main() -> int:
    parser = argparse.ArgumentParser(description="vidkeep 站点访问口令验证")
    parser.add_argument("--base", default=DEFAULT_BASE)
    parser.add_argument("--password", default=DEFAULT_PASSWORD)
    args = parser.parse_args()

    print("[1/6] 口令状态应为开启")
    status = request(args.base, "GET", "/api/public/site/status")
    payload = status.json
    if payload.get("code") != 0 or not payload["data"]["enabled"]:
        raise SystemExit(f"口令未启用：{payload}")
    print(f"      enabled={payload['data']['enabled']}")

    print("[2/6] 未携带口令访问公开接口应返回 2004")
    blocked = request(args.base, "GET", "/api/public/videos?page=1&size=5")
    if blocked.json.get("code") != 2004:
        raise SystemExit(f"期望 2004，实际 {blocked.text[:200]}")
    print(f"      code={blocked.json['code']} message={blocked.json['message']}")

    print("[3/6] 未携带口令访问 HLS 应 403")
    hls = request(args.base, "GET", "/hls/4/master.m3u8")
    if hls.status != 403:
        raise SystemExit(f"期望 403，实际 {hls.status}")
    print("      HTTP 403 ok")

    print("[4/6] 错误口令应返回 2006")
    wrong = request(args.base, "POST", "/api/public/site/verify",
                    body=json.dumps({"password": "wrong-password"}).encode(), content_type="application/json")
    if wrong.json.get("code") != 2006:
        raise SystemExit(f"期望 2006，实际 {wrong.text[:200]}")
    print(f"      code={wrong.json['code']} message={wrong.json['message']}")

    print("[5/6] 正确口令应通过并下发 cookie")
    correct = request(args.base, "POST", "/api/public/site/verify",
                      body=json.dumps({"password": args.password}).encode(), content_type="application/json")
    if correct.json.get("code") != 0 or not correct.json["data"]["verified"]:
        raise SystemExit(f"口令校验失败：{correct.text[:200]}")
    set_cookie = correct.headers.get("Set-Cookie") or correct.headers.get("set-cookie")
    if not set_cookie or "vidkeep_site_token" not in set_cookie:
        raise SystemExit(f"未下发 cookie：{correct.headers}")
    cookie = set_cookie.split(";")[0]
    print(f"      cookie 已下发（HttpOnly={('HttpOnly' in set_cookie)}）")

    print("[6/6] 带 cookie 访问公开接口/HLS 应放行；管理员 JWT 免口令")
    passed = request(args.base, "GET", "/api/public/videos?page=1&size=5", cookie=cookie)
    if passed.json.get("code") != 0:
        raise SystemExit(f"带 cookie 仍被拦截：{passed.text[:200]}")
    print(f"      公开列表 code=0 total={passed.json['data']['total']}")
    hls_ok = request(args.base, "GET", "/hls/4/master.m3u8", cookie=cookie)
    # master 仍需播放签名，未带签名时 403，但不应再是站点口令拦截（可通过 body code 区分）
    print(f"      带 cookie 且无播放签名访问 HLS -> HTTP {hls_ok.status}（预期 403 播放签名）")

    login = request(args.base, "POST", "/api/auth/login",
                    body=json.dumps({"username": "admin", "password": "admin123"}).encode(),
                    content_type="application/json")
    if login.json.get("code") != 0:
        raise SystemExit(f"登录失败：{login.text[:200]}")
    token = login.json["data"]["token"]
    admin = request(args.base, "GET", "/api/admin/stats", token=token)
    if admin.json.get("code") != 0:
        raise SystemExit(f"管理员免口令访问失败：{admin.text[:200]}")
    print(f"      管理员 stats code=0（JWT 豁免 ok）")

    print("\n✅ 站点口令验证 PASS")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(130)
