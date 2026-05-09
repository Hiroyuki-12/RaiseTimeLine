/**
 * auth.ts の単体テスト。
 *
 * 対象: login / register / refreshAccessToken / logout / getAccessToken / clearAuthData
 * 技法: 状態遷移 (未ログイン → ログイン → ログアウト)
 */
import { http, HttpResponse } from 'msw'
import { server } from '../test/server'
import {
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

    it('401 + リフレッシュも 401 のときは例外を伝播し、メモリがクリアされる', async () => {
      // login のインターセプターが refresh を試みるため、refresh も失敗させる
      // (インターセプターが clearAuthData を呼ぶことを確認)
      server.use(
        http.post('/api/auth/login', () =>
          HttpResponse.json({ message: '認証失敗' }, { status: 401 })
        ),
        http.post('/api/auth/refresh', () =>
          HttpResponse.json({ message: 'リフレッシュ不可' }, { status: 401 })
        )
      )
      // window.location.href への代入を抑止 (jsdom で navigation エラーになるため)
      const originalLocation = window.location
      Object.defineProperty(window, 'location', {
        configurable: true,
        value: { href: '' },
      })

      await expect(login({ email: 'a@example.com', password: 'wrong' })).rejects.toThrow()
      expect(getAccessToken()).toBeNull()

      Object.defineProperty(window, 'location', {
        configurable: true,
        value: originalLocation,
      })
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
})
