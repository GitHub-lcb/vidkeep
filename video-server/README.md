# video-server — 后端服务

Spring Boot 3.3 + JDK 21 + MyBatis-Plus 3.5.7。实现见 [技术文档](../docs/01-技术选型与开发文档.md)。

## 当前进度

- ✅ **M0**：骨架、统一响应/异常、Security + JWT 登录
- ✅ **M1**：`StorageService` 本地存储、分片上传五接口、秒传/断点续传、合并 MD5、文件魔数校验
- ✅ **M2**：ffprobe 探测、REMUX/TRANSCODE 决策、Redis Stream 队列与 Worker、转码进度/重试/取消、HLS 播放地址、播放进度记忆、自动封面
- ✅ **M3**：视频/分类管理 CRUD、公开列表/详情、级联删除、前台播放所需数据接口
- ⬜ M4：部署与加固（HTTPS、压测、Nginx secure_link 等）

## 目录结构

```
src/main/java/com/vidkeep/
├── VideoServerApplication.java
├── common/        Result / PageResult / ErrorCode / BizException / GlobalExceptionHandler
├── config/        MybatisPlusConfig / WebMvcConfig / SecurityConfig
├── security/      JwtUtil / JwtAuthFilter / LoginUser / 鉴权异常处理
├── infra/
│   ├── storage/   StorageService / LocalStorageService（路径穿越防护）
│   ├── ffmpeg/    FfprobeService / FfmpegService / TranscodeDecider / Profile
│   └── queue/     TranscodeQueue / RedisStreamQueue
└── module/
    ├── auth/      登录、当前用户、登出
    ├── user/      用户实体 / Mapper / Service
    ├── video/     作品与剧集管理、公开列表/详情
    ├── category/  分类 CRUD
    ├── upload/    分片上传（check/init/chunk/status/complete）
    ├── transcode/ 转码任务、进度、Worker、重试/取消
    └── play/      播放地址、播放进度上报/查询
```

## 接口一览

| 分组 | 路径 | 说明 |
|---|---|---|
| 认证 | `POST /api/auth/login`、`GET /api/auth/me`、`POST /api/auth/logout` | JWT 登录 |
| 视频管理 | `GET/POST /api/admin/videos`、`GET/PUT/DELETE /api/admin/videos/{id}`、`PUT /{id}/status`、`DELETE /api/admin/episodes/{id}` | 需 ADMIN |
| 上传 | `POST /api/admin/upload/check|init`、`PUT /upload/{uploadId}/chunk/{index}`、`GET /upload/{uploadId}/status`、`POST /upload/{uploadId}/complete` | 5MB 分片 |
| 转码 | `GET /api/admin/episodes/{id}/transcode`、`POST .../retry`、`POST .../cancel` | 轮询进度 |
| 分类 | `GET /api/public/categories`、`POST/PUT/DELETE /api/admin/categories` | — |
| 前台 | `GET /api/public/videos`、`GET /api/public/videos/{id}` | 仅上线作品 |
| 播放 | `GET /api/public/videos/{videoId}/episodes/{episodeId}/play?deviceId=`、`POST/GET /api/public/play/history` | 免登录 |

统一响应：`{ "code": 0, "message": "ok", "data": ... }`；业务错误同样 HTTP 200，错误码见 `ErrorCode`。

## 转码流水线

```
上传完成 → transcode_task(PENDING) → XADD transcode:queue
Worker(XREADGROUP) → ffprobe 探测
  ├─ H.264 + MP4 + 码率 ≤ 3Mbps → REMUX（-c copy，最快）
  └─ 其它 → TRANSCODE（720P，源 ≥720P 时追加 480P）
→ hls/{episodeId}/{720p|480p}/index.m3u8 + master.m3u8
→ episode DONE / FAILED（可重试 ≤3 次）；PROCESSING 超 2h 启动时回收
```

- 进度：Redis `transcode:progress:{episodeId}` 实时写；DB 每 5% 落库
- 幂等：执行前清理 `hls/{episodeId}`；失败重试不产生重复产物
- 自动封面：作品无封面且 coverMode=AUTO 时截帧到 `covers/{videoId}.jpg`

## 本地运行

前置：JDK 21、MySQL 8（执行过 `deploy/sql/init.sql`）、**Redis ≥ 5.0**（Stream 队列）、FFmpeg。

```powershell
# 仓库根目录执行：自动加载 .env、切换 JDK 21
powershell -ExecutionPolicy Bypass -File scripts/dev/start-backend.ps1
```

必须的环境变量（见仓库根 `.env.example`）：`MYSQL_PASSWORD`、`JWT_SECRET`（≥32 字符）；
FFmpeg 未在 PATH 时设置 `FFMPEG_PATH` / `FFPROBE_PATH` 绝对路径；Redis 不是本机默认端口时改 `REDIS_PORT`。

## 构建与测试

```bash
./mvnw clean package     # 构建 + 全部单元测试（24 个）
./mvnw test
```

## 全链路自测

```bash
# 需要后端已启动 + FFmpeg，覆盖上传/转码/HLS/进度/秒传/级联删除
python scripts/dev/e2e-video-flow.py
```

> 本地开发时后端额外把 `DATA_DIR/hls`、`DATA_DIR/covers` 映射为 `/hls/**`、`/covers/**` 静态资源，
> 方便没有 Nginx 时直接联调；生产环境由 Nginx 分发（文档 8.5）。

## 已知限制（M4/P1 阶段）

- 播放地址签名（防盗链 F-17）未启用，`/hls/**` 当前公开可访问
- 播放统计（F-15）未做，`video.view_count` 暂不增长
- 多码率仅 TRANSCODE 输出 720P+480P；REMUX 输出单档
