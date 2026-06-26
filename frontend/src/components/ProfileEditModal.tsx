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

  // 画像ファイルが選択されたときのプレビュー更新処理
  const handleAvatarChange = (file: File) => {
    if (avatarPreview) URL.revokeObjectURL(avatarPreview)
    setAvatarFile(file)
    setAvatarPreview(URL.createObjectURL(file))
  }

  /**
   * 画像アップロードボタンを押したときの処理。
   * input 要素を動的に生成して click() を呼び、ファイルピッカー（Finder）を開く。
   */
  const openAvatarFilePicker = () => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/jpeg,image/png'
    input.onchange = () => {
      const file = input.files?.[0]
      if (file) handleAvatarChange(file)
    }
    input.click()
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
    // オーバーレイ: モーダルの土台。aria-label でダイアログの名前を読み上げ可能にする
    <div style={styles.overlay} role="dialog" aria-modal="true" aria-label="プロフィールを編集">
      {/* 背景の閉じる領域: div + onClick はキーボード操作できずアクセシビリティ違反になるため、
          全画面を覆う透明 button にする。マウスでも Enter/Space でも閉じられる */}
      <button type="button" aria-label="閉じる" style={styles.backdrop} onClick={onClose} />
      {/* モーダル本体: 背景ボタンより前面に出すため position/zIndex を付与（styles.modal 側で指定） */}
      <div style={styles.modal}>
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
              {/* 画像アップロードボタン: クリックで動的に input[type=file] を生成して Finder を開く */}
              <button
                type="button"
                style={{
                  ...styles.avatarButton,
                  cursor: isSubmitting ? 'not-allowed' : 'pointer',
                  opacity: isSubmitting ? 0.5 : 1,
                }}
                onClick={openAvatarFilePicker}
                disabled={isSubmitting}
              >
                {avatarFile ? '画像を変更' : '画像をアップロード'}
              </button>
              <span style={styles.hint}>JPEG または PNG、2MB 以下</span>
            </div>
          </div>

          {/* ユーザー名 */}
          <div style={styles.field}>
            {/* htmlFor と input の id を一致させ、ラベルとコントロールを紐付ける。
                これでスクリーンリーダーが入力欄の名前を読み上げ、ラベルクリックで入力にフォーカスできる */}
            <label htmlFor="profile-username" style={styles.label}>
              ユーザー名
            </label>
            <input
              id="profile-username"
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
            <label htmlFor="profile-displayName" style={styles.label}>
              表示名
            </label>
            <input
              id="profile-displayName"
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
            <label htmlFor="profile-bio" style={styles.label}>
              自己紹介
            </label>
            <textarea
              id="profile-bio"
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
  // 背景の閉じるボタン: 全画面を覆う透明ボタン。見た目は出さず「閉じる」操作だけを担う
  backdrop: {
    position: 'absolute',
    inset: 0,
    background: 'transparent',
    border: 'none',
    padding: 0,
    margin: 0,
    cursor: 'default',
  },
  modal: {
    // 背景ボタン（position: absolute）より前面に出すため、自身も positioned + zIndex にする
    position: 'relative',
    zIndex: 1,
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
    color: '#d61f2b',
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
    background: '#1170b8',
    color: '#ffffff',
    border: 'none',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
  },
}
