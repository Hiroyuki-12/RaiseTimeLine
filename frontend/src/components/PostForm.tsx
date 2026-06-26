/**
 * 新規ポスト投稿フォームコンポーネント。
 * テキストエリアと投稿ボタンで構成し、280 文字カウンターを表示する。
 * 投稿成功後は親コンポーネント（HomePage）のタイムラインを更新する。
 */

import { useState } from 'react'
import { createPost, type Post } from '../api/post'

interface Props {
  /** 投稿成功時に呼ばれるコールバック。作成されたポストを渡す */
  onPostCreated: (post: Post) => void
}

export default function PostForm({ onPostCreated }: Props) {
  const [content, setContent] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const MAX_LENGTH = 280

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!content.trim() || isSubmitting) return
    setIsSubmitting(true)
    setError(null)
    try {
      const post = await createPost(content.trim())
      onPostCreated(post)
      setContent('')
    } catch (err: unknown) {
      // バックエンドのバリデーションエラーまたは通信エラーを表示する
      const msg =
        (
          err as {
            response?: { data?: { message?: string; errors?: Record<string, string> } }
          }
        )?.response?.data?.message ?? '投稿に失敗しました'
      setError(msg)
    } finally {
      setIsSubmitting(false)
    }
  }

  const remaining = MAX_LENGTH - content.length
  const isOverLimit = remaining < 0

  return (
    <form onSubmit={handleSubmit} style={styles.form}>
      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="いまどうしてる？"
        style={styles.textarea}
        rows={3}
        disabled={isSubmitting}
      />
      <div style={styles.footer}>
        {/* 残り文字数カウンター: 残り20文字以下は警告色、超過は赤 */}
        <span
          style={{
            ...styles.counter,
            color: isOverLimit ? '#d61f2b' : remaining <= 20 ? '#ffa500' : '#536471',
          }}
        >
          {remaining}
        </span>
        {error && <span style={styles.error}>{error}</span>}
        <button
          type="submit"
          disabled={isSubmitting || !content.trim() || isOverLimit}
          style={{
            ...styles.button,
            opacity: isSubmitting || !content.trim() || isOverLimit ? 0.5 : 1,
            cursor: isSubmitting || !content.trim() || isOverLimit ? 'not-allowed' : 'pointer',
          }}
        >
          {isSubmitting ? '投稿中...' : '投稿'}
        </button>
      </div>
    </form>
  )
}

const styles: Record<string, React.CSSProperties> = {
  form: {
    background: '#ffffff',
    border: '1px solid #e1e8ed',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  textarea: {
    width: '100%',
    border: 'none',
    outline: 'none',
    resize: 'none',
    fontSize: 16,
    color: '#0f1419',
    background: 'transparent',
    boxSizing: 'border-box',
    fontFamily: 'inherit',
    lineHeight: 1.6,
  },
  footer: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 12,
    marginTop: 8,
    borderTop: '1px solid #e1e8ed',
    paddingTop: 8,
  },
  counter: {
    fontSize: 13,
  },
  error: {
    fontSize: 13,
    color: '#d61f2b',
    flex: 1,
  },
  button: {
    padding: '8px 20px',
    background: '#1170b8',
    color: '#ffffff',
    border: 'none',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
    cursor: 'pointer',
  },
}
