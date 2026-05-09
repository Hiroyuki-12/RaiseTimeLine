/**
 * FollowListPage の単体テスト。
 *
 * 対象: FollowListPage
 * 技法: 状態遷移 (タブ切替: following ↔ followers)
 *      + クエリパラメータでの初期タブ決定
 */
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import FollowListPage from './FollowListPage'
import { server } from '../test/server'
import { login } from '../api/auth'

async function setUp(initialEntry: string) {
  await login({ email: 'a@example.com', password: 'Pass1234' })
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/users/:username/follows" element={<FollowListPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('FollowListPage', () => {
  beforeEach(() => {
    server.use(
      http.get('/api/users/:username/following', () =>
        HttpResponse.json([
          { id: 2, username: 'bob', displayName: 'ボブ', avatarUrl: null, isFollowing: true },
        ])
      ),
      http.get('/api/users/:username/followers', () =>
        HttpResponse.json([
          { id: 3, username: 'carol', displayName: 'キャロル', avatarUrl: null, isFollowing: false },
        ])
      )
    )
  })

  it('?tab=following で初期化するとフォロー中ユーザーを表示する', async () => {
    await setUp('/users/alice/follows?tab=following')

    await waitFor(() => expect(screen.getByText('ボブ')).toBeInTheDocument())
  })

  it('?tab=followers で初期化するとフォロワーを表示する', async () => {
    await setUp('/users/alice/follows?tab=followers')

    await waitFor(() => expect(screen.getByText('キャロル')).toBeInTheDocument())
  })

  it('タブを切り替えると別の API が呼ばれて表示が変わる', async () => {
    const user = userEvent.setup()
    await setUp('/users/alice/follows?tab=following')
    await waitFor(() => expect(screen.getByText('ボブ')).toBeInTheDocument())

    // 「フォロワー」タブをクリック
    await user.click(screen.getByRole('button', { name: 'フォロワー' }))

    await waitFor(() => expect(screen.getByText('キャロル')).toBeInTheDocument())
    // 直前のフォロー中の結果は消えている
    expect(screen.queryByText('ボブ')).not.toBeInTheDocument()
  })
})
