import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/**
 * Node 環境 (Vitest + jsdom) で動く MSW のリクエストインターセプター。
 *
 * setup.ts でテストライフサイクルにフックして listen / resetHandlers / close する。
 * 各テストで `server.use(...)` を呼ぶことで一時的にハンドラを上書きできる。
 */
export const server = setupServer(...handlers)
