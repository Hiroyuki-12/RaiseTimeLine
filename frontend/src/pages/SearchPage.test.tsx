/**
 * SearchPage の単体テスト。
 *
 * 対象: SearchPage
 * 技法: 状態遷移 (空 → 1 文字以上 → デバウンス → 検索結果)
 */
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import SearchPage from './SearchPage'
import { server } from '../test/server'
import { login } from '../api/auth'

async function setUp() {
  // セッション復元のフォールバックパスではなく getUserInfo() がヒットする方を通す
  await login({ email: 'a@example.com', password: 'Pass1234' })
  return render(
    <MemoryRouter>
      <SearchPage />
    </MemoryRouter>
  )
}

describe('SearchPage', () => {
  it('入力が空のときは API を呼ばず結果も表示しない', async () => {
    let callCount = 0
    server.use(
      http.get('/api/users/search', () => {
        callCount++
        return HttpResponse.json([])
      })
    )
    await setUp()
    // ページが「ユーザー検索」見出しを描画するまで待つ (セッション復元完了)
    await waitFor(() =>
      expect(screen.getByPlaceholderText('ユーザーを検索...')).toBeInTheDocument()
    )

    // 何も入力していない時点で API が呼ばれていないこと
    expect(callCount).toBe(0)
  })

  it('1 文字以上入力するとデバウンスを経て searchUsers API が呼ばれ結果を表示する', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('/api/users/search', ({ request }) => {
        const q = new URL(request.url).searchParams.get('q')
        if (q === 'bo') {
          return HttpResponse.json([
            { id: 2, username: 'bob', displayName: 'ボブ', avatarUrl: null, isFollowing: false },
          ])
        }
        return HttpResponse.json([])
      })
    )
    await setUp()
    await waitFor(() =>
      expect(screen.getByPlaceholderText('ユーザーを検索...')).toBeInTheDocument()
    )

    await user.type(screen.getByPlaceholderText('ユーザーを検索...'), 'bo')

    // 300ms デバウンス後に検索結果が反映される
    await waitFor(() => expect(screen.getByText('ボブ')).toBeInTheDocument(), {
      timeout: 2000,
    })
    expect(screen.getByText('@bob')).toBeInTheDocument()
  })
})
