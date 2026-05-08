/**
 * プロフィール編集モーダルコンポーネント。
 * ユーザー名・表示名・自己紹介を編集して保存する。
 * アバター画像のアップロードは今回スコープ外（S3 連携未整備）。
 */

import React, { useState } from 'react'
import { updateProfile, uploadAvatar, type UserProfile, type UpdateProfileRequest } from '../api/user'
import { Avatar } from './Sidebar'

interface Props {
  /** 編集対象のプロフィール（初期値として使う） */
  profile: UserProfile
  /** モーダルを閉じるコールバック */
  onClose: () => void
  /** 保存成功後に更新されたプロフィールを親コンポーネントへ通知するコールバック */
  onSaved: (updated: UserProfile) => void
}

export default function ProfileEditModal({ profile, onClose, onSaved }: Props) {
  const [username, setUsername] = useState(profile.username)
  const [displayName, setDisplayName] = useState(profile.displayName)
  const [bio, setBio] = useState(profile.bio ?? '')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // アバター画像のプレビュー URL（選択時のみ表示する）
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null)
  const [avatarFile, setAvatarFile] = useState<File | null>(null)

  // 画像ファイルが選択されたときのプレビュー更新処理（input の change イベントから呼ばれる）
  const handleAvatarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (avatarPreview) URL.revokeObjectURL(avatarPreview)
    setAvatarFile(file)
    setAvatarPreview(URL.createObjectURL(file))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (isSubmitting) return
    setIsSubmitting(true)
    setError(null)

    try {
      // 画像が選択されている場合は先にアップロードする
      if (avatarFile) {
        await uploadAvatar(avatarFile)
      }
      // テキスト情報を更新する
      const req: UpdateProfileRequest = {
        username: username.trim(),
        displayName: displayName.trim(),
        bio: bio.trim(),
      }
      const updated = await updateProfile(req)
      // プレビュー URL を解放する
      if (avatarPreview) URL.revokeObjectURL(avatarPreview)
      onSaved(updated)
      onClose()
    } catch (err: unknown) {
      // バリデーションエラー（400）やユーザー名重複（409）のメッセージを表示する
      const axiosErr = err as { response?: { data?: { message?: string } } }
      setError(axiosErr.response?.data?.message ?? '保存に失敗しました')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    // オーバーレイ: 背景クリックでモーダルを閉じる
    <div style={styles.overlay} onClick={onClose} role="dialog" aria-modal="true">
      {/* モーダル本体: クリックが親（オーバーレイ）に伝播しないようにする */}
      <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div style={styles.header}>
          <h2 style={styles.title}>プロフィールを編集</h2>
          <button style={styles.closeButton} onClick={onClose}>
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} style={styles.form}>
          {/* アバター画像 */}
          <div style={styles.avatarSection}>
            {/* 現在のアバター（プレビューまたは既存画像） */}
            <Avatar
              displayName={displayName || profile.displayName}
              username={profile.username}
              avatarUrl={avatarPreview ?? profile.avatarUrl}
              size={80}
            />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span style={styles.label}>プロフィール画像</span>
              {/* ネイティブの <input type="file"> をそのまま表示する。
                  隠さず素のままにすることで、どのブラウザでも確実にクリック → ファイルピッカーが開く。 */}
              <input
                type="file"
                accept="image/jpeg,image/png"
                onChange={handleAvatarChange}
                disabled={isSubmitting}
                style={{
                  fontSize: 14,
                  cursor: isSubmitting ? 'not-allowed' : 'pointer',
                }}
              />
              <span style={styles.hint}>JPEG または PNG、2MB 以下</span>
            </div>
          </div>

          {/* ユーザー名 */}
          <div style={styles.field}>
            <label style={styles.label}>ユーザー名</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              style={styles.input}
              maxLength={50}
              placeholder="username"
              disabled={isSubmitting}
            />
            <span style={styles.hint}>英数字・アンダースコア・ハイフンのみ使用できます</span>
          </div>

          {/* 表示名 */}
          <div style={styles.field}>
            <label style={styles.label}>表示名</label>
            <input
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              style={styles.input}
              maxLength={50}
              placeholder="表示名"
              disabled={isSubmitting}
            />
          </div>

          {/* 自己紹介 */}
          <div style={styles.field}>
            <label style={styles.label}>自己紹介</label>
            <textarea
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              style={styles.textarea}
              maxLength={160}
              rows={4}
              placeholder="自己紹介（160文字以内）"
              disabled={isSubmitting}
            />
            {/* 残り文字数を表示してユーザーが160文字制限を意識できるようにする */}
            <span style={styles.charCount}>{bio.length}/160</span>
          </div>

          {error && <p style={styles.error}>{error}</p>}

          <div style={styles.actions}>
            <button
              type="button"
              style={styles.cancelButton}
              onClick={onClose}
              disabled={isSubmitting}
            >
              キャンセル
            </button>
            <button
              type="submit"
              style={{
                ...styles.saveButton,
                opacity: isSubmitting ? 0.5 : 1,
                cursor: isSubmitting ? 'not-allowed' : 'pointer',
              }}
              disabled={isSubmitting}
            >
              {isSubmitting ? '保存中...' : '保存する'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    position: 'fixed',
    inset: 0,
    background: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
  modal: {
    background: '#ffffff',
    borderRadius: 16,
    padding: '24px 28px',
    width: 480,
    maxWidth: '90vw',
    maxHeight: '90vh',
    overflowY: 'auto',
    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.2)',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 18,
    fontWeight: 700,
    color: '#0f1419',
    margin: 0,
  },
  closeButton: {
    background: 'transparent',
    border: 'none',
    fontSize: 18,
    color: '#536471',
    cursor: 'pointer',
    padding: '4px 8px',
    borderRadius: 4,
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: 20,
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: 6,
  },
  label: {
    fontSize: 14,
    fontWeight: 600,
    color: '#536471',
  },
  input: {
    border: '1px solid #cfd9de',
    borderRadius: 8,
    padding: '10px 12px',
    fontSize: 15,
    fontFamily: 'inherit',
    outline: 'none',
  },
  textarea: {
    border: '1px solid #cfd9de',
    borderRadius: 8,
    padding: '10px 12px',
    fontSize: 15,
    fontFamily: 'inherit',
    resize: 'vertical',
    outline: 'none',
  },
  hint: {
    fontSize: 12,
    color: '#536471',
  },
  charCount: {
    fontSize: 12,
    color: '#536471',
    textAlign: 'right',
  },
  error: {
    fontSize: 13,
    color: '#f4212e',
    margin: 0,
  },
  actions: {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: 12,
    marginTop: 4,
  },
  avatarSection: {
    display: 'flex',
    alignItems: 'center',
    gap: 20,
    padding: '8px 0',
    borderBottom: '1px solid #e1e8ed',
    paddingBottom: 20,
  },
  avatarButton: {
    padding: '8px 16px',
    background: 'transparent',
    border: '1px solid #cfd9de',
    borderRadius: 9999,
    fontSize: 13,
    fontWeight: 600,
    cursor: 'pointer',
    color: '#0f1419',
  },
  cancelButton: {
    padding: '10px 20px',
    background: 'transparent',
    border: '1px solid #cfd9de',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 600,
    cursor: 'pointer',
  },
  saveButton: {
    padding: '10px 24px',
    background: '#1d9bf0',
    color: '#ffffff',
    border: 'none',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
  },
}
