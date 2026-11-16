# AGENTS.md — 项目协作约定

> 本文件面向在本仓库工作的 AI 编码助手与开发者。**开始任何编码任务前必须先读完本文件，再读 `docs/01-技术选型与开发文档.md`。**

---

## 1. 项目是什么

`vidkeep` 是一个**纯自有内容**的自建视频站：

- 管理员在后台上传视频 → 系统自动转码为 HLS → 前台访客播放
- 单台服务器 + Docker Compose 部署
- Java 后端（Spring Boot 3.3 / JDK 21）+ Vue 3 前端

**唯一权威需求文档**：[`docs/01-技术选型与开发文档.md`](docs/01-技术选型与开发文档.md)。任何与本文件冲突的地方，以该文档为准。

---

## 2. 目录约定

| 路径 | 说明 |
|---|---|
| `docs/` | 设计文档，只增不改历史版本 |
| `video-server/` | Spring Boot 后端，包根 `com.vidkeep` |
| `video-web/` | Vue 3 + TS + Vite 前端 |
| `deploy/` | docker-compose、Nginx 配置、SQL |
| `scripts/` | 构建、备份、迁移脚本 |
| `data/` | **运行时数据，禁止提交**（已在 .gitignore） |

---

## 3. 技术栈与版本（不要擅自升级/替换）

| 层 | 选型 | 版本 |
|---|---|---|
| 语言 | Java | 21 (LTS) |
| 框架 | Spring Boot | 3.3.x |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x |
| 鉴权 | Spring Security + jjwt | 0.12.x |
| 转码 | FFmpeg | 6.1+ |
| 前端 | Vue 3 + TypeScript | 3.4 / 5.4 |
| 构建 | Vite | 5.x |
| UI | Element Plus | 2.8+ |
| 播放器 | ArtPlayer + hls.js | 5.x / 1.5.x |

### 明确禁止引入的组件

| 组件 | 原因 |
|---|---|
| MinIO | 单机项目，本地文件系统足够 |
| RabbitMQ / Kafka | 用 Redis Stream 做转码队列 |
| Elasticsearch | 数据量 < 1000，MySQL LIKE 足够 |
| Spring Cloud / Nacos | 单体应用 |
| WebSocket | 转码进度用轮询即可 |
| TypeScript 之外的 JS 方言、Tailwind | 保持栈简单 |

---

## 4. 编码红线（每条都必须遵守）

### 4.1 安全

- [ ] **所有 SQL 用 `#{}`，禁止 `${}` 拼接**
- [ ] **所有文件路径必须 `normalize()` 后校验是否在 `DATA_DIR` 内**（防路径穿越）
- [ ] **上传文件类型白名单**：`mp4 / mov / mkv / avi / webm / flv`，校验扩展名 + 魔数
- [ ] 密码必须 BCrypt，**禁止明文或 MD5**
- [ ] 管理接口必须 `@PreAuthorize("hasRole('ADMIN')")`
- [ ] 禁止把密钥、密码写进代码或提交到仓库

### 4.2 一致性

- [ ] 所有接口返回统一 `Result<T>`，错误码使用 `ErrorCode` 枚举
- [ ] 业务异常抛 `BizException`，不要抛裸 `RuntimeException`
- [ ] 关键操作必须打日志：上传、合并、转码、删除
- [ ] 数据库字段使用 `snake_case`，Java 字段使用 `camelCase`
- [ ] 时间统一 `Asia/Shanghai`，DATETIME 存本地时间

### 4.3 转码相关

- [ ] 转码必须先 `ffprobe` 探测，**H.264 + MP4 + 码率 ≤ 3Mbps 一律走 REMUX**，不要无脑重编码
- [ ] 转码状态机必须实现：`NONE → PENDING → PROCESSING → DONE/FAILED`，FAILED 可重试 ≤ 3 次
- [ ] 任务必须幂等，失败重试不能产生重复产物
- [ ] Worker 启动时必须回收 `PROCESSING` 超过 2 小时的僵尸任务

### 4.4 前端

- [ ] 所有接口调用走 `src/api/` 封装，不要在组件里裸写 axios
- [ ] 类型定义放 `src/types/`，禁止 `any`（实在需要写 `unknown` + 收窄）
- [ ] 播放进度每 15 秒上报一次，不要每帧上报

---

## 5. 常用命令

### 后端

```bash
cd video-server
./mvnw clean package            # 构建
./mvnw spring-boot:run          # 本地运行
./mvnw test                     # 单元测试
```

### 后端（Windows 本地开发，无需 Docker）

```powershell
# 初始化数据库（首次；自动读取仓库根目录 .env）
powershell -ExecutionPolicy Bypass -File scripts/dev/init-db.ps1
# 启动后端（自动加载 .env、切换到 JDK 21）
powershell -ExecutionPolicy Bypass -File scripts/dev/start-backend.ps1
```

> 本机默认 `java` 可能是老版本，脚本会优先用 `VIDKEEP_JDK21` / `JDK21_HOME`（或自动探测）定位 JDK 21。

### 前端

```bash
cd video-web
npm install
npm run dev                     # 开发服务器 :5173
npm run build                   # 生产构建
npm run typecheck               # 类型检查
```

### 基础设施

```bash
cd deploy
docker compose up -d mysql redis          # 只起数据库
docker compose up -d --build              # 全栈
docker compose logs -f app                # 看后端日志
docker compose down                       # 停止
```

### 数据库

```bash
# 初始化（首次）
docker exec -i vidkeep-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" vidkeep < deploy/sql/init.sql
```

Windows 本机 MySQL（无 Docker）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/init-db.ps1
```

---

## 6. 当前进度

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 环境与骨架、登录 | ✅ 已完成（2026-11-16：骨架 + JWT 登录，本地联调通过） |
| M1 | 分片上传与存储 | ⬜ 待开始 |
| M2 | 转码流水线与 HLS 播放 | ⬜ 待开始 |
| M3 | 管理后台 | ⬜ 待开始 |
| M4 | 部署与加固 | ⬜ 待开始 |

> 每完成一个里程碑，更新此表。

---

## 7. AI 协作方式

### 7.1 上下文加载顺序

1. 本文件（AGENTS.md）
2. `docs/01-技术选型与开发文档.md` 第 1–5 章（需求、选型、架构、数据模型）
3. 当前任务对应的第 6 章接口 + 第 7 章设计

### 7.2 工作方式

- **小步交付**：一次只做一个模块，做完自测再进下一个，不要一次性生成整个项目
- **先约定后实现**：接口先按文档第 6 章定死，再写 Controller/Service
- **每步可验证**：每完成一个接口，提供一条可直接执行的 `curl` 验证命令
- **不要臆造**：文档没写的字段/接口，先提出来确认，不要自行扩展

### 7.3 提示词模板

```
【任务】实现 <里程碑> 的 <模块>
【依据】docs/01-技术选型与开发文档.md 第 <x.y> 节
【约束】遵循 AGENTS.md 编码红线
【产出】Controller / Service / DTO + 关键中文注释 + curl 验证命令
```

---

## 8. 提交规范

```
feat: 新增分片上传初始化接口
fix: 修复合并后 MD5 校验失败
docs: 补充部署章节
refactor: 抽取 StorageService 接口
chore: 更新依赖版本
```

- 一次提交只做一件事
- 提交前确保能编译通过
- **不要提交 `data/`、`node_modules/`、`target/`、`dist/`、`.env`**
