import 'vue-router'

// 扩展路由 meta 的类型，供守卫与布局安全读取（避免把 meta 当 any 用）
declare module 'vue-router' {
  interface RouteMeta {
    /** 浏览器标题 */
    title?: string
    /** 是否需要登录 */
    requiresAuth?: boolean
    /** 是否需要管理员角色 */
    requiresAdmin?: boolean
  }
}

export {}
