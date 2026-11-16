# video-web — 前端

Vue 3 + TypeScript + Vite 5 + Element Plus + ArtPlayer。

## 状态

⬜ **待初始化**（M0 任务）

## 初始化要求

按 [技术文档第 4.4 节](../docs/01-技术选型与开发文档.md#44-前端目录结构) 建立结构：

```
src/
├── api/          axios 封装 + 各模块接口
├── router/       路由表 + 守卫
├── stores/       Pinia
├── layouts/      AdminLayout / PortalLayout
├── views/
│   ├── admin/    登录、仪表盘、视频管理、上传、分类
│   └── portal/   首页、详情、播放
├── components/   VideoCard / HlsPlayer / UploadPanel
├── utils/        hash.ts / format.ts
└── types/
```

## 关键依赖版本

| 依赖 | 版本 |
|---|---|
| Vue | 3.4+ |
| TypeScript | 5.5+ |
| Vite | 5.4+ |
| Element Plus | 2.8+ |
| ArtPlayer | 5.2+ |
| hls.js | 1.5+ |

## 本地运行

```bash
npm install
npm run dev        # http://localhost:5173
npm run build
npm run typecheck
```

## 开发代理

`vite.config.ts` 中配置代理：

```ts
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true }
  }
}
```
