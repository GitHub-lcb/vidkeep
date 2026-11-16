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

> `artplayer` / `hls.js` M0 阶段只安装、不使用，M2 接入 HLS 播放时启用。
> 不引入 Tailwind、其他 UI 库或请求库。

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
  },
}
```

启动前端前请确认后端（`video-server/`）已在 `http://localhost:8080` 运行，否则登录接口会报网络错误。

## 目录结构

```
video-web/
├── index.html                 入口 HTML（zh-CN、移动端 viewport）
├── vite.config.ts             alias @ → src、5173 端口、/api 代理
├── tsconfig.json              strict + 路径别名，含 vite.config.ts
└── src/
    ├── main.ts                创建应用：Pinia → Router → Element Plus（zh-cn）
    ├── App.vue                仅保留 <router-view />，布局由路由决定
    ├── api/
    │   ├── request.ts         axios 实例：Bearer 注入、Result 解包、BizError、2001 跳登录
    │   └── auth.ts            login / me / logout
    ├── types/                 api.ts（Result/PageResult/错误码）、auth.ts、router.d.ts
    ├── stores/
    │   └── user.ts            登录态（token/user 持久化到 localStorage）
    ├── router/
    │   └── index.ts           路由表 + 登录/角色守卫 + 404 兜底
    ├── layouts/               AdminLayout（侧栏 + 顶栏）、PortalLayout（前台顶栏）
    ├── views/
    │   ├── admin/             Login.vue、Dashboard.vue
    │   ├── portal/            Home.vue
    │   └── NotFound.vue       404
    ├── utils/                 storage.ts（登录态存储）、format.ts（时长/大小）、throttle.ts
    └── styles/                index.scss 入口 + reset / variables / base
```

## 路由与访问控制（M0）

| 路由 | 页面 | 权限 |
|---|---|---|
| `/` | 前台首页占位 | 公开 |
| `/admin/login` | 管理后台登录 | 公开；已登录会跳仪表盘 |
| `/admin/dashboard` | 仪表盘占位（M1/M2/M3 模块卡片） | 需登录 + ADMIN |
| 其他 | 404 兜底 | — |

守卫规则（`src/router/index.ts`）：未登录访问 `/admin/**` → `/admin/login?redirect=...`；
已登录但 `role !== 'ADMIN'` → 提示无权限并回登录页；后端返回 `code = 2001` 时清本地登录态并跳登录页。

## 接口约定（M0）

统一响应体 `{ code, message, data }`，业务错误也是 HTTP 200：

- `code === 0`：`request.ts` 直接返回 `data`，调用方拿到的是业务数据而非 AxiosResponse
- `code !== 0`：抛出 `BizError`（携带 `code` 与后端 `message`），用 `isBizError(error)` 收窄后展示
- `code === 2001`：清除 local，`window.location` 跳 `/admin/login`（用 location 而非 router，避免循环依赖）

已封装接口：`login(username, password)`、`me()`、`logout()`。

## M0 完成范围

- [x] Vite + Vue3 + TS 脚手架、路径别名、代理
- [x] Axios 封装（Result 解包 / Bearer / BizError / 2001 处理）
- [x] Pinia 登录态持久化（`vidkeep_token` / `vidkeep_user`）
- [x] 路由守卫与 404
- [x] 登录页（Element Plus 表单校验、loading、回车提交、失败提示后端 message）
- [x] 后台布局 + 仪表盘占位、前台布局 + 首页占位
- [x] 类型全量、无 any、无组件内裸 axios

后续：M1 分片上传（`upload.ts`、`UploadPanel.vue`、`utils/hash.ts`）、M2 HLS 播放（`play.ts`、`HlsPlayer.vue`）、M3 视频与分类管理页。
