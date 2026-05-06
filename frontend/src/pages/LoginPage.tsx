/**
 * ログイン画面。
 * メールアドレスとパスワードを入力してバックエンドの /api/auth/login を呼び出す。
 * ログイン成功後はホーム画面（/home）に遷移する。
 */

import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { login } from '../api/auth'
import { AxiosError } from 'axios'

export default function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const handleSubmit = async (e: { preventDefault(): void }) => {
    e.preventDefault()
    setErrorMessage('')
    setIsLoading(true)
    try {
      await login({ email, password })
      // ログイン成功: ホーム画面に遷移する
      navigate('/home')
    } catch (err) {
      const axiosError = err as AxiosError<{ message: string }>
      setErrorMessage(
        axiosError.response?.data?.message ?? 'ログインに失敗しました。時間をおいて再度お試しください。'
      )
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div style={styles.layout}>
      <div style={styles.card}>
        {/* ロゴ */}
        <div style={styles.logo}>RaiseTimeLine</div>
        <h1 style={styles.title}>ログイン</h1>

        <form onSubmit={handleSubmit}>
          <div style={styles.formGroup}>
            <label style={styles.label} htmlFor="email">メールアドレス</label>
            <input
              id="email"
              type="email"
              style={styles.input}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="example@example.com"
              required
              autoComplete="email"
            />
          </div>

          <div style={styles.formGroup}>
            <label style={styles.label} htmlFor="password">パスワード</label>
            <input
              id="password"
              type="password"
              style={styles.input}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="パスワードを入力"
              required
              autoComplete="current-password"
            />
          </div>

          {/* エラーメッセージ */}
          {errorMessage && <p style={styles.errorText}>{errorMessage}</p>}

          <button
            type="submit"
            style={{ ...styles.button, opacity: isLoading ? 0.6 : 1 }}
            disabled={isLoading}
          >
            {isLoading ? 'ログイン中...' : 'ログイン'}
          </button>
        </form>

        <p style={styles.footer}>
          アカウントをお持ちでない方は{' '}
          <Link to="/register" style={styles.link}>新規登録</Link>
        </p>
      </div>
    </div>
  )
}

// プロトタイプ（docs/mock/index.html）の .auth-card デザインを参考にしたスタイル
const styles: Record<string, React.CSSProperties> = {
  layout: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
    background: '#ffffff',
  },
  card: {
    width: '100%',
    maxWidth: 400,
    padding: 40,
    background: '#f7f9f9',
    border: '1px solid #e1e8ed',
    borderRadius: 16,
  },
  logo: {
    fontSize: 28,
    fontWeight: 900,
    color: '#1d9bf0',
    textAlign: 'center',
    marginBottom: 24,
    letterSpacing: -1,
  },
  title: {
    fontSize: 24,
    fontWeight: 700,
    marginBottom: 24,
    color: '#0f1419',
  },
  formGroup: {
    marginBottom: 16,
  },
  label: {
    display: 'block',
    fontSize: 13,
    color: '#536471',
    marginBottom: 6,
  },
  input: {
    width: '100%',
    padding: '12px 14px',
    background: 'transparent',
    border: '1px solid #e1e8ed',
    borderRadius: 4,
    color: '#0f1419',
    fontSize: 15,
    outline: 'none',
    boxSizing: 'border-box',
  },
  errorText: {
    color: '#f4212e',
    fontSize: 13,
    marginBottom: 12,
  },
  button: {
    display: 'block',
    width: '100%',
    padding: '14px',
    background: '#1d9bf0',
    color: 'white',
    border: 'none',
    borderRadius: 9999,
    fontSize: 16,
    fontWeight: 700,
    cursor: 'pointer',
    marginTop: 8,
  },
  footer: {
    marginTop: 20,
    textAlign: 'center',
    color: '#536471',
    fontSize: 14,
  },
  link: {
    color: '#1d9bf0',
    textDecoration: 'none',
  },
}
