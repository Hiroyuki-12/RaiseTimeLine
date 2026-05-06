/**
 * 削除などの破壊的操作を実行する前に表示する確認モーダル。
 * window.confirm() の代わりに使うことで、デザインを統一し
 * ブラウザ依存のダイアログを排除する。
 *
 * 使い方:
 *   <ConfirmModal
 *     isOpen={isConfirmOpen}
 *     message="このコメントを削除しますか？"
 *     confirmLabel="削除する"
 *     onConfirm={handleDelete}
 *     onCancel={() => setIsConfirmOpen(false)}
 *   />
 */

import React from 'react'

interface Props {
  /** モーダルを表示するかどうか */
  isOpen: boolean
  /** 確認メッセージ（例: "このコメントを削除しますか？"） */
  message: string
  /** 実行ボタンのラベル（デフォルト: "削除する"） */
  confirmLabel?: string
  /** 実行ボタンクリック時のコールバック */
  onConfirm: () => void
  /** キャンセルボタンまたはオーバーレイクリック時のコールバック */
  onCancel: () => void
}

export default function ConfirmModal({
  isOpen,
  message,
  confirmLabel = '削除する',
  onConfirm,
  onCancel,
}: Props) {
  if (!isOpen) return null

  return (
    // オーバーレイ: 背景クリックでキャンセル
    <div style={styles.overlay} onClick={onCancel} role="dialog" aria-modal="true">
      {/* モーダル本体: クリックイベントが親（オーバーレイ）に伝播しないようにする */}
      <div style={styles.modal} onClick={(e: React.MouseEvent) => e.stopPropagation()}>
        <p style={styles.message}>{message}</p>
        <div style={styles.actions}>
          <button style={styles.cancelButton} onClick={onCancel}>
            キャンセル
          </button>
          <button style={styles.confirmButton} onClick={onConfirm}>
            {confirmLabel}
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
    width: 320,
    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.2)',
  },
  message: {
    fontSize: 16,
    color: '#0f1419',
    lineHeight: 1.6,
    margin: '0 0 20px',
    textAlign: 'center',
  },
  actions: {
    display: 'flex',
    gap: 12,
    justifyContent: 'center',
  },
  cancelButton: {
    flex: 1,
    padding: '10px 0',
    background: 'transparent',
    border: '1px solid #e1e8ed',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 600,
    color: '#0f1419',
    cursor: 'pointer',
  },
  confirmButton: {
    flex: 1,
    padding: '10px 0',
    // 削除など破壊的操作は赤色で強調する（誤操作を防ぐため）
    background: '#f4212e',
    border: 'none',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
    color: '#ffffff',
    cursor: 'pointer',
  },
}
