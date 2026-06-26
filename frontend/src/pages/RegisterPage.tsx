/**
 * 新規ユーザー登録画面。
 * ユーザー名・メールアドレス・パスワード・確認用パスワードを入力して
 * バックエンドの /api/auth/register を呼び出す。
 * 登録成功後はホーム画面（/home）に遷移する。
 */

import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { register } from '../api/auth'
import { AxiosError } from 'axios'

// バックエンドのバリデーションエラーはフィールドごとに返ってくる形式
interface ValidationErrors {
  [field: string]: string
}

export default function RegisterPage() {
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState('')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [fieldErrors, setFieldErrors] = useState<ValidationErrors>({})
  const [isLoading, setIsLoading] = useState(false)

  const handleSubmit = async (e: { preventDefault(): void }) => {
    e.preventDefault()
    setErrorMessage('')
    setFieldErrors({})
    setIsLoading(true)
    try {
      await register({ displayName, username, email, password, passwordConfirm })
      // 登録成功: ホーム画面に遷移する
      navigate('/home')
    } catch (err) {
      const axiosError = err as AxiosError<{ message?: string; errors?: ValidationErrors }>
      const data = axiosError.response?.data
      if (data?.errors) {
        // バリデーションエラー（フィールドごと）
        setFieldErrors(data.errors)
      } else {
        // ビジネスロジックエラー（メール重複など）
        setErrorMessage(data?.message ?? '登録に失敗しました。時間をおいて再度お試しください。')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div style={styles.layout}>
      <div style={styles.card}>
        <div style={styles.logo}>RaiseTimeLine</div>
        <h1 style={styles.title}>新規登録</h1>

        <form onSubmit={handleSubmit}>
          <div style={styles.formGroup}>
            <label style={styles.label} htmlFor="displayName">表示名</label>
            <input
              id="displayName"
              type="text"
              style={styles.input}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="日本語でも入力できます（1〜50文字）"
              required
              autoComplete="name"
            />
            {fieldErrors.displayName && <p style={styles.fieldError}>{fieldErrors.displayName}</p>}
          </div>

          <div style={styles.formGroup}>
            <label style={styles.label} htmlFor="username">ユーザー名（@handle）</label>
            <input
              id="username"
              type="text"
              style={styles.input}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="英数字・_・- で 1〜50文字"
              required
              autoComplete="username"
            />
            {fieldErrors.username && <p style={styles.fieldError}>{fieldErrors.username}</p>}
          </div>

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
            {fieldErrors.email && <p style={styles.fieldError}>{fieldErrors.email}</p>}
          </div>

          <div style={styles.formGroup}>
            <label style={styles.label} htmlFor="password">パスワード</label>
            <input
              id="password"
              type="password"
              style={styles.input}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="英字と数字を含む 8文字以上"
              required
              autoComplete="new-password"
            />
            {fieldErrors.password && <p style={styles.fieldError}>{fieldErrors.password}</p>}
          </div>

          <div style={styles.formGroup}>
            <label style={styles.label} htmlFor="passwordConfirm">パスワード（確認）</label>
            <input
              id="passwordConfirm"
              type="password"
              style={styles.input}
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              placeholder="もう一度入力してください"
              required
              autoComplete="new-password"
            />
            {fieldErrors.passwordConfirm && <p style={styles.fieldError}>{fieldErrors.passwordConfirm}</p>}
          </div>

          {/* 全体エラーメッセージ（メール重複など） */}
          {errorMessage && <p style={styles.errorText}>{errorMessage}</p>}

          <button
            type="submit"
            style={{ ...styles.button, opacity: isLoading ? 0.6 : 1 }}
            disabled={isLoading}
          >
            {isLoading ? '登録中...' : '新規登録'}
          </button>
        </form>

        <p style={styles.footer}>
          すでにアカウントをお持ちの方は{' '}
          <Link to="/login" style={styles.link}>ログイン</Link>
        </p>
      </div>
    </div>
  )
}

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
    color: '#1170b8',
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
  fieldError: {
    color: '#d61f2b',
    fontSize: 13,
    marginTop: 4,
  },
  errorText: {
    color: '#d61f2b',
    fontSize: 13,
    marginBottom: 12,
  },
  button: {
    display: 'block',
    width: '100%',
    padding: '14px',
    background: '#1170b8',
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
    color: '#1170b8',
    // 周囲の文章中に置かれるリンクは、色だけでなく下線でも区別できるようにする。
    // 色だけに頼ると色覚特性のあるユーザーがリンクと地の文を見分けられない（WCAG: link-in-text-block）。
    textDecoration: 'underline',
    fontWeight: 700,
  },
}
