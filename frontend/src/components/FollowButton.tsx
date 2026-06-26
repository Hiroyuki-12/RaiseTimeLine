/**
 * フォロー/アンフォローボタンコンポーネント。
 * - フォロー中の場合: 「フォロー中」と表示し、ホバー時に「フォロー解除」に変わる
 * - フォローしていない場合: 「フォローする」ボタンを表示
 * - アンフォロー時は確認モーダルを表示する（誤操作防止）
 */

import { useState } from 'react'
import { followUser, unfollowUser } from '../api/user'
import ConfirmModal from './ConfirmModal'

interface Props {
  /** フォロー対象ユーザーの ID */
  userId: number
  /** フォロー対象ユーザーの @handle（確認モーダルのメッセージに使う） */
  username: string
  /** 初期のフォロー状態 */
  initialIsFollowing: boolean
  /** フォロー状態が変わったときに親コンポーネントへ通知するコールバック */
  onFollowChanged: (isFollowing: boolean) => void
}

export default function FollowButton({
  userId,
  username,
  initialIsFollowing,
  onFollowChanged,
}: Props) {
  const [isFollowing, setIsFollowing] = useState(initialIsFollowing)
  const [isHovered, setIsHovered] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  // アンフォロー確認モーダルの表示状態
  const [isConfirmOpen, setIsConfirmOpen] = useState(false)

  /** フォローボタンをクリックしたとき */
  const handleFollow = async () => {
    if (isLoading) return
    setIsLoading(true)
    try {
      await followUser(userId)
      setIsFollowing(true)
      onFollowChanged(true)
    } catch {
      // エラー時はフォロー状態を変えない（サイレントに失敗する）
    } finally {
      setIsLoading(false)
    }
  }

  /** フォロー解除を確認モーダルで確定したとき */
  const handleUnfollowConfirmed = async () => {
    setIsConfirmOpen(false)
    setIsLoading(true)
    try {
      await unfollowUser(userId)
      setIsFollowing(false)
      onFollowChanged(false)
    } catch {
      // エラー時はフォロー状態を変えない
    } finally {
      setIsLoading(false)
    }
  }

  if (isFollowing) {
    return (
      <>
        <button
          style={{
            ...styles.followingButton,
            // ホバー時は赤色に変えて「フォロー解除」操作を連想させる
            background: isHovered ? '#fee2e2' : '#ffffff',
            color: isHovered ? '#d61f2b' : '#0f1419',
            borderColor: isHovered ? '#d61f2b' : '#cfd9de',
          }}
          onMouseEnter={() => setIsHovered(true)}
          onMouseLeave={() => setIsHovered(false)}
          onClick={() => setIsConfirmOpen(true)}
          disabled={isLoading}
        >
          {isHovered ? 'フォロー解除' : 'フォロー中'}
        </button>

        <ConfirmModal
          isOpen={isConfirmOpen}
          message={`@${username} のフォローを解除しますか？`}
          confirmLabel="フォロー解除"
          onConfirm={handleUnfollowConfirmed}
          onCancel={() => setIsConfirmOpen(false)}
        />
      </>
    )
  }

  return (
    <button style={styles.followButton} onClick={handleFollow} disabled={isLoading}>
      {isLoading ? '...' : 'フォローする'}
    </button>
  )
}

const styles: Record<string, React.CSSProperties> = {
  followButton: {
    padding: '8px 20px',
    background: '#0f1419',
    color: '#ffffff',
    border: 'none',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
    cursor: 'pointer',
  },
  followingButton: {
    padding: '8px 20px',
    border: '1px solid',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
    cursor: 'pointer',
    transition: 'background 0.15s, color 0.15s, border-color 0.15s',
    minWidth: 110,
  },
}
