/**
 * PostCard の単体テスト。
 *
 * 対象: PostCard
 * 技法: デシジョンテーブル (投稿者本人 × ログイン中) + 状態遷移 (いいね楽観的更新)
 */
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import PostCard from './PostCard'
import { server } from '../test/server'
import type { Post } from '../api/post'

const basePost: Post = {
  id: 10,
  content: 'こんにちは',
  authorId: 1,
  authorUsername: 'alice',
  authorDisplayName: 'アリス',
  authorAvatarUrl: null,
  imageUrl: null,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  likeCount: 5,
  commentCount: 0,
  liked: false,
}

function renderCard(props: Partial<React.ComponentProps<typeof PostCard>> = {}) {
  return render(
    <MemoryRouter>
      <PostCard
        post={basePost}
        currentUserId={1}
        onDeleted={vi.fn()}
        onUpdated={vi.fn()}
        onLikeToggled={vi.fn()}
        {...props}
      />
    </MemoryRouter>
  )
}

describe('PostCard', () => {
  it('投稿本文と著者名を表示する', () => {
    renderCard()

    expect(screen.getByText('こんにちは')).toBeInTheDocument()
    expect(screen.getByText('アリス')).toBeInTheDocument()
  })

  it('投稿者本人 (currentUserId=authorId) のときは編集・削除アイコンが表示される', () => {
    renderCard({ currentUserId: 1 })

    expect(screen.getByTitle('編集')).toBeInTheDocument()
    expect(screen.getByTitle('削除')).toBeInTheDocument()
  })

  it('他人の投稿のときは編集・削除アイコンが表示されない', () => {
    renderCard({ currentUserId: 999 })

    expect(screen.queryByTitle('編集')).not.toBeInTheDocument()
    expect(screen.queryByTitle('削除')).not.toBeInTheDocument()
  })

  it('いいねボタン押下で楽観的更新され、API 成功時はそのまま', async () => {
    server.use(
      http.post('/api/likes', () => new HttpResponse(null, { status: 204 }))
    )
    const onLikeToggled = vi.fn()
    renderCard({ onLikeToggled })

    // 初期状態は未いいね 5
    expect(screen.getByText('5')).toBeInTheDocument()
    await userEvent.click(screen.getByText('5'))

    // 楽観的に 6 に増えていること
    await waitFor(() => expect(screen.getByText('6')).toBeInTheDocument())
    expect(onLikeToggled).toHaveBeenCalledWith(10, true, 6)
  })

  it('削除アイコン押下で確認モーダルが出て、確定で deletePost が呼ばれる', async () => {
    server.use(http.delete('/api/posts/:id', () => new HttpResponse(null, { status: 204 })))
    const onDeleted = vi.fn()
    renderCard({ onDeleted })

    await userEvent.click(screen.getByTitle('削除'))
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '削除する' }))

    await waitFor(() => expect(onDeleted).toHaveBeenCalledWith(10))
  })
})
