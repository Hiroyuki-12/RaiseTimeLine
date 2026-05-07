/**
 * プロフィールページ（/users/:username）。
 *
 * レイアウト: 左サイドバー + 右メインコンテンツ
 *
 * 機能:
 * - ユーザー名（@handle）でプロフィールを取得・表示
 * - 自分のプロフィール: 「プロフィールを編集」ボタン + ProfileEditModal
 * - 他ユーザーのプロフィール: フォロー/アンフォローボタン
 * - フォロー数・フォロワー数（クリックでフォロー一覧へ）
 * - 投稿一覧（既存 PostCard を再利用）
 */

import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { getUserInfo, refreshAccessToken, logout, clearAuthData } from '../api/auth'
import {
  fetchUserProfile,
  fetchUserPosts,
  type UserProfile,
} from '../api/user'
import { type Post } from '../api/post'
import Sidebar, { Avatar } from '../components/Sidebar'
import PostCard from '../components/PostCard'
import FollowButton from '../components/FollowButton'
import ProfileEditModal from '../components/ProfileEditModal'
import PostModal from '../components/PostModal'

export default function ProfilePage() {
  const { username } = useParams<{ username: string }>()
  const navigate = useNavigate()

  const [isLoading, setIsLoading] = useState(true)
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [posts, setPosts] = useState<Post[]>([])
  const [error, setError] = useState<string | null>(null)

  const [currentUserId, setCurrentUserId] = useState<number>(0)
  const [currentUsername, setCurrentUsername] = useState('')
  const [currentDisplayName, setCurrentDisplayName] = useState('')

  // プロフィール編集モーダルの表示状態
  const [isEditModalOpen, setIsEditModalOpen] = useState(false)
  // 投稿モーダルの表示状態
  const [isPostModalOpen, setIsPostModalOpen] = useState(false)

  // セッション復元 + プロフィール取得
  useEffect(() => {
    const initialize = async () => {
      // メモリ上にユーザー情報がなければリフレッシュトークンで再取得する
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

      // プロフィールと投稿一覧を並行して取得する
      if (!username) return
      try {
        const [profileData, postsData] = await Promise.all([
          fetchUserProfile(username),
          fetchUserPosts(username),
        ])
        setProfile(profileData)
        setPosts(postsData)
      } catch {
        setError('ユーザーが見つかりません')
      } finally {
        setIsLoading(false)
      }
    }

    initialize()
  }, [username, navigate])

  const handleLogout = async () => {
    await logout()
    clearAuthData()
    navigate('/login', { replace: true })
  }

  /** プロフィール編集保存後: プロフィール情報を更新し、URL も新しい username に合わせる */
  const handleProfileSaved = (updated: UserProfile) => {
    setProfile(updated)
    // ユーザー名が変更されている場合は URL を更新する
    if (updated.username !== username) {
      navigate(`/users/${updated.username}`, { replace: true })
    }
    setIsEditModalOpen(false)
  }

  /** フォロー状態が変わったときにプロフィールのカウントを更新する */
  const handleFollowChanged = (isFollowing: boolean) => {
    if (!profile) return
    setProfile({
      ...profile,
      isFollowing,
      followerCount: profile.followerCount + (isFollowing ? 1 : -1),
    })
  }

  /** PostCard の更新コールバック */
  const handlePostUpdated = (updated: Post) => {
    setPosts((prev) => prev.map((p) => (p.id === updated.id ? updated : p)))
  }

  /** PostCard の削除コールバック */
  const handlePostDeleted = (postId: number) => {
    setPosts((prev) => prev.filter((p) => p.id !== postId))
  }

  /** PostCard のいいねコールバック */
  const handleLikeToggled = (postId: number, liked: boolean, likeCount: number) => {
    setPosts((prev) => prev.map((p) => (p.id === postId ? { ...p, liked, likeCount } : p)))
  }

  if (isLoading) {
    return (
      <div style={styles.loadingContainer}>
        <p>読み込み中...</p>
      </div>
    )
  }

  if (error || !profile) {
    return (
      <div style={styles.loadingContainer}>
        <p style={{ color: '#f4212e' }}>{error ?? 'エラーが発生しました'}</p>
        <button onClick={() => navigate('/home')} style={styles.backButton}>
          ホームへ戻る
        </button>
      </div>
    )
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
        {/* プロフィールヘッダー */}
        <div style={styles.profileHeader}>
          <div style={styles.profileTop}>
            <Avatar displayName={profile.displayName} username={profile.username} size={80} />

            {/* フォロー/編集ボタン */}
            <div>
              {profile.isOwnProfile ? (
                <button
                  style={styles.editButton}
                  onClick={() => setIsEditModalOpen(true)}
                >
                  プロフィールを編集
                </button>
              ) : (
                <FollowButton
                  userId={profile.id}
                  username={profile.username}
                  initialIsFollowing={profile.isFollowing}
                  onFollowChanged={handleFollowChanged}
                />
              )}
            </div>
          </div>

          {/* ユーザー名・handle */}
          <div style={styles.profileNames}>
            <span style={styles.displayName}>{profile.displayName}</span>
            <span style={styles.handle}>@{profile.username}</span>
          </div>

          {/* 自己紹介 */}
          {profile.bio && <p style={styles.bio}>{profile.bio}</p>}

          {/* フォロー数・フォロワー数 */}
          <div style={styles.stats}>
            <Link to={`/users/${profile.username}/follows?tab=following`} style={styles.statLink}>
              <span style={styles.statNumber}>{profile.followingCount}</span>
              <span style={styles.statLabel}>フォロー中</span>
            </Link>
            <Link to={`/users/${profile.username}/follows?tab=followers`} style={styles.statLink}>
              <span style={styles.statNumber}>{profile.followerCount}</span>
              <span style={styles.statLabel}>フォロワー</span>
            </Link>
          </div>
        </div>

        {/* 投稿一覧 */}
        <div style={styles.timeline}>
          {posts.length === 0 ? (
            <div style={styles.empty}>
              <p style={{ color: '#536471' }}>まだ投稿がありません</p>
            </div>
          ) : (
            posts.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                currentUserId={currentUserId}
                onDeleted={handlePostDeleted}
                onUpdated={handlePostUpdated}
                onLikeToggled={handleLikeToggled}
              />
            ))
          )}
        </div>
      </main>

      {/* プロフィール編集モーダル */}
      {isEditModalOpen && (
        <ProfileEditModal
          profile={profile}
          onClose={() => setIsEditModalOpen(false)}
          onSaved={handleProfileSaved}
        />
      )}

      {/* 投稿モーダル */}
      <PostModal
        isOpen={isPostModalOpen}
        displayName={currentDisplayName}
        username={currentUsername}
        onClose={() => setIsPostModalOpen(false)}
        onPostCreated={(p) => setPosts((prev) => [p, ...prev])}
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
  loadingContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    height: '100vh',
    gap: 16,
  },
  backButton: {
    padding: '8px 20px',
    background: '#1d9bf0',
    color: '#ffffff',
    border: 'none',
    borderRadius: 9999,
    fontSize: 14,
    cursor: 'pointer',
  },
  profileHeader: {
    padding: '20px 16px',
    borderBottom: '1px solid #e1e8ed',
  },
  profileTop: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  profileNames: {
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
    marginBottom: 8,
  },
  displayName: {
    fontSize: 20,
    fontWeight: 700,
    color: '#0f1419',
  },
  handle: {
    fontSize: 15,
    color: '#536471',
  },
  bio: {
    fontSize: 15,
    color: '#0f1419',
    lineHeight: 1.6,
    margin: '0 0 12px',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  },
  stats: {
    display: 'flex',
    gap: 20,
    marginTop: 8,
  },
  statLink: {
    display: 'flex',
    gap: 4,
    textDecoration: 'none',
    color: 'inherit',
  },
  statNumber: {
    fontSize: 15,
    fontWeight: 700,
    color: '#0f1419',
  },
  statLabel: {
    fontSize: 15,
    color: '#536471',
  },
  editButton: {
    padding: '8px 20px',
    background: 'transparent',
    color: '#0f1419',
    border: '1px solid #cfd9de',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
    cursor: 'pointer',
  },
  timeline: {
    display: 'flex',
    flexDirection: 'column',
  },
  empty: {
    padding: '40px 16px',
    textAlign: 'center',
  },
}
