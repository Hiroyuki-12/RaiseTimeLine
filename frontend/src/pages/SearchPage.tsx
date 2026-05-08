/**
 * ユーザー検索ページ（/search）。
 *
 * 機能:
 * - 入力フィールドにキーワードを入力するとデバウンス（300ms）してユーザーを検索する
 * - 1文字以上で API を呼び出し、ユーザー一覧を表示する
 * - 各ユーザー行をクリックするとプロフィールページへ遷移する
 * - フォロー/アンフォローボタンは FollowButton コンポーネントを再利用する
 */

import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { getUserInfo, refreshAccessToken, logout, clearAuthData } from '../api/auth'
import { searchUsers, type UserSummary } from '../api/user'
import Sidebar, { Avatar } from '../components/Sidebar'
import FollowButton from '../components/FollowButton'
import PostModal from '../components/PostModal'

export default function SearchPage() {
  const navigate = useNavigate()

  const [isLoading, setIsLoading] = useState(true)
  const [displayName, setDisplayName] = useState('')
  const [username, setUsername] = useState('')
  const [currentUserId, setCurrentUserId] = useState<number>(0)

  const [query, setQuery] = useState('')
  const [results, setResults] = useState<UserSummary[]>([])
  const [isSearching, setIsSearching] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)

  const [isModalOpen, setIsModalOpen] = useState(false)

  // デバウンス用タイマーの参照（クリア用）
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // セッション復元
  useEffect(() => {
    const init = async () => {
      if (getUserInfo()) {
        const info = getUserInfo()
        setDisplayName(info?.displayName ?? '')
        setUsername(info?.username ?? '')
        setCurrentUserId(info?.userId ?? 0)
        setIsLoading(false)
        return
      }
      try {
        const res = await refreshAccessToken()
        setDisplayName(res.displayName)
        setUsername(res.username)
        setCurrentUserId(res.userId)
      } catch {
        navigate('/login')
      } finally {
        setIsLoading(false)
      }
    }
    init()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /**
   * キーワードが変わったら 300ms デバウンスして検索 API を呼ぶ。
   * 1文字未満のときは API を呼ばず結果をクリアする。
   */
  const handleQueryChange = useCallback((q: string) => {
    setQuery(q)
    // 前のタイマーをキャンセルする
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current)

    if (q.trim().length === 0) {
      setResults([])
      setSearchError(null)
      return
    }

    debounceTimerRef.current = setTimeout(async () => {
      setIsSearching(true)
      setSearchError(null)
      try {
        const data = await searchUsers(q.trim())
        setResults(data)
      } catch {
        setSearchError('検索に失敗しました')
      } finally {
        setIsSearching(false)
      }
    }, 300)
  }, [])

  // アンマウント時にタイマーをクリアする
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current)
    }
  }, [])

  const handleLogout = async () => {
    try {
      await logout()
    } catch {
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
    <div style={styles.layout}>
      {/* 左サイドバー */}
      <Sidebar
        displayName={displayName}
        username={username}
        onOpenPostModal={() => setIsModalOpen(true)}
        onLogout={handleLogout}
      />

      {/* メインコンテンツ */}
      <main style={styles.main}>
        {/* ページヘッダー */}
        <div style={styles.pageHeader}>
          <h2 style={styles.pageTitle}>ユーザーを検索</h2>
        </div>

        {/* 検索入力欄 */}
        <div style={styles.searchBar}>
          <span style={styles.searchIcon}>🔍</span>
          <input
            type="text"
            value={query}
            onChange={(e) => handleQueryChange(e.target.value)}
            placeholder="ユーザーを検索..."
            style={styles.searchInput}
            autoFocus
          />
          {isSearching && <span style={styles.spinner}>⏳</span>}
        </div>

        {/* 検索結果 */}
        {searchError && <p style={styles.errorText}>{searchError}</p>}

        {query.trim().length > 0 && !isSearching && results.length === 0 && !searchError && (
          <p style={styles.emptyText}>
            「{query}」に一致するユーザーが見つかりませんでした
          </p>
        )}

        {results.map((user) => (
          <div key={user.id} style={styles.userRow}>
            {/* アバターと名前エリア: クリックでプロフィールページへ遷移 */}
            <Link
              to={`/users/${user.username}`}
              style={styles.userInfo}
            >
              <Avatar
                displayName={user.displayName}
                username={user.username}
                avatarUrl={user.avatarUrl}
                size={48}
              />
              <div style={styles.nameArea}>
                <span style={styles.displayName}>{user.displayName}</span>
                <span style={styles.handle}>@{user.username}</span>
              </div>
            </Link>

            {/* フォロー/アンフォローボタン（自分自身には表示しない） */}
            {user.id !== currentUserId && (
              <FollowButton
                userId={user.id}
                username={user.username}
                initialIsFollowing={user.isFollowing}
                onFollowChanged={(isFollowing) => {
                  // フォロー状態を検索結果に反映する
                  setResults((prev) =>
                    prev.map((u) => (u.id === user.id ? { ...u, isFollowing } : u)),
                  )
                }}
              />
            )}
          </div>
        ))}
      </main>

      {/* 投稿モーダル */}
      <PostModal
        isOpen={isModalOpen}
        displayName={displayName}
        username={username}
        onClose={() => setIsModalOpen(false)}
        onPostCreated={() => setIsModalOpen(false)}
      />
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
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
  layout: {
    display: 'flex',
    minHeight: '100vh',
    background: '#f7f9f9',
  },
  main: {
    flex: 1,
    maxWidth: 600,
    borderRight: '1px solid #e1e8ed',
    background: '#ffffff',
    minHeight: '100vh',
  },
  pageHeader: {
    padding: '12px 16px',
    borderBottom: '1px solid #e1e8ed',
    position: 'sticky',
    top: 0,
    background: 'rgba(255,255,255,0.85)',
    backdropFilter: 'blur(8px)',
    zIndex: 10,
  },
  pageTitle: {
    fontSize: 20,
    fontWeight: 800,
    color: '#0f1419',
    margin: 0,
  },
  searchBar: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    margin: '16px',
    padding: '10px 16px',
    border: '1px solid #cfd9de',
    borderRadius: 9999,
    background: '#f7f9f9',
  },
  searchIcon: {
    fontSize: 16,
    color: '#536471',
  },
  searchInput: {
    flex: 1,
    border: 'none',
    outline: 'none',
    background: 'transparent',
    fontSize: 16,
    color: '#0f1419',
    fontFamily: 'inherit',
  },
  spinner: {
    fontSize: 14,
  },
  userRow: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '12px 16px',
    borderBottom: '1px solid #e1e8ed',
    gap: 12,
  },
  userInfo: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    textDecoration: 'none',
    color: 'inherit',
    flex: 1,
    minWidth: 0,
  },
  nameArea: {
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
    minWidth: 0,
  },
  displayName: {
    fontWeight: 700,
    fontSize: 15,
    color: '#0f1419',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  handle: {
    fontSize: 14,
    color: '#536471',
  },
  errorText: {
    textAlign: 'center',
    color: '#f4212e',
    padding: 32,
    margin: 0,
  },
  emptyText: {
    textAlign: 'center',
    color: '#536471',
    padding: 32,
    margin: 0,
  },
}
