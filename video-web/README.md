# video-web — vidkeep 前端

vidkeep 是自建视频站：管理员后台上传自有视频 → 后端 FFmpeg 转码为 HLS → 前台访客播放。

本目录是**唯一的** Vue 前端工程，同时承载两端（文档 3.4「双端结构」）：

| 端 | 路由前缀 | UI 方案 | 用户 |
|---|---|---|---|
| 用户前台 | `/` | 自研轻量样式 + Element Plus 局部组件 | 访客 |
| 管理后台 | `/admin/*` | Element Plus | 管理员 |

## 技术栈 / 版本

严格按 `docs/01-技术选型与开发文档.md` 3.5「版本锁定清单」：

| 依赖 | 版本 | 依赖 | 版本 |
|---|---|---|---|
| vue | ^3.4.38 | vite | ^5.4.2 |
| vue-router | ^4.4.3 | typescript | ^5.5.4 |
| pinia | ^2.2.2 | vue-tsc | ^2.1.4 |
| element-plus | ^2.8.1 | sass | ^1.77.8 |
| axios | ^1.7.4 | @vitejs/plugin-vue | ^5.1.2 |
| artplayer | ^5.2.3 | @types/node | ^22.5.0 |
| hls.js | ^1.5.15 | dayjs | ^1.11.13 |

> `artplayer` / `hls.js` 用于 M2 播放器（`components/HlsPlayer.vue`），M1/M3 页面不直接引用。
> 不引入 Tailwind、其他 UI 库或请求库。

> 设计规范（Qoder CN 体系：forest 深浅主题、令牌、控件阶梯、布局与组件约定）见
> [docs/04-前端设计规范-Qoder.md](../docs/04-前端设计规范-Qoder.md)；新增页面前先读该文档。

## 本地运行

```bash
cd video-web
npm install          # 依赖安装（registry 使用项目配置的镜像源）
npm run dev          # 开发服务器 http://localhost:5173
npm run typecheck    # 类型检查（vue-tsc --noEmit）
npm run build        # 类型检查 + 生产构建，产物在 dist/
npm run preview      # 预览 dist/
```

Node 要求：>= 18.18（当前验证环境 Node v24 / npm 11）。

## 开发代理

`vite.config.ts` 已把 `/api` 代理到本地后端，开发期无需处理跨域：

```ts
server: {
  port: 5173,
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true },
    // 本地开发由后端直接分发 HLS 与封面（生产走 Nginx）
    '/hls': { target: 'http://localhost:8080', changeOrigin: true },
    '/covers': { target: 'http://localhost:8080', changeOrigin: true },
  },
}
```

启动前端前请确认后端（`video-server/`）已在 `http://localhost:8080` 运行，否则登录接口会报网络错误。

## 目录结构

```
video-web/
├── index.html                 入口 HTML（zh-CN、移动端 viewport）
├── vite.config.ts             alias @ → src、5173 端口、/api + /hls + /covers 代理
├── tsconfig.json              strict + 路径别名，含 vite.config.ts
└── src/
    ├── main.ts                创建应用：Pinia → Router → Element Plus（zh-cn）
    ├── App.vue                仅保留 <router-view />，布局由路由决定
    ├── api/
    │   ├── request.ts         axios 实例：Bearer 注入、Result 解包、BizError、2001 跳登录
    │   ├── auth.ts            login / me / logout
    │   ├── video.ts           后台视频 CRUD / 上下架 / 删单集
    │   ├── category.ts        分类列表 + 管理端 CRUD
    │   ├── upload.ts          check / init / 分片 PUT / status / complete
    │   ├── transcode.ts       转码进度 / 重试 / 取消
    │   └── play.ts            前台列表/详情、播放地址、播放进度
    ├── types/                 api / auth / video / category / upload / transcode / play / router
    ├── stores/                user.ts（登录态）、player.ts（设备 ID + 本地进度）
    ├── router/
    │   └── index.ts           路由表 + 登录/角色守卫 + 404 兜底
    ├── layouts/               AdminLayout（侧栏 + 顶栏）、PortalLayout（前台顶栏）
    ├── components/            UploadPanel（分片上传）、HlsPlayer（播放器）、VideoCard
    ├── views/
    │   ├── admin/             Login / Dashboard / VideoList / VideoEdit / VideoUpload / CategoryList
    │   ├── portal/            Home / VideoDetail / Player
    │   └── NotFound.vue       404
    ├── utils/                 storage / device / format / throttle / hash（抽样指纹）
    └── styles/                index.scss 入口 + reset / variables / base
```

## 路由与访问控制

| 路由 | 页面 | 权限 |
|---|---|---|
| `/` | 前台首页（分类筛选 + 搜索 + 视频网格 + 分页） | 公开 |
| `/videos/:id` | 前台详情（简介 + 剧集列表，转码中/失败有状态提示） | 公开 |
| `/play/:videoId/:episodeId` | 播放页（ArtPlayer + hls.js、剧集切换、进度记忆） | 公开 |
| `/admin/login` | 管理后台登录 | 公开；已登录会跳仪表盘 |
| `/admin/dashboard` | 仪表盘（视频总数/分类数等真实统计） | 需登录 + ADMIN |
| `/admin/videos` | 视频列表（筛选/分页/上下架/删除/新建） | 需登录 + ADMIN |
| `/admin/videos/:id` | 视频编辑（信息 + 剧集表格 + 预览/删单集） | 需登录 + ADMIN |
| `/admin/upload` | 视频上传（选作品 + 分片上传 + 转码进度） | 需登录 + ADMIN |
| `/admin/categories` | 分类管理（CRUD） | 需登录 + ADMIN |
| `/admin/logs` | 操作日志（P1 审计，按用户名/模块筛选） | 需登录 + ADMIN |
| 其他 | 404 兜底 | — |

守卫规则（`src/router/index.ts`）：未登录访问 `/admin/**` → `/admin/login?redirect=...`；
已登录但 `role !== 'ADMIN'` → 提示无权限并回登录页；后端返回 `code = 2001` 时清本地登录态并跳登录页。

## 接口约定

统一响应体 `{ code, message, data }`，业务错误也是 HTTP 200：

- `code === 0`：`request.ts` 直接返回 `data`，调用方拿到的是业务数据而非 AxiosResponse
- `code !== 0`：抛出 `BizError`（携带 `code` 与后端 `message`），用 `isBizError(error)` 收窄后展示
- `code === 2001`：清除本地登录态，`window.location` 跳 `/admin/login`（用 location 而非 router，避免循环依赖）

已封装模块：

| 文件 | 接口 |
|---|---|
| `api/auth.ts` | 登录 / 当前用户 / 登出 |
| `api/video.ts` | 后台视频列表、详情、新建、编辑、删除、上下架、删单集 |
| `api/category.ts` | 分类列表 + 管理端 CRUD |
| `api/upload.ts` | 秒传检查、init、分片 PUT、status、complete |
| `api/transcode.ts` | 转码进度查询、重试、取消 |
| `api/play.ts` | 前台列表/详情、播放地址、播放进度上报/查询 |
| `api/site.ts` | P1 站点口令状态/校验 |
| `api/admin.ts` | P1 后台统计、操作日志列表 |

## M0–M3 完成范围

- [x] Vite + Vue3 + TS 脚手架、路径别名、代理（`/api`、`/hls`、`/covers`）
- [x] Axios 封装（Result 解包 / Bearer / BizError / 2001 处理）
- [x] Pinia 登录态持久化；登录页与路由守卫
- [x] M1 上传：抽样指纹（`utils/hash.ts`）、3 并发分片、断点续传、秒传、转码进度轮询/重试/取消
- [x] M2 播放：ArtPlayer + hls.js 封装（Safari 原生回退）、进度记忆（服务端 + localStorage，≥95% 归零、15s 上报）
- [x] M3 管理页：视频列表/编辑、上传页、分类管理、仪表盘真实统计
- [x] M3 前台：首页列表/搜索/分类筛选、详情/剧集、播放页
- [x] P1：站点访问口令门禁（`SiteGate`）、操作日志页、仪表盘真实统计、播放地址过期重试
- [x] 类型全量、无 any、无组件内裸 axios

后续（M4）：HTTPS/CDN、播放器多码率手动切换、Element Plus 按需引入减包。
