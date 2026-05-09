import { http, HttpResponse } from 'msw'

/**
 * MSW (Mock Service Worker) の共通リクエストハンドラ。
 *
 * 各テストで個別にレスポンスを上書きする想定なので、ここでは
 * 「全エンドポイントが 200 のダミーを返すデフォルト」だけを置く。
 * 個別シナリオ (401 / 500 等) は各テストで `server.use(...)` で上書きする。
 */
export const handlers = [
  // 認証
  http.post('/api/auth/login', () =>
    HttpResponse.json({ accessToken: 'mock-token', userId: 1, username: 'mock', displayName: 'モック' })
  ),
  http.post('/api/auth/register', () =>
    HttpResponse.json(
      { accessToken: 'mock-token', userId: 1, username: 'mock', displayName: 'モック' },
      { status: 201 }
    )
  ),
  http.post('/api/auth/refresh', () =>
    HttpResponse.json({ accessToken: 'mock-token', userId: 1, username: 'mock', displayName: 'モック' })
  ),
  http.post('/api/auth/logout', () => new HttpResponse(null, { status: 204 })),

  // タイムライン (空配列をデフォルト)
  http.get('/api/posts', () => HttpResponse.json([])),
]
