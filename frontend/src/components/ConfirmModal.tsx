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
    // オーバーレイ: モーダルの土台。aria-label を付けてダイアログの名前を読み上げ可能にする
    <div style={styles.overlay} role="dialog" aria-modal="true" aria-label="確認">
      {/* 背景の閉じる領域: div + onClick だとキーボードで操作できずアクセシビリティ違反になるため、
          全画面を覆う透明な button にする。これでマウスクリックでも Enter/Space でも閉じられる */}
      <button type="button" aria-label="閉じる" style={styles.backdrop} onClick={onCancel} />
      {/* モーダル本体: 背景ボタンより前面に出すため position/zIndex を付与（styles.modal 側で指定） */}
      <div style={styles.modal}>
        <p style={styles.message}>{message}</p>
        <div style={styles.actions}>
          <button style={styles.cancelButton} onClick={onCancel}>
            キャンセル
          </button>
          {/* data-testid は E2E テストが確認モーダルの実行ボタンを掴むための目印（ラベルが削除/フォロー解除など可変なため） */}
          <button style={styles.confirmButton} onClick={onConfirm} data-testid="confirm-accept">
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
  // 背景の閉じるボタン: 全画面を覆う透明ボタン。見た目は出さず、クリック/キーボードでの「閉じる」操作だけを担う
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
    background: '#d61f2b',
    border: 'none',
    borderRadius: 9999,
    fontSize: 14,
    fontWeight: 700,
    color: '#ffffff',
    cursor: 'pointer',
  },
}
