/**
 * FollowButton の単体テスト。
 *
 * 対象: FollowButton
 * 技法: 状態遷移 (未フォロー → フォロー中 → アンフォロー確認 → 未フォロー)
 *      + デシジョンテーブル (initialIsFollowing × ホバー)
 */
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import FollowButton from './FollowButton'
import { server } from '../test/server'

describe('FollowButton', () => {
  describe('未フォロー状態 (initialIsFollowing=false)', () => {
    it('「フォローする」ボタンが表示される', () => {
      render(
        <FollowButton
          userId={2}
          username="bob"
          initialIsFollowing={false}
          onFollowChanged={vi.fn()}
        />
      )
      expect(screen.getByRole('button', { name: 'フォローする' })).toBeInTheDocument()
    })

    it('クリックするとフォロー API が呼ばれ「フォロー中」表示に変わる', async () => {
      let calledId: string | null = null
      server.use(
        http.post('/api/users/:userId/follow', ({ params }) => {
          calledId = String(params.userId)
          return new HttpResponse(null, { status: 204 })
        })
      )
      const onFollowChanged = vi.fn()
      render(
        <FollowButton
          userId={2}
          username="bob"
          initialIsFollowing={false}
          onFollowChanged={onFollowChanged}
        />
      )

      await userEvent.click(screen.getByRole('button', { name: 'フォローする' }))

      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
      )
      expect(calledId).toBe('2')
      expect(onFollowChanged).toHaveBeenCalledWith(true)
    })
  })

  describe('フォロー中状態 (initialIsFollowing=true)', () => {
    it('初期表示は「フォロー中」', () => {
      render(
        <FollowButton
          userId={2}
          username="bob"
          initialIsFollowing={true}
          onFollowChanged={vi.fn()}
        />
      )
      expect(screen.getByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
    })

    it('クリック時は確認モーダルが表示される (即時アンフォローしない)', async () => {
      render(
        <FollowButton
          userId={2}
          username="bob"
          initialIsFollowing={true}
          onFollowChanged={vi.fn()}
        />
      )

      await userEvent.click(screen.getByRole('button', { name: 'フォロー中' }))

      expect(screen.getByText('@bob のフォローを解除しますか？')).toBeInTheDocument()
    })

    it('確認モーダルでフォロー解除を確定するとアンフォロー API が呼ばれる', async () => {
      let calledId: string | null = null
      server.use(
        http.delete('/api/users/:userId/follow', ({ params }) => {
          calledId = String(params.userId)
          return new HttpResponse(null, { status: 204 })
        })
      )
      const onFollowChanged = vi.fn()
      render(
        <FollowButton
          userId={2}
          username="bob"
          initialIsFollowing={true}
          onFollowChanged={onFollowChanged}
        />
      )
      // ホバー状態でボタン文言が「フォロー解除」になり、モーダル内の確定ボタンも
       // 同名なので、モーダル (role=dialog) スコープ内のボタンを明示的に選ぶ
      await userEvent.click(screen.getByRole('button', { name: 'フォロー中' }))
      const dialog = screen.getByRole('dialog')

      await userEvent.click(within(dialog).getByRole('button', { name: 'フォロー解除' }))

      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'フォローする' })).toBeInTheDocument()
      )
      expect(calledId).toBe('2')
      expect(onFollowChanged).toHaveBeenCalledWith(false)
    })

    it('確認モーダルでキャンセルするとフォロー状態のまま', async () => {
      const onFollowChanged = vi.fn()
      render(
        <FollowButton
          userId={2}
          username="bob"
          initialIsFollowing={true}
          onFollowChanged={onFollowChanged}
        />
      )
      await userEvent.click(screen.getByRole('button', { name: 'フォロー中' }))

      await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))

      // モーダルが閉じた後、ホバー状態のままなので「フォロー解除」表記になっている
      // (実装の挙動)。モーダルが閉じていることと onFollowChanged が呼ばれていないことを検証する。
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(onFollowChanged).not.toHaveBeenCalled()
    })
  })
})
