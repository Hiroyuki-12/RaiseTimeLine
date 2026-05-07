/**
 * フォロー/フォロワー一覧ページ（/users/:username/follows）。
 *
 * URL のクエリパラメータ ?tab=following|followers でタブを切り替える。
 *
 * 機能:
 * - フォロー中タブ: 指定ユーザーがフォローしているユーザー一覧
 * - フォロワータブ: 指定ユーザーをフォローしているユーザー一覧
 * - 各ユーザー行にフォロー/アンフォローボタンを表示（自分の行には表示しない）
 * - ユーザー名クリックでプロフィールページへ遷移
 */

import { useState, useEffect } from 'react'
import { useParams, useNavigate, useSearchParams, Link } from 'react-router-dom'
import { getUserInfo, refreshAccessToken, logout, clearAuthData } from '../api/auth'
import { fetchFollowing, fetchFollowers, type UserSummary } from '../api/user'
import Sidebar, { Avatar } from '../components/Sidebar'
import FollowButton from '../components/FollowButton'
import PostModal from '../components/PostModal'

type Tab = 'following' | 'followers'

export default function FollowListPage() {
  const { username } = useParams<{ username: string }>()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  // ?tab= クエリパラメータでタブを初期化する（デフォルトは following）
  const initialTab = (searchParams.get('tab') as Tab) ?? 'following'
  const [activeTab, setActiveTab] = useState<Tab>(initialTab)
  const [users, setUsers] = useState<UserSummary[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const [currentUserId, setCurrentUserId] = useState<number>(0)
  const [currentUsername, setCurrentUsername] = useState('')
  const [currentDisplayName, setCurrentDisplayName] = useState('')

  const [isPostModalOpen, setIsPostModalOpen] = useState(false)

  // セッション復元
  useEffect(() => {
    const initialize = async () => {
      let userInfo = getUserInfo()
      if (!userInfo) {
        try {
          await refreshAccessToken()
          userInfo = getUserInfo()
        } catch {
          navigate('/login', { replace: true })
          return
        }
      }

      if (!userInfo) {
        navigate('/login', { replace: true })
        return
      }

      setCurrentUserId(userInfo.userId)
      setCurrentUsername(userInfo.username)
      setCurrentDisplayName(userInfo.displayName)
    }

    initialize()
  }, [navigate])

  // タブ切り替え時にユーザー一覧を再取得する
  useEffect(() => {
    if (!username) return

    const loadUsers = async () => {
      setIsLoading(true)
      try {
        const data =
          activeTab === 'following'
            ? await fetchFollowing(username)
            : await fetchFollowers(username)
        setUsers(data)
      } catch {
        setUsers([])
      } finally {
        setIsLoading(false)
      }
    }

    loadUsers()
  }, [username, activeTab])

  const handleTabChange = (tab: Tab) => {
    setActiveTab(tab)
    // URL のクエリパラメータも更新してブラウザの戻るボタンに対応する
    setSearchParams({ tab })
  }

  const handleLogout = async () => {
    await logout()
    clearAuthData()
    navigate('/login', { replace: true })
  }

  /** フォロー状態が変わったときに対象ユーザーの isFollowing を更新する */
  const handleFollowChanged = (userId: number, isFollowing: boolean) => {
    setUsers((prev) => prev.map((u) => (u.id === userId ? { ...u, isFollowing } : u)))
  }

  return (
    <div style={styles.layout}>
      {/* 左サイドバー */}
      <Sidebar
        displayName={currentDisplayName}
        username={currentUsername}
        onOpenPostModal={() => setIsPostModalOpen(true)}
        onLogout={handleLogout}
      />

      {/* メインコンテンツ */}
      <main style={styles.main}>
        {/* ヘッダー: ページタイトル + 戻るリンク */}
        <div style={styles.pageHeader}>
          <Link to={`/users/${username}`} style={styles.backLink}>
            ← @{username}
          </Link>
          <h1 style={styles.pageTitle}>
            {activeTab === 'following' ? 'フォロー中' : 'フォロワー'}
          </h1>
        </div>

        {/* タブ */}
        <div style={styles.tabs}>
          <button
            style={{
              ...styles.tab,
              borderBottom: activeTab === 'following' ? '2px solid #1d9bf0' : '2px solid transparent',
              color: activeTab === 'following' ? '#1d9bf0' : '#536471',
            }}
            onClick={() => handleTabChange('following')}
          >
            フォロー中
          </button>
          <button
            style={{
              ...styles.tab,
              borderBottom: activeTab === 'followers' ? '2px solid #1d9bf0' : '2px solid transparent',
              color: activeTab === 'followers' ? '#1d9bf0' : '#536471',
            }}
            onClick={() => handleTabChange('followers')}
          >
            フォロワー
          </button>
        </div>

        {/* ユーザー一覧 */}
        {isLoading ? (
          <div style={styles.loading}>
            <p>読み込み中...</p>
          </div>
        ) : users.length === 0 ? (
          <div style={styles.empty}>
            <p style={{ color: '#536471' }}>
              {activeTab === 'following' ? 'フォロー中のユーザーはいません' : 'フォロワーはいません'}
            </p>
          </div>
        ) : (
          <div>
            {users.map((user) => (
              <div key={user.id} style={styles.userRow}>
                {/* アバター + 名前（クリックでプロフィールへ遷移） */}
                <Link to={`/users/${user.username}`} style={styles.userInfo}>
                  <Avatar displayName={user.displayName} username={user.username} size={48} />
                  <div style={styles.userNames}>
                    <span style={styles.displayName}>{user.displayName}</span>
                    <span style={styles.handle}>@{user.username}</span>
                  </div>
                </Link>

                {/* フォロー/アンフォローボタン（自分の行には表示しない） */}
                {user.id !== currentUserId && (
                  <FollowButton
                    userId={user.id}
                    username={user.username}
                    initialIsFollowing={user.isFollowing}
                    onFollowChanged={(isFollowing) => handleFollowChanged(user.id, isFollowing)}
                  />
                )}
              </div>
            ))}
          </div>
        )}
      </main>

      {/* 投稿モーダル */}
      <PostModal
        isOpen={isPostModalOpen}
        displayName={currentDisplayName}
        username={currentUsername}
        onClose={() => setIsPostModalOpen(false)}
        onPostCreated={() => setIsPostModalOpen(false)}
      />
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  layout: {
    display: 'flex',
    minHeight: '100vh',
    background: '#ffffff',
  },
  main: {
    flex: 1,
    maxWidth: 600,
    borderRight: '1px solid #e1e8ed',
  },
  pageHeader: {
    padding: '16px',
    borderBottom: '1px solid #e1e8ed',
  },
  backLink: {
    fontSize: 14,
    color: '#1d9bf0',
    textDecoration: 'none',
    display: 'block',
    marginBottom: 4,
  },
  pageTitle: {
    fontSize: 18,
    fontWeight: 700,
    color: '#0f1419',
    margin: 0,
  },
  tabs: {
    display: 'flex',
    borderBottom: '1px solid #e1e8ed',
  },
  tab: {
    flex: 1,
    padding: '16px 0',
    background: 'transparent',
    border: 'none',
    borderBottom: '2px solid transparent',
    fontSize: 15,
    fontWeight: 700,
    cursor: 'pointer',
    transition: 'color 0.15s',
  },
  loading: {
    padding: '40px 16px',
    textAlign: 'center',
  },
  empty: {
    padding: '40px 16px',
    textAlign: 'center',
  },
  userRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '12px 16px',
    borderBottom: '1px solid #e1e8ed',
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
  userNames: {
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
    minWidth: 0,
  },
  displayName: {
    fontSize: 15,
    fontWeight: 700,
    color: '#0f1419',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  handle: {
    fontSize: 14,
    color: '#536471',
  },
}
