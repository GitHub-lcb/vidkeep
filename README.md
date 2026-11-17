# vidkeep

> 自建视频站：Spring Boot 3 + Vue 3，分片上传 · FFmpeg 转码 · HLS 播放 · 管理后台

一个**纯自有内容**的自建视频站。管理员在后台上传视频，系统自动转码为 HLS，访客可在网页端直接观看，支持断点续传、进度记忆与移动端自适应。

不接入任何第三方采集源，单机 Docker Compose 部署。

---

## 功能特性

### 已实现（M0–M3 + P1）

- 🔐 **管理员登录** — JWT 鉴权，后台受保护
- 📤 **分片上传** — 5MB 分片、断点续传、秒传去重，支持 3GB+ 大文件
- ⚙️ **自动转码** — FFmpeg 探测源片，H.264 走 remux（无损秒转），其余重编码为 HLS
- 🎬 **HLS 播放** — ArtPlayer + hls.js，支持拖动、倍速、全屏；TRANSCODE 输出 720P+480P 自动切换
- ⏱ **进度记忆** — 本地 + 服务端双写，刷新/重进从中断处继续
- 📚 **作品/剧集** — 一个作品可挂多个视频文件（多集/多 P）
- 🗂 **分类管理** — 分类 CRUD，前台按类筛选；标题搜索
- 📊 **P1 播放统计** — 播放次数、观看时长、后台仪表盘
- 🔗 **P1 防盗链** — 播放地址 HMAC 签名，m3u8/切片全链路校验
- 🔑 **P1 站点口令** — 内网场景全站访问口令（管理员不受影响）
- 📝 **P1 操作日志** — 管理员写操作自动审计 + 后台审计页
- 📱 **响应式** — 手机、平板、桌面均可正常观看

### 明确不做

- ❌ 聚合源 / 第三方采集 / m3u8 代理
- ❌ 用户注册 / 评论 / 弹幕 / 付费
- ❌ 分布式部署、多租户

---

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端 | Java + Spring Boot + MyBatis-Plus | JDK 21 / 3.3.x / 3.5.7 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x |
| 转码 | FFmpeg | 6.1+ |
| 前端 | Vue 3 + TypeScript + Vite | 3.4 / 5.4 / 5.x |
| UI | Element Plus | 2.8+ |
| 播放器 | ArtPlayer + hls.js | 5.x / 1.5.x |
| 部署 | Docker + Docker Compose + Nginx | — |

> 详细选型理由、版本锁定、放弃某些组件的说明见 [docs/01-技术选型与开发文档.md](docs/01-技术选型与开发文档.md) 第 3 章。

---

## 目录结构

```
vidkeep/
├── docs/                    # 设计文档
│   └── 01-技术选型与开发文档.md
├── video-server/            # Spring Boot 后端
├── video-web/               # Vue 3 前端
├── deploy/
│   ├── docker-compose.yml   # 一键启动
│   ├── nginx/conf.d/        # Nginx 站点配置
│   └── sql/init.sql         # 建表脚本
├── scripts/                 # 运维/构建脚本
├── .env.example             # 环境变量模板
└── AGENTS.md                # AI 协作约定（开发前必读）
```

---

## 快速开始

### 前置要求

- JDK 21
- Node.js 18+
- Docker + Docker Compose（部署）
- MySQL 8、Redis ≥5.0（转码队列用 Redis Stream）、FFmpeg 6.1+（本地运行必需）

### 1. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，至少修改 MYSQL_ROOT_PASSWORD / JWT_SECRET / PLAY_TOKEN_SECRET
```

### 2. 启动基础设施

```bash
cd deploy
docker compose up -d mysql redis
```

### 3. 启动后端

```bash
cd video-server
./mvnw spring-boot:run
```

后端默认监听 `http://localhost:8080`。

### 4. 启动前端

```bash
cd video-web
npm install
npm run dev
```

前端默认监听 `http://localhost:5173`，已配置代理转发 `/api` 到后端。

### 5. 全栈一键启动（可选）

```bash
cd deploy
docker compose up -d --build
```

### 6. Windows 本地开发（无 Docker）

本机已装 MySQL 8 / Redis ≥5 / JDK 21 / FFmpeg 时，可直接用开发脚本（脚本自动读取 `.env`、切换 JDK 21）：

```powershell
# 1) 配置环境变量（JWT_SECRET 至少 32 字符；JDK21_HOME、FFMPEG_PATH 可选）
Copy-Item .env.example .env

# 2) 便携 Redis（需要 ≥5.0；本机已有合格 Redis 可跳过，用 .env 的 REDIS_PORT 指定）
powershell -ExecutionPolicy Bypass -File scripts/dev/start-redis-dev.ps1

# 3) 建库建表 + 默认管理员（admin / admin123）
powershell -ExecutionPolicy Bypass -File scripts/dev/init-db.ps1

# 4) 启动后端 → http://localhost:8080（未在 PATH 的 FFmpeg 用 .env 的 FFMPEG_PATH 绝对路径）
powershell -ExecutionPolicy Bypass -File scripts/dev/start-backend.ps1

# 5) 另开终端启动前端 → http://localhost:5173
cd video-web
npm install
npm run dev
```

### 7. M1–M3 全链路自测

```bash
# 需要后端已启动 + FFmpeg；覆盖：建作品→分片上传/断点续传→转码（REMUX/TRANSCODE）→
# HLS 播放→播放进度记忆→秒传→级联删除
python scripts/dev/e2e-video-flow.py

# 断言指定转码方式（H.264+MP4+≤3Mbps 应走 REMUX）
python scripts/dev/e2e-video-flow.py --expect-mode REMUX --duration 12 --crf 30 --size 854x480
```

基础接口自测：

```bash
# 登录成功 → code 0 + token
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}"

# 未登录访问 → code 2001
curl -s http://localhost:8080/api/auth/me
```

---

## 文档

| 文档 | 说明 |
|---|---|
| [01-技术选型与开发文档](docs/01-技术选型与开发文档.md) | **唯一开发依据**：需求、选型、架构、数据模型、接口、核心设计、部署、计划 |
| [02-P1功能设计说明](docs/02-P1功能设计说明.md) | P1 实现口径：播放签名/站点口令/操作日志/播放统计 |
| [03-前端设计规范](docs/03-前端设计规范.md) | （历史版本）v1 深色方案，已被 04 取代 |
| [04-前端设计规范-Qoder](docs/04-前端设计规范-Qoder.md) | **当前生效**：Qoder CN 体系（forest 深浅主题、令牌、组件、布局、验收） |
| [AGENTS.md](AGENTS.md) | AI 协作约定、编码红线、命令速查 |

---

## 开发路线图

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 环境与骨架、登录 | ✅ 已完成（2026-11-16） |
| M1 | 分片上传与存储 | ✅ 已完成（2026-11-16） |
| M2 | 转码流水线与 HLS 播放 | ✅ 已完成（2026-11-16） |
| M3 | 管理后台 | ✅ 已完成（2026-11-16） |
| P1 | 播放统计/防盗链/站点口令/操作日志 | ✅ 已完成（2026-11-17，见 [P1 设计说明](docs/02-P1功能设计说明.md)） |
| M4 | 部署与加固 | ⬜ 待开始 |

---

## 部署

测试阶段可使用 **Oracle Cloud Always Free**（4 ARM 核 / 24GB / 200GB，永久免费）；验证通过后迁移到 Contabo 日本/新加坡 VPS（约 $9–16/月）。

详见 [技术文档第 8 章](docs/01-技术选型与开发文档.md#8-部署方案)。

---

## 许可证

暂未指定。如需开源，建议 MIT。
