/**
 * ログイン後のホーム画面。
 * ログインしていない場合は /login にリダイレクトする。
 * ページロード時に Cookie のリフレッシュトークンを使ってアクセストークンを復元する。
 */

import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getUserInfo, getAccessToken, refreshAccessToken, logout, clearAuthData } from '../api/auth'

export default function HomePage() {
  const navigate = useNavigate()
  // ロード中はスピナーを表示する（リフレッシュ中のちらつきを防ぐ）
  const [isLoading, setIsLoading] = useState(true)
  const [username, setUsername] = useState('')

  useEffect(() => {
    const initAuth = async () => {
      // まずメモリ内にアクセストークンがあるか確認する
      if (getAccessToken()) {
        setUsername(getUserInfo()?.username ?? '')
        setIsLoading(false)
        return
      }
      // メモリにない場合（ページリロードなど）はリフレッシュトークンで再発行を試みる
      try {
        const res = await refreshAccessToken()
        setUsername(res.username)
      } catch {
        // リフレッシュも失敗した場合はログイン画面へ（セッション切れ）
        navigate('/login')
      } finally {
        setIsLoading(false)
      }
    }
    initAuth()
  }, [navigate])

  const handleLogout = async () => {
    try {
      await logout()
    } catch {
      // ログアウト API が失敗してもクライアント側はクリアする
      clearAuthData()
    }
    navigate('/login')
  }

  if (isLoading) {
    return (
      <div style={styles.loadingContainer}>
        <p style={styles.loadingText}>読み込み中...</p>
      </div>
    )
  }

  return (
    <div style={styles.container}>
      <div style={styles.card}>
        {/* ロゴ */}
        <div style={styles.logo}>RaiseTimeLine</div>

        {/* ログイン成功メッセージ */}
        <div style={styles.successBadge}>ログイン成功</div>
        <h1 style={styles.welcome}>ようこそ、{username} さん！</h1>
        <p style={styles.description}>
          認証が完了しました。アクセストークン（15分）とリフレッシュトークン（7日間）が正常に発行されています。
        </p>

        {/* トークン情報の説明 */}
        <div style={styles.infoBox}>
          <p style={styles.infoItem}>
            <span style={styles.infoLabel}>アクセストークン</span>
            メモリに保持（15分有効）
          </p>
          <p style={styles.infoItem}>
            <span style={styles.infoLabel}>リフレッシュトークン</span>
            HttpOnly Cookie に保持（7日間有効）
          </p>
          <p style={styles.infoItem}>
            <span style={styles.infoLabel}>ページリロード後</span>
            リフレッシュトークンでアクセストークンを自動再発行
          </p>
        </div>

        <button style={styles.logoutButton} onClick={handleLogout}>
          ログアウト
        </button>
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
    background: '#f7f9f9',
    padding: 16,
  },
  card: {
    width: '100%',
    maxWidth: 480,
    padding: 40,
    background: '#ffffff',
    border: '1px solid #e1e8ed',
    borderRadius: 16,
    textAlign: 'center',
  },
  logo: {
    fontSize: 24,
    fontWeight: 900,
    color: '#1d9bf0',
    marginBottom: 24,
    letterSpacing: -1,
  },
  successBadge: {
    display: 'inline-block',
    padding: '4px 16px',
    background: '#d1fae5',
    color: '#065f46',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
    marginBottom: 16,
  },
  welcome: {
    fontSize: 22,
    fontWeight: 700,
    color: '#0f1419',
    marginBottom: 8,
  },
  description: {
    fontSize: 14,
    color: '#536471',
    lineHeight: 1.6,
    marginBottom: 24,
  },
  infoBox: {
    background: '#f7f9f9',
    border: '1px solid #e1e8ed',
    borderRadius: 8,
    padding: 16,
    marginBottom: 24,
    textAlign: 'left',
  },
  infoItem: {
    fontSize: 13,
    color: '#0f1419',
    marginBottom: 8,
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
  },
  infoLabel: {
    fontWeight: 700,
    color: '#1d9bf0',
    fontSize: 12,
  },
  logoutButton: {
    display: 'block',
    width: '100%',
    padding: '12px',
    background: 'transparent',
    color: '#f4212e',
    border: '1px solid #f4212e',
    borderRadius: 9999,
    fontSize: 15,
    fontWeight: 700,
    cursor: 'pointer',
  },
  loadingContainer: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
  },
  loadingText: {
    color: '#536471',
    fontSize: 15,
  },
}
