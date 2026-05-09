import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { cleanup } from '@testing-library/react'
import { server } from './server'

/**
 * Vitest の setupFiles で読み込まれるテスト共通フック。
 *
 * - `@testing-library/jest-dom/vitest` を import することで toBeInTheDocument 等のカスタム
 *   マッチャーが Vitest の expect に追加される。
 * - MSW サーバーを全テスト前に起動し、各テスト後にハンドラをリセット、全テスト後に close する。
 *   これにより API モックの汚染が次のテストに漏れない。
 * - Testing Library の cleanup を各テスト後に呼んで DOM をリセットする
 *   (デフォルトで自動だが、明示しておく)。
 */
beforeAll(() => {
  // 未登録のリクエストはエラーにする (テストが知らない API を叩いたら気づけるように)。
  server.listen({ onUnhandledRequest: 'error' })
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
})

afterAll(() => {
  server.close()
})
