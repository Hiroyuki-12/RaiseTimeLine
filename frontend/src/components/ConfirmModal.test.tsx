/**
 * ConfirmModal の単体テスト。
 *
 * 対象: ConfirmModal
 * 技法: 同値分割 (isOpen=true/false) + 状態遷移 (オーバーレイクリック / 確定 / キャンセル)
 */
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import ConfirmModal from './ConfirmModal'

describe('ConfirmModal', () => {
  it('isOpen=false のときは何もレンダリングしない', () => {
    const { container } = render(
      <ConfirmModal isOpen={false} message="削除しますか?" onConfirm={vi.fn()} onCancel={vi.fn()} />
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('isOpen=true のとき message と確定 / キャンセルボタンが表示される', () => {
    render(
      <ConfirmModal
        isOpen={true}
        message="このコメントを削除しますか？"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    )

    expect(screen.getByText('このコメントを削除しますか？')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '削除する' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'キャンセル' })).toBeInTheDocument()
  })

  it('confirmLabel を渡すとボタン名が変わる', () => {
    render(
      <ConfirmModal
        isOpen={true}
        message="m"
        confirmLabel="アンフォロー"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    )
    expect(screen.getByRole('button', { name: 'アンフォロー' })).toBeInTheDocument()
  })

  it('確定ボタンクリックで onConfirm が呼ばれる', async () => {
    const onConfirm = vi.fn()
    render(
      <ConfirmModal isOpen={true} message="m" onConfirm={onConfirm} onCancel={vi.fn()} />
    )

    await userEvent.click(screen.getByRole('button', { name: '削除する' }))

    expect(onConfirm).toHaveBeenCalledOnce()
  })

  it('キャンセルボタンクリックで onCancel が呼ばれる', async () => {
    const onCancel = vi.fn()
    render(
      <ConfirmModal isOpen={true} message="m" onConfirm={vi.fn()} onCancel={onCancel} />
    )

    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))

    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('背景の「閉じる」ボタンクリックで onCancel が呼ばれる (誤操作防止)', async () => {
    // 背景クリックでの「閉じる」は、キーボード操作可能な全画面 button（aria-label="閉じる"）が担う。
    // div + onClick だとキーボードで閉じられずアクセシビリティ違反になるため button にしている。
    const onCancel = vi.fn()
    render(
      <ConfirmModal isOpen={true} message="m" onConfirm={vi.fn()} onCancel={onCancel} />
    )

    await userEvent.click(screen.getByRole('button', { name: '閉じる' }))

    expect(onCancel).toHaveBeenCalled()
  })
})
