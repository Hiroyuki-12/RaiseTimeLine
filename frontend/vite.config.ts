import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // ポートルール: フロントエンドは必ず 5173 で起動する（変更禁止）
    port: 5173,
    proxy: {
      // /api へのリクエストをバックエンド（:8080）に転送する
      // これにより開発時の CORS 問題を回避できる
      // （フロントエンドとバックエンドが同一オリジンに見える）
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
