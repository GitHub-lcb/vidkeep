# video-server — 后端服务

Spring Boot 3.3 + JDK 21 + MyBatis-Plus 3.5.7。实现见 [技术文档第 4.3 节](../docs/01-技术选型与开发文档.md)。

## 当前进度

- ✅ **M0 已完成**：项目骨架、统一响应/错误码/全局异常、MyBatis-Plus、Spring Security + JWT、登录 `/api/auth/login`、当前用户 `/api/auth/me`
- ⬜ M1 分片上传（`infra/storage`、`module/upload`）
- ⬜ M2 转码与播放（`infra/ffmpeg`、`infra/queue`、`module/transcode`、`module/play`）
- ⬜ M3 管理后台所需接口（`module/video`、`module/category`）

## 目录结构（M0）

```
src/main/java/com/vidkeep/
├── VideoServerApplication.java
├── common/        Result / PageResult / ErrorCode / BizException / GlobalExceptionHandler
├── config/        MybatisPlusConfig / WebMvcConfig / SecurityConfig
├── security/      JwtUtil / JwtAuthFilter / LoginUser / 鉴权异常处理
├── module/
│   ├── auth/      登录、当前用户、登出
│   └── user/      用户实体 / Mapper / Service
└── resources/     application.yml + dev / prod / worker
```

> `infra/`（storage / ffmpeg / queue）与 `module/upload|transcode|play|video|category` 随 M1–M3 逐步补齐。
> Redis 使用 Spring Boot 自动配置的 `StringRedisTemplate` / `RedisTemplate`，M1 需要自定义序列化时再补 `RedisConfig`。

## 关键依赖版本

| 依赖 | 版本 |
|---|---|
| JDK | 21 |
| Spring Boot | 3.3.5 |
| MyBatis-Plus | 3.5.7（SQL 解析用 jsqlparser 4.9） |
| jjwt | 0.12.6 |
| Hutool | 5.8.32 |

## 本地运行

前置：MySQL 8（执行过 `deploy/sql/init.sql`）、Redis 7、JDK 21。

```powershell
# 仓库根目录执行：自动加载 .env、切换 JDK 21、启动 8080
pwsh -File scripts/dev/start-backend.ps1
```

或手动：

```bash
cd video-server
./mvnw spring-boot:run
```

必须提供的环境变量（见仓库根 `.env.example`）：`MYSQL_PASSWORD`、`JWT_SECRET`（≥32 字符）。

默认 profile 为 `dev`，也可用 `SPRING_PROFILES_ACTIVE=prod|worker` 指定。

## 构建与测试

```bash
./mvnw clean package     # 构建 + 单元测试
./mvnw test              # 只跑测试
```

## 接口验证（M0）

```bash
# 1. 登录（默认管理员 admin / admin123，见 deploy/sql/init.sql）
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}"

# 2. 当前用户（需替换 TOKEN）
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer TOKEN"

# 3. 未登录访问 → code 2001
curl -s http://localhost:8080/api/auth/me

# 4. 密码错误 → code 2002
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"wrong\"}"
```
