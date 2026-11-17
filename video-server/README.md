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

## P1 能力（2026-11-17）

| 功能 | 实现 | 接口/入口 |
|---|---|---|
| F-17 防盗链 | `PlayTokenService`（HMAC 签名）+ `HlsStreamController`（/hls 全链路校验、m3u8 子列表/切片改写；未签名 HTTP 403） | 播放接口返回带 `?expire=&token=` 的地址 |
| F-18 站点访问口令 | `SiteAccessService` + `SiteAccessFilter`（cookie 门禁，管理员 JWT 豁免） | `GET /api/public/site/status`、`POST /api/public/site/verify` |
| F-19 操作日志 | `admin_operation_log` 表 + `OperationLogAspect` 自动审计 + 查询接口 | `GET /api/admin/operation-logs` |
| F-15 播放统计 | 播放接口 `view_count+1`；观看时长按 `play_history.duration` 汇总 | `GET /api/admin/stats` |

详见 [docs/02-P1功能设计说明.md](../docs/02-P1功能设计说明.md)。新增环境变量：

| 变量 | 说明 | 默认 |
|---|---|---|
| `PLAY_TOKEN_SECRET` | 播放签名密钥（≥32 字节；未配置回退 JWT_SECRET） | — |
| `PLAY_TOKEN_EXPIRE_SECONDS` | master 签名有效期 | 7200 |
| `PLAY_SEGMENT_EXPIRE_SECONDS` | 切片签名有效期 | 43200 |
| `SITE_ACCESS_PASSWORD` | 站点口令，留空=关闭 | 空 |
| `SITE_TOKEN_SECRET` | 口令 cookie 签名密钥 | 回退 JWT_SECRET |

> P1 起 `/hls/**` 由后端签名校验与改写，生产 Nginx 需把 `/hls/` 反代到 app（见 `deploy/nginx/conf.d/vidkeep.conf`）。

## 已知限制

- 播放签名为 2 小时有效（切片 12 小时），过期需重新进入播放页获取新地址
- 播放次数为粗口径（刷新会累计）；观看时长为「每设备每集最后一次时长」汇总
- 审计日志只增不删，后续 M4 可加归档清理任务
- 站点口令为单一共享口令，不支持多账号
