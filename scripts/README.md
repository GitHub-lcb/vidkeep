# scripts — 运维与构建脚本

## 目录约定

- `dev/`：**Windows 本地开发脚本**（当前已用）
- 根目录脚本：Linux 服务器运维脚本（M4 阶段补充）

## 本地开发（Windows / PowerShell 5.1+）

| 脚本 | 用途 |
|---|---|
| `dev/init-db.ps1` | 读取 `.env`，执行 `deploy/sql/init.sql`（幂等：建库建表 + 默认管理员） |
| `dev/start-backend.ps1` | 加载 `.env`、切换到 JDK 21、`mvnw spring-boot:run`（:8080） |
| `dev/start-redis-dev.ps1` | 启动便携 Redis（需 ≥5.0，项目用 Redis Stream 做转码队列） |
| `dev/e2e-video-flow.py` | 全链路验证：建作品 → 分片上传/断点续传 → 转码（REMUX/TRANSCODE）→ HLS 播放 → 进度记忆 → 秒传 → 级联删除；P1 起额外校验播放签名/403、切片改写、播放量、统计接口、操作日志 |
| `dev/e2e-site-gate.py` | P1 站点访问口令专项验证（status/2004/403/2006/cookie/JWT 豁免） |
| `dev/common.ps1` | 公共函数：加载 `.env`、定位 JDK 21 |

### 一键跑通本地环境

```powershell
# 1) 便携 Redis（本机已有 Redis 5+ 可跳过；Docker 部署不需要此步）
powershell -ExecutionPolicy Bypass -File scripts/dev/start-redis-dev.ps1

# 2) 初始化数据库（首次）
powershell -ExecutionPolicy Bypass -File scripts/dev/init-db.ps1

# 3) 启动后端（自动加载 .env 并切 JDK 21）
powershell -ExecutionPolicy Bypass -File scripts/dev/start-backend.ps1

# 4) 另开终端启动前端
cd video-web; npm run dev

# 5) 全链路自测（另开终端；需要 FFmpeg，路径由 .env 的 FFMPEG_PATH 指定）
python scripts/dev/e2e-video-flow.py

# 6) P1 站点口令验证（需另起一个开启 SITE_ACCESS_PASSWORD 的实例，如 8082）
python scripts/dev/e2e-site-gate.py --base http://localhost:8082 --password dev-gate
```

### 前置依赖

| 依赖 | 说明 |
|---|---|
| JDK 21 | 脚本优先用 `VIDKEEP_JDK21` / `JDK21_HOME`，否则自动探测常见安装目录 |
| MySQL 8 | `.env` 中 `MYSQL_*`；首次执行 `init-db.ps1` |
| Redis ≥ 5 | 转码队列使用 Redis Stream（`XADD/XREADGROUP`），本机旧版 Redis 需升级或用便携版 |
| FFmpeg 6.1+ | M2 转码必需；未在 PATH 时在 `.env` 填 `FFMPEG_PATH` / `FFPROBE_PATH` 绝对路径 |

> 生产部署使用 Docker Compose（`deploy/docker-compose.yml`），容器内 Redis 为 `redis:7`，FFmpeg 由镜像提供。

## 运维脚本（M4 补充）

| 脚本 | 用途 |
|---|---|
| `deploy.sh` | 一键部署（拉代码 → 构建 → 起容器） |
| `backup.sh` | 备份 MySQL + 数据目录，保留 7 天 |
| `check-env.sh` | 检查服务器环境（Docker、FFmpeg、磁盘空间） |
