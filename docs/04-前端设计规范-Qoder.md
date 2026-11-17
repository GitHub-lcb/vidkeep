# 前端设计规范 v2 —— Qoder CN 体系

| 项 | 内容 |
|---|---|
| 文档版本 | v2.0 |
| 编写日期 | 2026-11-17 |
| 状态 | 生效中（完全重构前台与后台） |
| 适用 | `video-web/` 全部页面 |
| 设计来源 | `my-agents/docs/qoder-cn-design-spec`（Qoder CN 0.5.7 设计系统逆向分析：`tokens/design-tokens.json`、`appendix/components.md`、`appendix/surfaces.md`、`appendix/brand.md`） |
| 取代 | [03-前端设计规范（v1 深色方案）](03-前端设计规范.md)（保留为历史版本，不再更新） |

> 本文描述 `video-web` 当前**实际生效**的 Qoder 风格设计系统。设计令牌唯一真源：
> `src/styles/_tokens.scss`（CSS 变量，含深浅主题）；本文件是它的使用说明与验收依据。

---

## 1. 设计原则（来自 Qoder 规范）

1. **系统字体 + 紧凑基准**：UI 以 12/13px 为主档，信息密度高但不拥挤；正文 13/20。
2. **语义 Token 分层**：raw → semantic → 工具类；业务只消费语义变量（`--q-*`），禁止裸色值。
3. **表面分层用发丝描边**：`bg-base / bg-container / bg-elevated / bg-layout` 四级表面；
   卡片与控件统一 `box-shadow: var(--q-shadow-border)`（0 0 0 1px + 微投影），不堆重阴影。
4. **单一主色**：forest 灰绿（浅色 `#4b6f5a` / 深色 `#4d9c61`）；成功/警告/危险仅用于语义反馈。
5. **单一缓动**：`cubic-bezier(.2,.8,.2,1)`；UI 过渡 150–240ms；`prefers-reduced-motion` 全局降级。
6. **克制**：无大渐变、无霓虹、无多余装饰；视频封面是页面主角。

---

## 2. 主题机制

| 项 | 实现 |
|---|---|
| 主题 | `forest-light`（默认）/ `forest-dark` |
| 切换 | `html[data-theme='light'\|'dark']` + CSS 变量覆盖（`_tokens.scss`） |
| 首屏防闪烁 | `index.html` 内联脚本在 CSS 前写入 `data-theme`（localStorage → 系统偏好） |
| 运行期 | `src/utils/theme.ts`：`initTheme / toggleTheme / getTheme / onThemeChange`，支持 View Transition 160ms |
| 入口 | 顶栏 `ThemeToggle` 组件（太阳/月亮图标） |
| 持久化 | `localStorage.vidkeep_theme` |

---

## 3. 设计令牌（`src/styles/_tokens.scss`）

### 3.1 颜色（浅色 → 深色）

| 语义 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `--q-bg-base` | `#ffffff` | `#222523` | 基础表面（输入、弹窗） |
| `--q-bg-container` | `#ffffff` | `#191b1a` | 卡片、侧栏、右栏 |
| `--q-bg-elevated` | `#f9f9f9` | `#252825` | 后台内容区、抬升面 |
| `--q-bg-layout` | `#ffffff` | `#191b1a` | 页面底色 |
| `--q-bg-highlight` | `#080807` | `#eeeeeb` | 反白按钮（Secondary） |
| `--q-text` | `#141414` | `#d9dcd9` | 主文字 |
| `--q-text-secondary` | `#636261` | `#b3bab4` | 次级文字/图标 |
| `--q-text-tertiary` | `#838280` | `#949d96` | 辅助说明 |
| `--q-text-quaternary` | `#aaa9a8` | `#8a948c` | 占位/极弱信息 |
| `--q-border-tertiary` | `#e6e6e6` | `#363c37` | 标准描边（发丝） |
| `--q-border-secondary` | `#dddddd` | `#404842` | hover 描边 |
| `--q-border` | `#bcbbba` | `#404842` | focus 描边 |
| `--q-primary` | `#4b6f5a` | `#4d9c61` | 主色（按钮/强调） |
| `--q-primary-hover / active` | `#436651 / #3a5747` | `#62ad78 / #428955` | 悬停/按下 |
| `--q-primary-text / bg / border` | `#4b6f5a / #f2f4f2 / #d5dbd8` | `#8ee5a1 / #14261c / #335942` | 主色文字/浅底/浅边 |
| `--q-success / bg / border` | `#579b6e / #ebf7f0 / #b3ddc2` | `#73cd94 / #14261c / #335942` | 成功 |
| `--q-warning / bg / border` | `#efb041 / #fefbe8 / #fbe6c3` | `#faad14 / #4a4019 / #664d19` | 警告 |
| `--q-error / bg / border` | `#ec5b56 / #fdf2f0 / #f7cec9` | `#ff4d4f / #4a1a1b / #663338` | 危险/错误 |
| `--q-fill / secondary / tertiary / quaternary` | `#dfdfdf` / `rgba(0,0,0,.07)` / `#f6f6f6` / `#fcfcfc` | `#363d37` / `#2d332e` / `#252b26` / `#212622` | 填充层级 |

阴影：`--q-shadow-border`（发丝 + 微投影）、`--q-shadow-card`、`--q-shadow-pop`（浮层）。
视频播放舞台：`--q-player-bg: #101211`（与主题无关）。

### 3.2 字号 / 行高 / 字重

| Token | 值 | 用途 |
|---|---|---|
| `--q-fs-10 / 11` | 10 / 11px | eyebrow、标签、元信息 |
| `--q-fs-12` | 12px | 辅助说明、表头、标签、表单 label |
| `--q-fs-13` | **13px（UI 主档）** | 正文、按钮、输入、菜单、表格 |
| `--q-fs-14` | 14px | 区块标题 |
| `--q-fs-16` | 16px | 弹窗标题、空态标题 |
| `--q-fs-18` | 18px | 后台页面标题 |
| `--q-fs-24` | 24px | 统计数字、前台大标题 |
| 行高 | 16 / 18 / 20 / 22 / 24 | 与字号配对 |

字体：`--q-font-ui`（Inter 400/500/600 + 系统中文字体回退）、`--q-font-brand`（Instrument Sans）、
`--q-font-mono`（等宽）。字体文件在 `src/assets/fonts`（latin 子集，中文走系统字体）。

### 3.3 间距 / 圆角 / 控件 / 动效 / 布局

| 类别 | 值 |
|---|---|
| 间距 | 4 基准：4/8/12/16/20/24/32/40/48/64 |
| 圆角 | 标签 4 / 菜单 8 / 容器 12 / 控件 16 / 胶囊 999（`--q-r-control` 16、`--q-r-surface` 12） |
| 控件高度 | sm 20 / md 28 / lg 36（`--q-ctl-sm/md/lg`） |
| 动效 | `--q-dur-fast 150ms` / `base 200ms` / `slow 240ms`；`--q-ease: cubic-bezier(.2,.8,.2,1)` |
| 布局 | 顶栏 40px、侧栏 248px、右栏 280px、内容限宽 924px（`--q-titlebar-h/--q-sidebar-w/--q-rail-w/--q-content-max`） |
| 滚动条 | 6px 宽、3px 圆角、hover 显色 |

---

## 4. 布局骨架

### 4.1 前台（`layouts/PortalLayout.vue`）

```
┌─────────────────────────────────────────────────────────────┐
│ 40px 顶栏：brand · 中部搜索(28px) · 主题切换 · 管理后台       │
├────────────┬────────────────────────────────┬───────────────┤
│ 248 侧栏   │ 主内容（Home/详情/播放）        │ 280 右栏      │
│ 首页/搜索  │ 页面标题 + 筛选 + 视频网格      │ 最近添加/站点 │
│ 分类列表   │                                │ 说明          │
└────────────┴────────────────────────────────┴───────────────┘
```
- ≥1280 显示右栏；≤1100 隐藏侧栏；≤720 顶栏紧凑、单列网格。

### 4.2 后台（`layouts/AdminLayout.vue`）

```
┌─────────────────────────────────────────────────────────────┐
│ 40px 顶栏：brand · 页面标题 · 查看前台/主题/菜单             │
├────────────┬───────────────────────────────────────────────┤
│ 248 侧栏   │ 内容区 bg-elevated：q-page（max 1240，居中）  │
│ 内容分组   │ 页头 + 卡片（q-card）                         │
│ 系统分组   │                                               │
│ 账号/退出  │                                               │
└────────────┴───────────────────────────────────────────────┘
```
- ≤1100 侧栏变为抽屉（顶栏菜单按钮 + 遮罩）。

---

## 5. 页面模式

| 页面 | 结构 |
|---|---|
| 前台首页 | 页头（eyebrow/标题/说明/数量）→ 分类 chips + 清除筛选 → 最近更新（大卡 16:9 + 紧凑列表）→ 全部视频 3 列网格 → 分页/空态/骨架 |
| 前台详情 | 返回 → 封面 + 信息（标题 20/600、分类·集数 q-tag、简介、开始播放）→ 剧集列表（状态 tag） |
| 播放页 | 页头 → 深色播放舞台（16:9，圆角 12）+ 右侧剧集侧栏（当前集高亮）→ 标题/元信息 → 过期重试状态块 |
| 后台概览 | 页头 + 5 张统计卡（24px 数字）+ 处理流程卡 + 已交付能力卡 |
| 后台列表 | 页头（标题/说明/新建）→ 筛选卡 → 表格卡（封面 16:9 缩略图、q-tag/switch、文字操作）→ 分页 |
| 后台编辑 | 页头 → 基础信息卡（表单）+ 剧集表格卡 → 保存/上传/返回 |
| 上传页 | 页头 → 作品选择卡 → UploadPanel 卡（拖拽区/文件卡/进度/状态） |
| 登录/口令 | 全屏浅色背景 + 居中卡片（登录 860px 双栏、口令 360px 单卡），36px 输入、16px 圆角、primary 按钮 |
| 空态 | `.q-empty` 堆叠卡片（48px 三层错位）+ 标题 16/500 + 描述 12 + 操作组 |

---

## 6. 组件规范（对应实现）

| 组件 | 规范 |
|---|---|
| 按钮 `.q-btn` | 高度 28（sm 20 / lg 36）；圆角 16；主色填充 / secondary 反白（`--q-bg-highlight`）/ outline 发丝边 / ghost 透明 / danger；focus 用 2px 主色浅边替代 ring；禁用 opacity .5 |
| 图标按钮 `.q-icon-btn` | 28×28（sm 20 / lg 36），ghost，hover 填充 |
| 输入 `.q-input` / `el-input` | 28（md）/36（lg），圆角 10–16，发丝内描边，hover 加深、focus 用 `--q-border` |
| 表单 `.q-field` / `el-form` | label 12/500 次要色；错误 11px 红；间距 14–16 |
| 标签 `.q-tag` / `el-tag` | 高 20、圆角 4、11/500；语义色用浅底 + 同色文字（`--dot` 可带圆点） |
| 表格 `el-table` | 表头 12/500 次要色 + `--q-fill-tertiary` 底；行高紧凑、横线 `--q-border-quaternary`；无外框（卡片包裹） |
| 开关 `el-switch` | 32×20，主色开启、`--q-fill` 关闭 |
| 弹窗 `el-dialog`/`el-message-box` | 圆角 16、发丝边、`--q-shadow-pop`、标题 16/600、内边距 20 |
| 消息 `el-message` | 圆角 8、发丝边、13px |
| 分页 | 26px 高、12px 字号、当前页主色浅底 |
| 空态/骨架/进度 | `.q-empty` / `.q-skeleton`（pulse，reduced-motion 关闭）/ `.q-progress` 4px |
| 图标 | 统一 `QIcon`（24 viewBox、stroke 1.5、16px 默认），不引入图标库 |
| 品牌 | `AppLogo`：forest 主色圆角方块 + 白色播放三角 + Instrument Sans 字标 |

Element Plus 主题映射见 `_base.scss` 的 `:root`（主色/语义色/边框/填充/圆角/字号）与组件级覆盖
（按钮/输入/表格/分页/标签/开关/弹窗/消息/下拉/进度/加载遮罩）。组件内只做局部 `:deep()` 修正。

---

## 7. 代码约定

1. **颜色/尺寸只用 CSS 变量**：`var(--q-*)`；页面 SCSS 用 `@use '../../styles/variables' as *;` 取别名与 mixin。
2. **禁止硬编码 hex**（唯一例外：视频画面上的半透明黑角标 `rgba(8,8,7,...)` 与播放舞台）。
3. 优先复用全局类：`.q-page / .q-page-head / .q-page-title / .q-page-desc / .q-page-actions / .q-card / .q-btn / .q-icon-btn / .q-tag / .q-empty / .q-field / .q-skeleton / .q-section-title`。
4. 组件私有样式写 `<style scoped lang="scss">`；类名 BEM 风格（`video-card__title`）。
5. 交互必须主题自适配，禁止在组件里判断深浅色（只用 token）。
6. 图标用 `QIcon`，品牌用 `AppLogo`，主题切换用 `ThemeToggle`。
7. 响应式：`@media (max-width: 1280px/1100px/720px)`；前台网格 3→2→1。
8. 接口走 `src/api/`、类型放 `src/types/`、禁止 `any`。

---

## 8. 与 Qoder 原始规范的差异（有意取舍）

| 项 | Qoder | 本项目 | 原因 |
|---|---|---|---|
| UI 基准字号 | 12px | 13px（辅助 11/12） | 中文小字号可读性 |
| 控件圆角 | squircle 16（`corner-shape`） | border-radius 12/16 兜底 | `corner-shape` 兼容性不足，Chromium 外需降级 |
| 焦点 | 边框/底色变化，无全局 ring | 边框变化 + 键盘 2px 浅色描边 | WCAG 可访问性兜底 |
| 字体 | Inter + Instrument Sans + 衬线阅读模式 | Inter + Instrument Sans + 系统中文 | 中文正文无衬线更稳；未引入 Crimson Pro |
| 品牌视觉 | Qoder Logo / Qoduck 吉祥物 | vidkeep 自定义 mark（绿底播放三角） | 品牌独立，不照搬 |
| 主题 | 10 套 | 2 套（forest light/dark） | 当前只需要深浅两态 |
| 内容密度 | 桌面工作台 1320×800 三栏 | Web 自适应三栏（≥1280 右栏） | 视频站需要移动端 |

---

## 9. 验收

```bash
cd video-web
npm run typecheck
npm run build
```

视觉验收：无头浏览器截图（1440×900）覆盖：
登录页（浅/深）、前台首页（浅/深）、后台概览（浅/深）、视频列表、上传页、分类管理、操作日志、
前台详情、播放页。截图脚本为本地开发辅助（`data/screenshots/`，已 gitignore）。
