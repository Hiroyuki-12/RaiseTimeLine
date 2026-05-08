/**
 * タイムライン画面（ログイン後のホーム画面）。
 *
 * レイアウト: 左サイドバー + 右メインコンテンツ（mock のデザインに合わせる）
 *
 * 機能:
 * - ページロード時にセッション復元
 * - 初回20件のタイムライン取得
 * - 無限スクロール（Intersection Observer API）
 * - 30秒ポーリングで新着件数をチェックし、バナーで通知
 * - 投稿作成モーダル・編集・削除
 * - タブ切り替え（全員 / フォロー中）※フォロー中は未実装
 */

import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getUserInfo, getAccessToken, refreshAccessToken, logout, clearAuthData } from '../api/auth'
import { fetchTimeline, fetchNewCount, type Post } from '../api/post'
import Sidebar, { Avatar } from '../components/Sidebar'
import PostCard from '../components/PostCard'
import PostModal from '../components/PostModal'

const PAGE_SIZE = 20
// ポーリング間隔: 30 秒（30,000 ミリ秒）
const POLLING_INTERVAL_MS = 30_000

export default function HomePage() {
  const navigate = useNavigate()
  const [isLoading, setIsLoading] = useState(true)
  const [displayName, setDisplayName] = useState('')
  const [username, setUsername] = useState('')
  const [currentUserId, setCurrentUserId] = useState<number>(0)

  // タイムラインの状態
  const [posts, setPosts] = useState<Post[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [timelineError, setTimelineError] = useState<string | null>(null)

  // 新着ポーリングの状態
  const [newCount, setNewCount] = useState(0)
  // ポーリング基準点: タイムライン初回取得時刻
  const fetchedAtRef = useRef<string>(new Date().toISOString())

  // モーダルの状態
  const [isModalOpen, setIsModalOpen] = useState(false)

  // タブの状態（全員 or フォロー中）
  const [activeTab, setActiveTab] = useState<'all' | 'following'>('all')

  // 無限スクロールのセンター要素
  const sentinelRef = useRef<HTMLDivElement>(null)

  // --------- タイムライン取得 ---------

  /**
   * 初回のタイムライン取得。ポーリング基準点もここでセットする。
   * tab を引数で受け取ることでタブ切り替え時にも同じ関数を使い回せる。
   */
  const loadInitialTimeline = async (tab: 'all' | 'following' = 'all') => {
    try {
      const now = new Date().toISOString()
      fetchedAtRef.current = now
      const data = await fetchTimeline(0, PAGE_SIZE, tab)
      setPosts(data)
      setPage(0)
      setHasMore(data.length === PAGE_SIZE)
      setTimelineError(null)
    } catch {
      setTimelineError('タイムラインの取得に失敗しました')
    }
  }

  // --------- 認証初期化 ---------

  useEffect(() => {
    const initAuth = async () => {
      if (getAccessToken()) {
        const info = getUserInfo()
        setDisplayName(info?.displayName ?? '')
        setUsername(info?.username ?? '')
        setCurrentUserId(info?.userId ?? 0)
        await loadInitialTimeline()
        setIsLoading(false)
        return
      }
      try {
        const res = await refreshAccessToken()
        setDisplayName(res.displayName)
        setUsername(res.username)
        setCurrentUserId(res.userId)
        await loadInitialTimeline()
      } catch {
        navigate('/login')
      } finally {
        setIsLoading(false)
      }
    }
    initAuth()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /** 次ページを追加取得する（無限スクロール用）。activeTab を参照してタイムライン種別を維持する。 */
  const loadMore = useCallback(async () => {
    if (isLoadingMore || !hasMore) return
    setIsLoadingMore(true)
    try {
      const nextPage = page + 1
      const data = await fetchTimeline(nextPage, PAGE_SIZE, activeTab)
      setPosts((prev) => {
        const existingIds = new Set(prev.map((p) => p.id))
        return [...prev, ...data.filter((p) => !existingIds.has(p.id))]
      })
      setPage(nextPage)
      setHasMore(data.length === PAGE_SIZE)
    } catch {
      // 追加読み込み失敗は静かに失敗させる
    } finally {
      setIsLoadingMore(false)
    }
  }, [isLoadingMore, hasMore, page, activeTab])

  // --------- 無限スクロール（Intersection Observer） ---------

  useEffect(() => {
    // rootMargin: '200px' で sentinel より 200px 手前から発火させ、先読みを実現する
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !isLoadingMore) {
          loadMore()
        }
      },
      { threshold: 0, rootMargin: '200px' },
    )
    const sentinel = sentinelRef.current
    if (sentinel) observer.observe(sentinel)
    return () => {
      if (sentinel) observer.unobserve(sentinel)
    }
  }, [hasMore, isLoadingMore, loadMore])

  // --------- ポーリング（30秒間隔） ---------

  useEffect(() => {
    if (isLoading) return
    const id = setInterval(async () => {
      try {
        const { count } = await fetchNewCount(fetchedAtRef.current)
        if (count > 0) setNewCount(count)
      } catch {
        // ポーリング失敗は静かに失敗させる
      }
    }, POLLING_INTERVAL_MS)
    return () => clearInterval(id)
  }, [isLoading])

  // --------- イベントハンドラー ---------

  const handleLogout = async () => {
    try {
      await logout()
    } catch {
      clearAuthData()
    }
    navigate('/login')
  }

  const handleRefresh = async () => {
    setNewCount(0)
    setPosts([])
    setPage(0)
    setHasMore(true)
    await loadInitialTimeline(activeTab)
  }

  /** タブを切り替えてタイムラインをリセット・再取得する。 */
  const handleTabChange = async (tab: 'all' | 'following') => {
    if (tab === activeTab) return
    setActiveTab(tab)
    setNewCount(0)
    setPosts([])
    setPage(0)
    setHasMore(true)
    await loadInitialTimeline(tab)
  }

  const handlePostCreated = (post: Post) => {
    fetchedAtRef.current = post.createdAt
    setPosts((prev) => {
      if (prev.some((p) => p.id === post.id)) return prev
      return [post, ...prev]
    })
  }

  const handlePostDeleted = (postId: number) => {
    setPosts((prev) => prev.filter((p) => p.id !== postId))
  }

  const handlePostUpdated = (updated: Post) => {
    setPosts((prev) => prev.map((p) => (p.id === updated.id ? updated : p)))
  }

  /**
   * いいねトグル後に posts 配列を更新するコールバック。
   * PostCard の楽観的更新と同期し、スクロール中でも正しい状態を保つ。
   */
  const handleLikeToggled = (postId: number, liked: boolean, likeCount: number) => {
    setPosts((prev) => prev.map((p) => (p.id === postId ? { ...p, liked, likeCount } : p)))
  }

  // --------- レンダリング ---------

  if (isLoading) {
    return (
      <div style={styles.loadingContainer}>
        <p style={styles.loadingText}>読み込み中...</p>
      </div>
    )
  }

  return (
    <div style={styles.layout}>
      {/* 新着フローティングバナー: 画面上部中央に固定表示 */}
      {newCount > 0 && (
        <div
          style={styles.newPostBanner}
          onClick={handleRefresh}
          role="button"
          tabIndex={0}
        >
          ↑ {newCount}件の新しい投稿を見る
        </div>
      )}

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
          <h2 style={styles.pageTitle}>ホーム</h2>
        </div>

        {/* タブ: 全員 / フォロー中（クリックで切り替え） */}
        <div style={styles.tabs}>
          <div
            style={{ ...styles.tab, ...(activeTab === 'all' ? styles.tabActive : {}) }}
            onClick={() => handleTabChange('all')}
            role="tab"
            aria-selected={activeTab === 'all'}
          >
            全員
          </div>
          <div
            style={{ ...styles.tab, ...(activeTab === 'following' ? styles.tabActive : {}) }}
            onClick={() => handleTabChange('following')}
            role="tab"
            aria-selected={activeTab === 'following'}
          >
            フォロー中
          </div>
        </div>

        {/* コンパクト投稿欄（クリックでモーダルを開く） */}
        <div style={styles.composeBar} onClick={() => setIsModalOpen(true)}>
          <Avatar displayName={displayName} username={username} size={42} />
          <span style={styles.composePlaceholder}>いまどうしてる？</span>
        </div>

        {/* タイムライン */}
        {timelineError ? (
          <p style={styles.errorText}>{timelineError}</p>
        ) : posts.length === 0 ? (
          <p style={styles.emptyText}>
            {activeTab === 'following'
              ? 'フォロー中のユーザーの投稿がありません。誰かをフォローしてみましょう！'
              : 'まだ投稿がありません。最初の投稿をしてみましょう！'}
          </p>
        ) : (
          <>
            {posts.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                currentUserId={currentUserId}
                onDeleted={handlePostDeleted}
                onUpdated={handlePostUpdated}
                onLikeToggled={handleLikeToggled}
              />
            ))}
            {/* 無限スクロールのセンター要素 */}
            <div ref={sentinelRef} style={styles.sentinel}>
              {isLoadingMore && <p style={styles.loadingMoreText}>読み込み中...</p>}
              {/* hasMore=true かつ isLoadingMore=false のとき「さらに読み込む」ボタンを表示する。
                  IntersectionObserver が発火しない環境（古いブラウザ等）向けのフォールバック。 */}
              {hasMore && !isLoadingMore && (
                <button style={styles.loadMoreButton} onClick={loadMore}>
                  さらに読み込む
                </button>
              )}
              {!hasMore && posts.length > 0 && (
                <p style={styles.noMoreText}>すべての投稿を読み込みました</p>
              )}
            </div>
          </>
        )}
      </main>

      {/* 投稿モーダル */}
      <PostModal
        isOpen={isModalOpen}
        displayName={displayName}
        username={username}
        onClose={() => setIsModalOpen(false)}
        onPostCreated={handlePostCreated}
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
  tabs: {
    display: 'flex',
    borderBottom: '1px solid #e1e8ed',
  },
  tab: {
    flex: 1,
    textAlign: 'center',
    padding: '14px 0',
    fontSize: 15,
    fontWeight: 600,
    color: '#536471',
    cursor: 'pointer',
  },
  tabActive: {
    color: '#0f1419',
    borderBottom: '2px solid #1d9bf0',
  },
  composeBar: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    padding: '12px 16px',
    borderBottom: '1px solid #e1e8ed',
    cursor: 'pointer',
  },
  composePlaceholder: {
    fontSize: 18,
    color: '#536471',
    flex: 1,
  },
  newPostBanner: {
    position: 'fixed',
    top: 16,
    left: '50%',
    transform: 'translateX(-50%)',
    background: '#1d9bf0',
    color: '#ffffff',
    padding: '10px 24px',
    borderRadius: 9999,
    boxShadow: '0 4px 16px rgba(0,0,0,0.2)',
    cursor: 'pointer',
    fontSize: 14,
    fontWeight: 700,
    zIndex: 200,
    whiteSpace: 'nowrap',
  },
  errorText: {
    textAlign: 'center',
    color: '#f4212e',
    padding: 32,
  },
  emptyText: {
    textAlign: 'center',
    color: '#536471',
    padding: 32,
  },
  sentinel: {
    height: 40,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingMoreText: {
    color: '#536471',
    fontSize: 14,
    margin: 0,
  },
  loadMoreButton: {
    padding: '8px 24px',
    background: 'transparent',
    color: '#1d9bf0',
    border: '1px solid #1d9bf0',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
    cursor: 'pointer',
  },
  noMoreText: {
    color: '#536471',
    fontSize: 13,
    margin: 0,
  },
}
