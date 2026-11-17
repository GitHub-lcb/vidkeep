import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { initTheme } from '@/utils/theme'
import '@/styles/index.scss'

// 主题由 index.html 内联脚本先行应用，这里同步一次（localStorage 与系统偏好兜底）
initTheme()

const app = createApp(App)

// Pinia 必须先于 router 安装：路由守卫里会用到 useUserStore
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
