# vidkeep

> 自建视频站：Spring Boot 3 + Vue 3，分片上传 · FFmpeg 转码 · HLS 播放 · 管理后台

一个**纯自有内容**的自建视频站。管理员在后台上传视频，系统自动转码为 HLS，访客可在网页端直接观看，支持断点续传、进度记忆与移动端自适应。

不接入任何第三方采集源，单机 Docker Compose 部署。

---

## 功能特性

### 已规划（MVP）

- 🔐 **管理员登录** — JWT 鉴权，后台受保护
- 📤 **分片上传** — 5MB 分片、断点续传、秒传去重，支持 3GB+ 大文件
- ⚙️ **自动转码** — FFmpeg 探测源片，H.264 走 remux（无损秒转），其余重编码为 HLS
- 🎬 **HLS 播放** — ArtPlayer + hls.js，支持拖动、倍速、全屏
- ⏱ **进度记忆** — 本地 + 服务端双写，刷新/重进从中断处继续
- 📚 **作品/剧集** — 一个作品可挂多个视频文件（多集/多 P）
- 🗂 **分类管理** — 分类 CRUD，前台按类筛选
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
- Docker + Docker Compose
- FFmpeg 6.1+（本地开发建议安装）

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

---

## 文档

| 文档 | 说明 |
|---|---|
| [01-技术选型与开发文档](docs/01-技术选型与开发文档.md) | **唯一开发依据**：需求、选型、架构、数据模型、接口、核心设计、部署、计划 |
| [AGENTS.md](AGENTS.md) | AI 协作约定、编码红线、命令速查 |

---

## 开发路线图

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 环境与骨架、登录 | ⬜ 待开始 |
| M1 | 分片上传与存储 | ⬜ 待开始 |
| M2 | 转码流水线与 HLS 播放 | ⬜ 待开始 |
| M3 | 管理后台 | ⬜ 待开始 |
| M4 | 部署与加固 | ⬜ 待开始 |

---

## 部署

测试阶段可使用 **Oracle Cloud Always Free**（4 ARM 核 / 24GB / 200GB，永久免费）；验证通过后迁移到 Contabo 日本/新加坡 VPS（约 $9–16/月）。

详见 [技术文档第 8 章](docs/01-技术选型与开发文档.md#8-部署方案)。

---

## 许可证

暂未指定。如需开源，建议 MIT。
