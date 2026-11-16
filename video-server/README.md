# video-server — 后端服务

Spring Boot 3.3 + JDK 21 + MyBatis-Plus。

## 状态

⬜ **待初始化**（M0 任务）

## 初始化要求

按 [技术文档第 4.3 节](../docs/01-技术选型与开发文档.md#43-后端目录结构) 建立包结构：

```
src/main/java/com/vidkeep/
├── VideoServerApplication.java
├── common/        Result / PageResult / ErrorCode / BizException / GlobalExceptionHandler
├── config/        MybatisPlus / Redis / WebMvc / Security / Async
├── security/      JwtUtil / JwtAuthFilter / LoginUser
├── infra/
│   ├── storage/   StorageService / LocalStorageService
│   ├── ffmpeg/    FfprobeService / FfmpegService / TranscodeProfile
│   └── queue/     RedisStreamQueue
└── module/
    ├── auth/
    ├── user/
    ├── video/
    ├── upload/
    ├── transcode/
    ├── play/
    └── category/
```

## 关键依赖版本

| 依赖 | 版本 |
|---|---|
| JDK | 21 |
| Spring Boot | 3.3.5 |
| MyBatis-Plus | 3.5.7 |
| jjwt | 0.12.6 |
| Hutool | 5.8.32 |

## 本地运行

```bash
./mvnw spring-boot:run
```

默认端口 8080，profile 由 `SPRING_PROFILES_ACTIVE` 指定（`dev` / `prod` / `worker`）。
