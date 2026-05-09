import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

/**
 * Vitest の設定。
 *
 * - environment: 'jsdom' でブラウザ DOM を Node 上に再現する。React コンポーネントテストに必須。
 * - globals: true で `describe` / `it` / `expect` を import 不要で使えるようにする。
 * - setupFiles で jest-dom のカスタムマッチャー拡張と MSW の起動/停止フックを読み込む。
 * - css: false で CSS のインポートを無視 (ロード時間短縮 & jsdom が CSS を実際にレンダリングしないため不要)。
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // build/dist や node_modules を確実に除外する
    exclude: ['node_modules', 'dist', '.idea', '.git', '.cache'],
  },
})
