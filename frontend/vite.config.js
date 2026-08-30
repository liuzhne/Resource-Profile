import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: false,
      // 生成 ESLint 可识别的全局符号清单，避免自动导入的 API（ref/computed/ElMessage…）被 no-undef 误报
      eslintrc: {
        enabled: true,
        filepath: './.eslintrc-auto-import.json',
        globalsPropValue: true
      }
    }),
    Components({
      resolvers: [ElementPlusResolver()]
    })
  ],
  resolve: {
    alias: {
      '@': resolve(import.meta.dirname, 'src')
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler'
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    // 业务入口保持在 500 KiB 内；ECharts/Element Plus 等重依赖独立缓存，
    // scripts/check-bundle-size.mjs 在本地和 CI 中执行同一套硬预算。
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks(id) {
          const moduleId = id.replaceAll('\\', '/')
          if (moduleId.includes('/node_modules/echarts/') || moduleId.includes('/node_modules/zrender/')) {
            return 'charts'
          }
          if (
            moduleId.includes('/node_modules/element-plus/') ||
            moduleId.includes('/node_modules/@element-plus/icons-vue/')
          ) {
            return 'element-plus'
          }
          if (
            moduleId.includes('/node_modules/vue/') ||
            moduleId.includes('/node_modules/@vue/') ||
            moduleId.includes('/node_modules/vue-router/') ||
            moduleId.includes('/node_modules/pinia/')
          ) {
            return 'vue-vendor'
          }
        }
      }
    }
  }
})
