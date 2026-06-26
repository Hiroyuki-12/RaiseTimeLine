/**
 * 投稿詳細ページ（/posts/:postId）。
 * X/Twitter のように投稿本文を上部に表示し、その下にコメント一覧と入力欄を配置する。
 * コメント投稿者本人のみ編集・削除ボタンを表示する。
 */

import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getUserInfo, refreshAccessToken } from '../api/auth'
import { type Post, fetchPost, addLike, removeLike } from '../api/post'
import { type Comment, fetchComments, createComment, updateComment, deleteComment } from '../api/comment'
import { Avatar } from '../components/Sidebar'
import ConfirmModal from '../components/ConfirmModal'

/**
 * 日時文字列を相対時間に変換するヘルパー。
 */
function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const min = Math.floor(diff / 60_000)
  if (min < 1) return 'たった今'
  if (min < 60) return `${min}分前`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h}時間前`
  const d = Math.floor(h / 24)
  if (d < 7) return `${d}日前`
  return new Date(iso).toLocaleDateString('ja-JP')
}

export default function PostDetailPage() {
  const { postId } = useParams<{ postId: string }>()
  const navigate = useNavigate()
  const numPostId = Number(postId)

  // 現在のユーザー情報
  const [currentUserId, setCurrentUserId] = useState<number | null>(null)

  // 投稿データ
  const [post, setPost] = useState<Post | null>(null)
  const [isLoadingPost, setIsLoadingPost] = useState(true)

  // いいね楽観的更新
  const [localLiked, setLocalLiked] = useState(false)
  const [localLikeCount, setLocalLikeCount] = useState(0)
  const [isLiking, setIsLiking] = useState(false)

  // コメント一覧
  const [comments, setComments] = useState<Comment[]>([])
  const [isLoadingComments, setIsLoadingComments] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  // 新規コメント入力
  const [newContent, setNewContent] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const MAX_LENGTH = 140

  // 編集中のコメント
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editContent, setEditContent] = useState('')
  const [isEditSubmitting, setIsEditSubmitting] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)

  // 削除確認モーダルの対象コメント ID（null のとき非表示）
  const [deletingId, setDeletingId] = useState<number | null>(null)

  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const loadComments = async () => {
    setIsLoadingComments(true)
    setLoadError(null)
    try {
      const data = await fetchComments(numPostId)
      setComments(data)
    } catch {
      setLoadError('コメントの読み込みに失敗しました')
    } finally {
      setIsLoadingComments(false)
    }
  }

  // ページ初期化: セッション確認 + 投稿取得 + コメント取得
  useEffect(() => {
    const init = async () => {
      // セッションがない場合はログインページへリダイレクトする
      try {
        await refreshAccessToken()
      } catch {
        navigate('/login')
        return
      }
      const info = getUserInfo()
      if (info) setCurrentUserId(info.userId)

      // 投稿本文とコメントを並行取得してロード時間を短縮する
      setIsLoadingPost(true)
      try {
        const [postData] = await Promise.all([fetchPost(numPostId), loadComments()])
        setPost(postData)
        setLocalLiked(postData.liked)
        setLocalLikeCount(postData.likeCount)
      } catch {
        navigate('/home')
      } finally {
        setIsLoadingPost(false)
      }
    }
    init()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [numPostId])

  /**
   * いいねトグル処理（楽観的更新）。
   * API 呼び出し前に UI を先に変更し、失敗したら元に戻す。
   */
  const handleLikeToggle = async () => {
    if (isLiking || !post) return
    const nextLiked = !localLiked
    const nextCount = localLikeCount + (nextLiked ? 1 : -1)
    setLocalLiked(nextLiked)
    setLocalLikeCount(nextCount)
    setIsLiking(true)
    try {
      if (nextLiked) {
        await addLike(post.id)
      } else {
        await removeLike(post.id)
      }
    } catch {
      // API 失敗時はロールバック
      setLocalLiked(localLiked)
      setLocalLikeCount(localLikeCount)
    } finally {
      setIsLiking(false)
    }
  }

  // コメント送信
  const handleSubmit = async () => {
    const trimmed = newContent.trim()
    if (!trimmed || trimmed.length > MAX_LENGTH || isSubmitting) return
    setIsSubmitting(true)
    setSubmitError(null)
    try {
      const created = await createComment(numPostId, trimmed)
      setComments((prev) => [...prev, created])
      setNewContent('')
      // コメント数をローカルで更新する
      if (post) setPost({ ...post, commentCount: post.commentCount + 1 })
    } catch {
      setSubmitError('コメントの投稿に失敗しました')
    } finally {
      setIsSubmitting(false)
    }
  }

  // コメント編集開始
  const startEdit = (comment: Comment) => {
    setEditingId(comment.id)
    setEditContent(comment.content)
    setEditError(null)
  }

  // コメント編集保存
  const handleEditSave = async (commentId: number) => {
    const trimmed = editContent.trim()
    if (!trimmed || trimmed.length > MAX_LENGTH || isEditSubmitting) return
    setIsEditSubmitting(true)
    setEditError(null)
    try {
      const updated = await updateComment(commentId, trimmed)
      setComments((prev) => prev.map((c) => (c.id === commentId ? updated : c)))
      setEditingId(null)
    } catch {
      setEditError('編集に失敗しました')
    } finally {
      setIsEditSubmitting(false)
    }
  }

  // コメント削除確認モーダルを開く
  const handleDeleteRequest = (commentId: number) => {
    setDeletingId(commentId)
  }

  // コメント削除実行
  const handleDeleteConfirmed = async () => {
    if (deletingId === null) return
    const commentId = deletingId
    setDeletingId(null)
    try {
      await deleteComment(commentId)
      setComments((prev) => prev.filter((c) => c.id !== commentId))
      // コメント数をローカルで更新する
      if (post) setPost({ ...post, commentCount: Math.max(0, post.commentCount - 1) })
    } catch {
      // 削除失敗時はコメント一覧を再取得して正確な状態に戻す
      await loadComments()
    }
  }

  // Enter キーで送信（Shift+Enter は改行）
  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }

  const isOverLimit = newContent.length > MAX_LENGTH
  const canSubmit = newContent.trim().length > 0 && !isOverLimit && !isSubmitting

  if (isLoadingPost) {
    return (
      <div style={styles.loadingContainer}>
        <p style={styles.loadingText}>読み込み中...</p>
      </div>
    )
  }

  return (
    <div style={styles.page}>
      {/* ヘッダー */}
      <div style={styles.header}>
        <button style={styles.backButton} onClick={() => navigate('/home')} aria-label="戻る">
          ← 戻る
        </button>
        <h2 style={styles.title}>投稿</h2>
      </div>

      {/* 投稿本文エリア（X/Twitter スタイル: 大きめに表示） */}
      {post && (
        <div style={styles.postArea}>
          {/* 投稿者情報 */}
          <div style={styles.postAuthorRow}>
            <Avatar displayName={post.authorDisplayName} username={post.authorUsername} size={44} />
            <div style={styles.postAuthorInfo}>
              <span style={styles.postDisplayName}>{post.authorDisplayName}</span>
              <span style={styles.postUsername}>@{post.authorUsername}</span>
            </div>
          </div>

          {/* 投稿本文（大きめのフォントで表示） */}
          <p style={styles.postContent}>{post.content}</p>

          {/* 投稿日時（絶対時間で表示） */}
          <p style={styles.postTime}>
            {new Date(post.createdAt).toLocaleString('ja-JP', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit',
            })}
          </p>

          {/* いいね数・コメント数サマリー */}
          <div style={styles.statRow}>
            <span style={styles.statItem}>
              <strong style={styles.statNum}>{post.commentCount}</strong>
              <span style={styles.statLabel}>コメント</span>
            </span>
            <span style={styles.statItem}>
              <strong style={styles.statNum}>{localLikeCount}</strong>
              <span style={styles.statLabel}>いいね</span>
            </span>
          </div>

          {/* いいねボタン（楽観的更新） */}
          <div style={styles.actionRow}>
            <button
              style={{
                ...styles.likeButton,
                color: localLiked ? '#f4212e' : '#536471',
              }}
              onClick={handleLikeToggle}
              disabled={isLiking}
              aria-label={localLiked ? 'いいねを取り消す' : 'いいね'}
            >
              {localLiked ? '❤️' : '🤍'} {localLiked ? 'いいね済み' : 'いいね'}
            </button>
          </div>
        </div>
      )}

      {/* コメント入力フォーム */}
      <div style={styles.inputArea}>
        <div style={styles.inputRow}>
          <textarea
            ref={textareaRef}
            value={newContent}
            onChange={(e) => setNewContent(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="返信を入力... (Enterで送信、Shift+Enterで改行)"
            style={styles.textarea}
            rows={2}
            disabled={isSubmitting}
            maxLength={MAX_LENGTH + 10}
          />
        </div>
        <div style={styles.inputFooter}>
          <span
            style={{
              ...styles.charCount,
              color:
                isOverLimit
                  ? '#f4212e'
                  : newContent.length > MAX_LENGTH - 20
                    ? '#ff7a00'
                    : '#536471',
            }}
          >
            {newContent.length}/{MAX_LENGTH}
          </span>
          <button
            style={{
              ...styles.submitButton,
              opacity: canSubmit ? 1 : 0.5,
              cursor: canSubmit ? 'pointer' : 'not-allowed',
            }}
            onClick={handleSubmit}
            disabled={!canSubmit}
          >
            {isSubmitting ? '送信中...' : '返信'}
          </button>
        </div>
        {submitError && <p style={styles.error}>{submitError}</p>}
      </div>

      {/* コメント一覧 */}
      <div style={styles.commentSection}>
        {isLoadingComments ? (
          <p style={styles.loadingText}>読み込み中...</p>
        ) : loadError ? (
          <p style={styles.error}>{loadError}</p>
        ) : comments.length === 0 ? (
          <p style={styles.emptyText}>まだ返信はありません。最初の返信を投稿しましょう！</p>
        ) : (
          comments.map((comment) => (
            // data-testid / data-comment-id は E2E が特定コメントの編集・削除ボタンを掴むための目印
            <div
              key={comment.id}
              style={styles.commentCard}
              data-testid="comment-item"
              data-comment-id={comment.id}
            >
              <Avatar
                displayName={comment.authorDisplayName}
                username={comment.authorUsername}
                size={40}
              />
              <div style={styles.commentBody}>
                {/* コメントヘッダー */}
                <div style={styles.commentHeader}>
                  <div style={styles.commentAuthorInfo}>
                    <span style={styles.commentDisplayName}>{comment.authorDisplayName}</span>
                    <span style={styles.commentUsername}>@{comment.authorUsername}</span>
                    <span style={styles.commentDot}>·</span>
                    <span style={styles.commentTime}>{relativeTime(comment.createdAt)}</span>
                  </div>
                  {/* コメント投稿者本人のみ編集・削除ボタンを表示する */}
                  {currentUserId === comment.authorId && editingId !== comment.id && (
                    <div style={styles.commentActions}>
                      <button
                        style={styles.iconButton}
                        title="編集"
                        onClick={() => startEdit(comment)}
                      >
                        ✏️
                      </button>
                      <button
                        style={styles.iconButton}
                        title="削除"
                        onClick={() => handleDeleteRequest(comment.id)}
                      >
                        🗑️
                      </button>
                    </div>
                  )}
                </div>

                {/* コメント本文 or 編集モード */}
                {editingId === comment.id ? (
                  <div>
                    <textarea
                      value={editContent}
                      onChange={(e) => setEditContent(e.target.value)}
                      style={styles.editTextarea}
                      rows={3}
                      disabled={isEditSubmitting}
                      maxLength={MAX_LENGTH + 10}
                    />
                    {editError && <p style={styles.error}>{editError}</p>}
                    <div style={styles.editActions}>
                      <button
                        style={styles.cancelButton}
                        onClick={() => setEditingId(null)}
                        disabled={isEditSubmitting}
                      >
                        キャンセル
                      </button>
                      <button
                        style={{
                          ...styles.saveButton,
                          opacity:
                            isEditSubmitting ||
                            !editContent.trim() ||
                            editContent.length > MAX_LENGTH
                              ? 0.5
                              : 1,
                          cursor:
                            isEditSubmitting ||
                            !editContent.trim() ||
                            editContent.length > MAX_LENGTH
                              ? 'not-allowed'
                              : 'pointer',
                        }}
                        onClick={() => handleEditSave(comment.id)}
                        disabled={
                          isEditSubmitting ||
                          !editContent.trim() ||
                          editContent.length > MAX_LENGTH
                        }
                      >
                        {isEditSubmitting ? '保存中...' : '保存'}
                      </button>
                    </div>
                  </div>
                ) : (
                  <p style={styles.commentContent}>{comment.content}</p>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {/* コメント削除確認モーダル */}
      <ConfirmModal
        isOpen={deletingId !== null}
        message="このコメントを削除しますか？"
        confirmLabel="削除する"
        onConfirm={handleDeleteConfirmed}
        onCancel={() => setDeletingId(null)}
      />
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  loadingContainer: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '60vh',
  },
  page: {
    maxWidth: 600,
    margin: '0 auto',
    fontFamily: 'inherit',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    padding: '12px 16px',
    borderBottom: '1px solid #e1e8ed',
    position: 'sticky',
    top: 0,
    background: 'rgba(255,255,255,0.9)',
    backdropFilter: 'blur(8px)',
    zIndex: 10,
  },
  backButton: {
    background: 'transparent',
    border: 'none',
    fontSize: 16,
    cursor: 'pointer',
    color: '#1d9bf0',
    padding: '4px 8px',
    borderRadius: 4,
  },
  title: {
    fontSize: 18,
    fontWeight: 800,
    color: '#0f1419',
    margin: 0,
  },
  // 投稿本文エリア
  postArea: {
    padding: '16px 16px 0',
    borderBottom: '1px solid #e1e8ed',
  },
  postAuthorRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    marginBottom: 12,
  },
  postAuthorInfo: {
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
  },
  postDisplayName: {
    fontWeight: 700,
    fontSize: 16,
    color: '#0f1419',
  },
  postUsername: {
    fontSize: 14,
    color: '#536471',
  },
  postContent: {
    fontSize: 20,
    color: '#0f1419',
    lineHeight: 1.6,
    margin: '0 0 12px',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  },
  postTime: {
    fontSize: 14,
    color: '#536471',
    margin: '0 0 12px',
  },
  statRow: {
    display: 'flex',
    gap: 20,
    padding: '12px 0',
    borderTop: '1px solid #e1e8ed',
    borderBottom: '1px solid #e1e8ed',
  },
  statItem: {
    display: 'flex',
    gap: 4,
    alignItems: 'baseline',
  },
  statNum: {
    fontSize: 15,
    fontWeight: 700,
    color: '#0f1419',
  },
  statLabel: {
    fontSize: 14,
    color: '#536471',
  },
  actionRow: {
    display: 'flex',
    padding: '8px 0',
  },
  likeButton: {
    background: 'transparent',
    border: 'none',
    fontSize: 15,
    cursor: 'pointer',
    padding: '4px 8px',
    borderRadius: 4,
    fontWeight: 600,
  },
  // コメント入力エリア
  inputArea: {
    padding: '12px 16px',
    borderBottom: '1px solid #e1e8ed',
  },
  inputRow: {
    display: 'flex',
    gap: 10,
    alignItems: 'flex-start',
  },
  textarea: {
    flex: 1,
    border: '1px solid #e1e8ed',
    borderRadius: 8,
    padding: 10,
    fontSize: 15,
    resize: 'vertical',
    boxSizing: 'border-box' as const,
    fontFamily: 'inherit',
    outline: 'none',
  },
  inputFooter: {
    display: 'flex',
    justifyContent: 'flex-end',
    alignItems: 'center',
    gap: 12,
    marginTop: 8,
  },
  charCount: {
    fontSize: 13,
  },
  submitButton: {
    padding: '8px 20px',
    background: '#1d9bf0',
    color: '#ffffff',
    border: 'none',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
  },
  // コメント一覧
  commentSection: {
    paddingBottom: 32,
  },
  commentCard: {
    display: 'flex',
    gap: 10,
    padding: '12px 16px',
    borderBottom: '1px solid #e1e8ed',
  },
  commentBody: {
    flex: 1,
    minWidth: 0,
  },
  commentHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 4,
  },
  commentAuthorInfo: {
    display: 'flex',
    alignItems: 'baseline',
    gap: 4,
    flexWrap: 'wrap' as const,
  },
  commentDisplayName: {
    fontWeight: 700,
    fontSize: 14,
    color: '#0f1419',
  },
  commentUsername: {
    fontSize: 13,
    color: '#536471',
  },
  commentDot: {
    fontSize: 13,
    color: '#536471',
  },
  commentTime: {
    fontSize: 13,
    color: '#536471',
  },
  commentContent: {
    fontSize: 15,
    color: '#0f1419',
    lineHeight: 1.6,
    margin: 0,
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  },
  commentActions: {
    display: 'flex',
    gap: 4,
    flexShrink: 0,
  },
  iconButton: {
    background: 'transparent',
    border: 'none',
    cursor: 'pointer',
    fontSize: 14,
    padding: '2px 4px',
    borderRadius: 4,
  },
  editTextarea: {
    width: '100%',
    border: '1px solid #e1e8ed',
    borderRadius: 8,
    padding: 8,
    fontSize: 14,
    resize: 'vertical',
    boxSizing: 'border-box' as const,
    fontFamily: 'inherit',
  },
  editActions: {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: 8,
    marginTop: 8,
  },
  cancelButton: {
    padding: '6px 16px',
    background: 'transparent',
    border: '1px solid #e1e8ed',
    borderRadius: 9999,
    fontSize: 13,
    cursor: 'pointer',
  },
  saveButton: {
    padding: '6px 16px',
    background: '#1d9bf0',
    color: '#ffffff',
    border: 'none',
    borderRadius: 9999,
    fontSize: 13,
    fontWeight: 700,
  },
  loadingText: {
    color: '#536471',
    fontSize: 14,
    textAlign: 'center' as const,
    padding: '24px 0',
  },
  emptyText: {
    color: '#536471',
    fontSize: 14,
    textAlign: 'center' as const,
    padding: '24px 16px',
  },
  error: {
    fontSize: 13,
    color: '#f4212e',
    margin: '4px 0 0',
  },
}
