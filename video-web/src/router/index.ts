import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

/** 管理后台登录页与首页地址（守卫、请求拦截器共用同一份常量） */
export const ADMIN_LOGIN_PATH = '/admin/login'
export const ADMIN_HOME_PATH = '/admin/dashboard'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/PortalLayout.vue'),
    children: [
      {
        path: '',
        name: 'portal-home',
        component: () => import('@/views/portal/Home.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'videos/:id',
        name: 'portal-video-detail',
        component: () => import('@/views/portal/VideoDetail.vue'),
        meta: { title: '视频详情' },
      },
      {
        path: 'play/:videoId/:episodeId',
        name: 'portal-player',
        component: () => import('@/views/portal/Player.vue'),
        meta: { title: '播放' },
      },
    ],
  },
  {
    path: ADMIN_LOGIN_PATH,
    name: 'admin-login',
    component: () => import('@/views/admin/Login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: ADMIN_HOME_PATH,
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '仪表盘', requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'videos',
        name: 'admin-videos',
        component: () => import('@/views/admin/VideoList.vue'),
        meta: { title: '作品管理', requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'videos/:id',
        name: 'admin-video-edit',
        component: () => import('@/views/admin/VideoEdit.vue'),
        meta: { title: '视频编辑', requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'upload',
        name: 'admin-upload',
        component: () => import('@/views/admin/VideoUpload.vue'),
        meta: { title: '视频上传', requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'categories',
        name: 'admin-categories',
        component: () => import('@/views/admin/CategoryList.vue'),
        meta: { title: '分类管理', requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'logs',
        name: 'admin-logs',
        component: () => import('@/views/admin/OperationLogs.vue'),
        meta: { title: '操作日志', requiresAuth: true, requiresAdmin: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

/**
 * 全局前置守卫（文档 7.4.3 权限模型）：
 * 1. /admin/** 需要登录，未登录带 redirect 回登录页
 * 2. 需要管理员的页面，角色不符则提示无权限并回登录页
 * 3. 已登录访问登录页，直接进仪表盘
 */
router.beforeEach((to) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth === true) {
    if (!userStore.isLoggedIn) {
      return { path: ADMIN_LOGIN_PATH, query: { redirect: to.fullPath } }
    }
    if (to.meta.requiresAdmin === true && !userStore.isAdmin) {
      ElMessage.error('无权限访问管理后台')
      userStore.clearAuth()
      return { path: ADMIN_LOGIN_PATH, query: { redirect: to.fullPath } }
    }
    return true
  }

  if (to.path === ADMIN_LOGIN_PATH && userStore.isLoggedIn) {
    return { path: ADMIN_HOME_PATH }
  }

  return true
})

// 标题统一带站点名，方便多标签页区分
router.afterEach((to) => {
  document.title = typeof to.meta.title === 'string' && to.meta.title.length > 0
    ? `${to.meta.title} · vidkeep`
    : 'vidkeep'
})

export default router
