import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// Vite 配置：开发端口 5173；/api 反向代理到本地 Spring Boot（8080），避免开发期跨域
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 使用 Sass 现代编译器 API，规避 legacy JS API 弃用告警
        api: 'modern-compiler',
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 本地开发由后端直接分发 HLS 切片与封面（生产环境走 Nginx）
      '/hls': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/covers': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // Element Plus 全量引入后单 chunk 偏大，M0 阶段先放宽告警阈值，M3 再按需拆包
    chunkSizeWarningLimit: 1500,
  },
})
