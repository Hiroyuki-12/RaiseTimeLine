/**
 * 認証 API クライアント。
 * axios を使ってバックエンドの /api/auth/** エンドポイントを呼び出す。
 *
 * アクセストークンの保持方針:
 * - メモリ（このモジュールの変数）にのみ保持する
 * - localStorage には保存しない（XSS 攻撃でトークンが盗まれるリスクがあるため）
 * - ページをリロードするとメモリから消えるが、その場合は /api/auth/refresh を呼んで再発行する
 *
 * リフレッシュトークンの保持方針:
 * - バックエンドが HttpOnly Cookie として自動的にセットする
 * - JavaScript からは一切アクセスできない（axios の withCredentials: true で自動送信される）
 */

import axios from 'axios'

// バックエンドのレスポンス形式に合わせた型定義
export interface AuthResponse {
  accessToken: string
  userId: number
  username: string
  displayName: string
}

// アクセストークンをメモリ内に保持する変数（外部からは関数経由でのみアクセスする）
let _accessToken: string | null = null
// ユーザー情報もメモリに保持する
let _userInfo: { userId: number; username: string; displayName: string } | null = null

/** 現在保持しているアクセストークンを返す */
export const getAccessToken = () => _accessToken

/** 現在ログイン中のユーザー情報を返す */
export const getUserInfo = () => _userInfo

/** アクセストークンとユーザー情報をメモリに保存する内部関数 */
const storeAuthData = (res: AuthResponse) => {
  _accessToken = res.accessToken
  _userInfo = { userId: res.userId, username: res.username, displayName: res.displayName }
}

/** ログアウト時にメモリ上のトークンとユーザー情報をクリアする */
export const clearAuthData = () => {
  _accessToken = null
  _userInfo = null
}

/**
 * axios インスタンス。
 * withCredentials: true により、Cookie（リフレッシュトークン）が自動的に送受信される。
 * baseURL は Vite のプロキシ設定（/api → localhost:8080）を通じてバックエンドに届く。
 */
export const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true, // Cookie（リフレッシュトークン）を自動的に送受信する
  headers: { 'Content-Type': 'application/json' },
})

/**
 * リクエストインターセプター:
 * すべてのリクエストに対して、メモリ内のアクセストークンを Authorization ヘッダーに自動付与する。
 * これにより各 API 呼び出し側でヘッダーを手動でセットする必要がなくなる。
 */
apiClient.interceptors.request.use((config) => {
  if (_accessToken) {
    config.headers.Authorization = `Bearer ${_accessToken}`
  }
  return config
})

/**
 * レスポンスインターセプター:
 * 401 エラー（アクセストークン期限切れ）が返ってきたとき、リフレッシュトークンで自動的に再発行を試みる。
 * リフレッシュも失敗した場合は、ログイン画面にリダイレクトする。
 */
apiClient.interceptors.response.use(
  // 正常レスポンスはそのまま返す
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    // 401 かつリトライ済みでない場合のみリフレッシュを試みる（無限ループ防止）
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true
      try {
        const res = await refreshAccessToken()
        // リフレッシュ成功: 新しいアクセストークンで元のリクエストを再送する
        originalRequest.headers.Authorization = `Bearer ${res.accessToken}`
        return apiClient(originalRequest)
      } catch {
        // リフレッシュも失敗: セッション切れとしてログイン画面へ
        clearAuthData()
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

/**
 * 新規ユーザー登録 API。
 * POST /api/auth/register
 * 成功するとアクセストークンがメモリに保存され、リフレッシュトークンが HttpOnly Cookie にセットされる。
 */
export const register = async (data: {
  displayName: string
  username: string
  email: string
  password: string
  passwordConfirm: string
}): Promise<AuthResponse> => {
  const res = await apiClient.post<AuthResponse>('/auth/register', data)
  storeAuthData(res.data)
  return res.data
}

/**
 * ログイン API。
 * POST /api/auth/login
 * 成功するとアクセストークンがメモリに保存され、リフレッシュトークンが HttpOnly Cookie にセットされる。
 */
export const login = async (data: {
  email: string
  password: string
}): Promise<AuthResponse> => {
  const res = await apiClient.post<AuthResponse>('/auth/login', data)
  storeAuthData(res.data)
  return res.data
}

/**
 * アクセストークン再発行 API。
 * POST /api/auth/refresh
 * Cookie のリフレッシュトークンを使って新しいアクセストークンを取得する。
 * ページロード時に呼び出してセッションを復元する用途にも使う。
 */
export const refreshAccessToken = async (): Promise<AuthResponse> => {
  // インターセプターを通さず直接呼ぶ（インターセプター内からも呼ぶため循環を避ける）
  const res = await axios.post<AuthResponse>(
    '/api/auth/refresh',
    {},
    { withCredentials: true }
  )
  storeAuthData(res.data)
  return res.data
}

/**
 * ログアウト API。
 * POST /api/auth/logout
 * サーバー側でリフレッシュトークンを無効化し、Cookie をクリアする。
 * クライアント側ではメモリのアクセストークンもクリアする。
 */
export const logout = async (): Promise<void> => {
  await apiClient.post('/auth/logout')
  clearAuthData()
}
