/**
 * auth.ts の単体テスト。
 *
 * 対象: login / register / refreshAccessToken / logout / getAccessToken / clearAuthData
 * 技法: 状態遷移 (未ログイン → ログイン → ログアウト)
 */
import { http, HttpResponse } from 'msw'
import { server } from '../test/server'
import {
  apiClient,
  clearAuthData,
  getAccessToken,
  getUserInfo,
  login,
  logout,
  refreshAccessToken,
  register,
} from './auth'

describe('auth API', () => {
  beforeEach(() => {
    // 前のテストでメモリに残ったトークンをクリア
    clearAuthData()
  })

  describe('login', () => {
    it('成功時はメモリにトークン / ユーザー情報を保存する', async () => {
      const res = await login({ email: 'a@example.com', password: 'Pass1234' })

      expect(res.accessToken).toBe('mock-token')
      expect(getAccessToken()).toBe('mock-token')
      expect(getUserInfo()).toEqual({ userId: 1, username: 'mock', displayName: 'モック' })
    })

    it('401 のときはリフレッシュせず例外をそのまま伝播する（ログイン画面でエラー表示するため）', async () => {
      // ログイン失敗の 401 は「資格情報が誤り」を意味するので、トークンリフレッシュの対象外。
      // refresh が呼ばれていないこと（=リダイレクトでエラーが消えないこと）を確認する。
      let refreshCalled = false
      server.use(
        http.post('/api/auth/login', () =>
          HttpResponse.json(
            { message: 'メールアドレスまたはパスワードが正しくありません' },
            { status: 401 }
          )
        ),
        http.post('/api/auth/refresh', () => {
          refreshCalled = true
          return HttpResponse.json({ message: 'should not be called' }, { status: 401 })
        })
      )

      // 401 がそのまま reject され、呼び出し元（LoginPage）がメッセージを参照できる
      await expect(login({ email: 'a@example.com', password: 'wrong' })).rejects.toMatchObject({
        response: {
          status: 401,
          data: { message: 'メールアドレスまたはパスワードが正しくありません' },
        },
      })
      // 認証エンドポイントの 401 はリフレッシュ対象外なので refresh は呼ばれない
      expect(refreshCalled).toBe(false)
    })
  })

  describe('register', () => {
    it('成功時はトークン / ユーザー情報を保存する', async () => {
      const res = await register({
        displayName: 'アリス',
        username: 'alice',
        email: 'a@example.com',
        password: 'Pass1234',
        passwordConfirm: 'Pass1234',
      })

      expect(res.userId).toBe(1)
      expect(getAccessToken()).toBe('mock-token')
    })
  })

  describe('refreshAccessToken', () => {
    it('成功時は新しいトークンをメモリに保存する', async () => {
      const res = await refreshAccessToken()
      expect(res.accessToken).toBe('mock-token')
      expect(getAccessToken()).toBe('mock-token')
    })
  })

  describe('logout', () => {
    it('logout 後にメモリがクリアされる', async () => {
      await login({ email: 'a@example.com', password: 'Pass1234' })
      expect(getAccessToken()).toBe('mock-token')

      await logout()

      expect(getAccessToken()).toBeNull()
      expect(getUserInfo()).toBeNull()
    })
  })

  describe('clearAuthData', () => {
    it('メモリ上のトークン / ユーザー情報を消す', async () => {
      await login({ email: 'a@example.com', password: 'Pass1234' })

      clearAuthData()

      expect(getAccessToken()).toBeNull()
      expect(getUserInfo()).toBeNull()
    })
  })

  // 認証エンドポイント以外（通常の保護 API）の 401 自動リフレッシュが壊れていないことを確認する。
  describe('レスポンスインターセプター（401 自動リフレッシュ）', () => {
    it('認証エンドポイント以外の 401 はリフレッシュして元のリクエストを再試行する', async () => {
      let postsCalls = 0
      server.use(
        // 1回目の GET /api/posts は 401（トークン失効）、refresh 後の再試行は 200 を返す
        http.get('/api/posts', () => {
          postsCalls += 1
          if (postsCalls === 1) {
            return HttpResponse.json({ message: 'expired' }, { status: 401 })
          }
          return HttpResponse.json([{ id: 1 }])
        }),
        http.post('/api/auth/refresh', () =>
          HttpResponse.json({
            accessToken: 'new-token',
            userId: 1,
            username: 'mock',
            displayName: 'モック',
          })
        )
      )

      const res = await apiClient.get('/posts')
      expect(res.status).toBe(200)
      // 401 → refresh → リトライ の流れで 2 回呼ばれる
      expect(postsCalls).toBe(2)
    })
  })
})
