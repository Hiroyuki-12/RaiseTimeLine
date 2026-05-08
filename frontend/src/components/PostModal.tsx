/**
 * 新規投稿作成モーダルコンポーネント。
 * サイドバーの「投稿する」ボタンやタイムライン上部のコンパクト投稿欄から開く。
 * オーバーレイ + カードのモーダル UI で投稿を作成する。
 */

import { useState, useEffect, useRef } from 'react'
import { createPost, type Post } from '../api/post'
import { Avatar } from './Sidebar'

interface Props {
  isOpen: boolean
  displayName: string
  username: string
  avatarUrl?: string | null
  onClose: () => void
  onPostCreated: (post: Post) => void
}

export default function PostModal({
  isOpen,
  displayName,
  username,
  avatarUrl,
  onClose,
  onPostCreated,
}: Props) {
  const [content, setContent] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // 添付画像ファイルとプレビュー URL の状態を管理する
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const MAX_LENGTH = 280

  // モーダルが開いたときにテキストエリアにフォーカスする
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => textareaRef.current?.focus(), 50)
    }
  }, [isOpen])

  // Escape キーでモーダルを閉じる
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    if (isOpen) window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [isOpen, onClose])

  if (!isOpen) return null

  // 画像ファイルが選択されたときのプレビュー更新処理
  const handleImageChange = (file: File) => {
    if (imagePreview) URL.revokeObjectURL(imagePreview)
    setImageFile(file)
    setImagePreview(URL.createObjectURL(file))
  }

  /**
   * 画像選択ボタンを押したときの処理。
   * input 要素を動的に生成して click() を呼び、ファイルピッカー（Finder）を開く。
   */
  const openFilePicker = () => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/jpeg,image/png'
    input.onchange = () => {
      const file = input.files?.[0]
      if (file) handleImageChange(file)
    }
    input.click()
  }

  // 画像選択を解除する
  const handleRemoveImage = () => {
    if (imagePreview) URL.revokeObjectURL(imagePreview)
    setImageFile(null)
    setImagePreview(null)
  }

  const handleSubmit = async () => {
    if (!content.trim() || isSubmitting) return
    setIsSubmitting(true)
    setError(null)
    try {
      const post = await createPost(content.trim(), imageFile ?? undefined)
      onPostCreated(post)
      // 成功時にフォームをリセットしてからモーダルを閉じる
      setContent('')
      // プレビュー URL を解放する
      if (imagePreview) URL.revokeObjectURL(imagePreview)
      setImageFile(null)
      setImagePreview(null)
      setError(null)
      onClose()
    } catch (err: unknown) {
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

  // オーバーレイクリック時に入力内容もリセットしてから閉じる
  const handleOverlayClose = () => {
    setContent('')
    setError(null)
    if (imagePreview) URL.revokeObjectURL(imagePreview)
    setImageFile(null)
    setImagePreview(null)
    onClose()
  }

  return (
    <div style={styles.overlay} onClick={handleOverlayClose}>
      <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
        {/* モーダルヘッダー */}
        <div style={styles.modalHeader}>
          <button style={styles.closeButton} onClick={handleOverlayClose}>
            ✕
          </button>
          <span style={styles.modalTitle}>新しい投稿</span>
        </div>

        {/* 投稿入力エリア */}
        <div style={styles.inputArea}>
          <Avatar displayName={displayName} username={username} avatarUrl={avatarUrl} size={42} />
          <div style={{ flex: 1 }}>
            <textarea
              ref={textareaRef}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="いまどうしてる？"
              style={styles.textarea}
              rows={4}
              disabled={isSubmitting}
            />
            {/* 画像プレビュー（選択時のみ表示） */}
            {imagePreview && (
              <div style={{ position: 'relative', marginTop: 8 }}>
                <img
                  src={imagePreview}
                  alt="添付画像プレビュー"
                  style={styles.imagePreview}
                />
                <button
                  style={styles.removeImageButton}
                  onClick={handleRemoveImage}
                  title="画像を削除"
                >
                  ✕
                </button>
              </div>
            )}
          </div>
        </div>

        {/* 画像選択ボタン: クリックで動的に input[type=file] を生成して Finder を開く */}
        <div style={styles.imageBar}>
          <button
            type="button"
            style={{
              ...styles.imageButton,
              cursor: isSubmitting ? 'not-allowed' : 'pointer',
              opacity: isSubmitting ? 0.5 : 1,
            }}
            onClick={openFilePicker}
            disabled={isSubmitting}
          >
            🖼 画像を追加
          </button>
        </div>

        {/* フッター: 文字数カウンター・投稿ボタン */}
        <div style={styles.modalFooter}>
          {error && <span style={styles.error}>{error}</span>}
          <span
            style={{
              ...styles.counter,
              color: isOverLimit ? '#f4212e' : remaining <= 20 ? '#ffa500' : '#536471',
            }}
          >
            {remaining}
          </span>
          <button
            onClick={handleSubmit}
            disabled={isSubmitting || !content.trim() || isOverLimit}
            style={{
              ...styles.submitButton,
              opacity: isSubmitting || !content.trim() || isOverLimit ? 0.5 : 1,
              cursor: isSubmitting || !content.trim() || isOverLimit ? 'not-allowed' : 'pointer',
            }}
          >
            {isSubmitting ? '投稿中...' : '投稿'}
          </button>
        </div>
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    position: 'fixed',
    inset: 0,
    background: 'rgba(0,0,0,0.4)',
    display: 'flex',
    alignItems: 'flex-start',
    justifyContent: 'center',
    paddingTop: 60,
    zIndex: 100,
  },
  modal: {
    background: '#ffffff',
    borderRadius: 16,
    width: '100%',
    maxWidth: 600,
    boxShadow: '0 8px 32px rgba(0,0,0,0.2)',
  },
  modalHeader: {
    display: 'flex',
    alignItems: 'center',
    padding: '12px 16px',
    borderBottom: '1px solid #e1e8ed',
    gap: 16,
  },
  closeButton: {
    background: 'transparent',
    border: 'none',
    fontSize: 16,
    cursor: 'pointer',
    color: '#536471',
    padding: '4px 8px',
    borderRadius: '50%',
  },
  modalTitle: {
    fontWeight: 700,
    fontSize: 17,
    color: '#0f1419',
  },
  inputArea: {
    display: 'flex',
    gap: 12,
    padding: '16px',
  },
  textarea: {
    flex: 1,
    border: 'none',
    outline: 'none',
    resize: 'none',
    fontSize: 18,
    color: '#0f1419',
    background: 'transparent',
    fontFamily: 'inherit',
    lineHeight: 1.6,
  },
  modalFooter: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 12,
    padding: '12px 16px',
    borderTop: '1px solid #e1e8ed',
  },
  error: {
    fontSize: 13,
    color: '#f4212e',
    flex: 1,
  },
  counter: {
    fontSize: 13,
  },
  submitButton: {
    padding: '8px 24px',
    background: '#1d9bf0',
    color: '#ffffff',
    border: 'none',
    borderRadius: 9999,
    fontSize: 15,
    fontWeight: 700,
    cursor: 'pointer',
  },
  imageBar: {
    padding: '0 16px 8px',
    borderTop: '1px solid #e1e8ed',
    paddingTop: 8,
  },
  imageButton: {
    background: 'transparent',
    border: 'none',
    color: '#1d9bf0',
    fontSize: 14,
    cursor: 'pointer',
    padding: '4px 8px',
    borderRadius: 8,
  },
  imagePreview: {
    width: '100%',
    maxHeight: 200,
    objectFit: 'cover' as const,
    borderRadius: 12,
    display: 'block',
  },
  removeImageButton: {
    position: 'absolute' as const,
    top: 8,
    right: 8,
    background: 'rgba(0,0,0,0.6)',
    color: '#fff',
    border: 'none',
    borderRadius: '50%',
    width: 24,
    height: 24,
    fontSize: 12,
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
}
