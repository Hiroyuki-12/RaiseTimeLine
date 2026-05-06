/**
 * タイムライン上の1件のポストを表示するカードコンポーネント。
 * mock のデザインに合わせて:
 * - カラーアバター（イニシャル丸）
 * - 相対時間（3分前 など）
 * - ♥ いいね数・💬 コメント数（現時点は 0 固定。今後の機能追加で更新する）
 * - 投稿者本人のみ編集(✏)・削除(🗑)アイコンを表示
 */

import { useState } from 'react'
import { type Post, updatePost, deletePost } from '../api/post'
import { Avatar } from './Sidebar'

interface Props {
  post: Post
  /** ログイン中のユーザーの ID（本人判定に使う） */
  currentUserId: number
  /** 削除成功時に親コンポーネントへ通知するコールバック */
  onDeleted: (postId: number) => void
  /** 編集成功時に親コンポーネントへ通知するコールバック */
  onUpdated: (post: Post) => void
}

/**
 * 日時文字列を相対時間（3分前、1時間前 など）に変換するヘルパー。
 * mock の relativeTime 関数と同様のロジック。
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

export default function PostCard({ post, currentUserId, onDeleted, onUpdated }: Props) {
  const [isEditing, setIsEditing] = useState(false)
  const [editContent, setEditContent] = useState(post.content)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 投稿者本人かどうかを判定する
  const isOwner = post.authorId === currentUserId

  const handleUpdate = async () => {
    if (!editContent.trim() || isSubmitting) return
    setIsSubmitting(true)
    setError(null)
    try {
      const updated = await updatePost(post.id, editContent.trim())
      onUpdated(updated)
      setIsEditing(false)
    } catch {
      setError('更新に失敗しました')
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleDelete = async () => {
    if (!window.confirm('このポストを削除しますか？')) return
    setIsSubmitting(true)
    try {
      await deletePost(post.id)
      onDeleted(post.id)
    } catch {
      setError('削除に失敗しました')
      setIsSubmitting(false)
    }
  }

  return (
    <div style={styles.card}>
      {/* 左カラム: アバター */}
      <Avatar displayName={post.authorDisplayName} username={post.authorUsername} size={42} />

      {/* 右カラム: ヘッダー・本文・フッター */}
      <div style={styles.body}>
        {/* ヘッダー: 表示名・@handle・相対時間・操作ボタン */}
        <div style={styles.header}>
          <div style={styles.authorInfo}>
            <span style={styles.displayName}>{post.authorDisplayName}</span>
            <span style={styles.username}>@{post.authorUsername}</span>
            <span style={styles.dot}>·</span>
            <span style={styles.time}>{relativeTime(post.createdAt)}</span>
          </div>
          {/* 本人のみ編集・削除アイコンを表示する */}
          {isOwner && !isEditing && (
            <div style={styles.actions}>
              <button
                style={styles.iconButton}
                title="編集"
                onClick={() => {
                  setIsEditing(true)
                  setEditContent(post.content)
                }}
              >
                ✏️
              </button>
              <button
                style={styles.iconButton}
                title="削除"
                onClick={handleDelete}
                disabled={isSubmitting}
              >
                🗑️
              </button>
            </div>
          )}
        </div>

        {/* 本文: 通常表示 or 編集モード */}
        {isEditing ? (
          <div>
            <textarea
              value={editContent}
              onChange={(e) => setEditContent(e.target.value)}
              style={styles.editTextarea}
              rows={3}
              disabled={isSubmitting}
            />
            {error && <p style={styles.error}>{error}</p>}
            <div style={styles.editActions}>
              <button
                style={styles.cancelButton}
                onClick={() => setIsEditing(false)}
                disabled={isSubmitting}
              >
                キャンセル
              </button>
              <button
                style={{
                  ...styles.saveButton,
                  opacity: isSubmitting || !editContent.trim() ? 0.5 : 1,
                  cursor: isSubmitting || !editContent.trim() ? 'not-allowed' : 'pointer',
                }}
                onClick={handleUpdate}
                disabled={isSubmitting || !editContent.trim()}
              >
                {isSubmitting ? '保存中...' : '保存'}
              </button>
            </div>
          </div>
        ) : (
          <p style={styles.content}>{post.content}</p>
        )}

        {/* フッター: ♥ いいね数・💬 コメント数（現時点は 0 固定） */}
        {!isEditing && (
          <div style={styles.footer}>
            <span style={styles.reaction}>
              <span style={styles.reactionIcon}>🤍</span>
              <span style={styles.reactionCount}>0</span>
            </span>
            <span style={styles.reaction}>
              <span style={styles.reactionIcon}>💬</span>
              <span style={styles.reactionCount}>0</span>
            </span>
          </div>
        )}

        {error && !isEditing && <p style={styles.error}>{error}</p>}
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  card: {
    display: 'flex',
    gap: 12,
    padding: '12px 16px',
    borderBottom: '1px solid #e1e8ed',
    background: '#ffffff',
  },
  body: {
    flex: 1,
    minWidth: 0,
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 4,
  },
  authorInfo: {
    display: 'flex',
    alignItems: 'baseline',
    gap: 4,
    flexWrap: 'wrap',
  },
  displayName: {
    fontWeight: 700,
    fontSize: 15,
    color: '#0f1419',
  },
  username: {
    fontSize: 14,
    color: '#536471',
  },
  dot: {
    fontSize: 14,
    color: '#536471',
  },
  time: {
    fontSize: 14,
    color: '#536471',
  },
  content: {
    fontSize: 15,
    color: '#0f1419',
    lineHeight: 1.6,
    margin: '0 0 8px',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  },
  footer: {
    display: 'flex',
    gap: 24,
    marginTop: 8,
  },
  reaction: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
    cursor: 'default',
  },
  reactionIcon: {
    fontSize: 16,
  },
  reactionCount: {
    fontSize: 13,
    color: '#536471',
  },
  actions: {
    display: 'flex',
    gap: 4,
    flexShrink: 0,
  },
  iconButton: {
    background: 'transparent',
    border: 'none',
    cursor: 'pointer',
    fontSize: 15,
    padding: '2px 4px',
    borderRadius: 4,
    lineHeight: 1,
  },
  editTextarea: {
    width: '100%',
    border: '1px solid #e1e8ed',
    borderRadius: 8,
    padding: 8,
    fontSize: 15,
    resize: 'vertical',
    boxSizing: 'border-box',
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
    fontSize: 14,
    cursor: 'pointer',
  },
  saveButton: {
    padding: '6px 16px',
    background: '#1d9bf0',
    color: '#ffffff',
    border: 'none',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
    cursor: 'pointer',
  },
  error: {
    fontSize: 13,
    color: '#f4212e',
    margin: '4px 0 0',
  },
}
