/**
 * PostForm の単体テスト。
 *
 * 対象: PostForm
 * 技法: 境界値 (0 / 1 / 280 / 281 字) + 状態遷移 (送信前 → 送信中 → 成功 / 失敗)
 */
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import PostForm from './PostForm'
import { server } from '../test/server'
import type { Post } from '../api/post'

const samplePost: Post = {
  id: 1,
  content: 'hello',
  authorId: 1,
  authorUsername: 'alice',
  authorDisplayName: 'アリス',
  authorAvatarUrl: null,
  imageUrl: null,
  createdAt: '2026-05-09T00:00:00',
  updatedAt: '2026-05-09T00:00:00',
  likeCount: 0,
  commentCount: 0,
  liked: false,
}

describe('PostForm', () => {
  it('初期状態では投稿ボタンが disabled (空文字)', () => {
    render(<PostForm onPostCreated={vi.fn()} />)

    expect(screen.getByRole('button', { name: '投稿' })).toBeDisabled()
  })

  it('1 文字入力すると投稿ボタンが有効になる (境界値: 最小)', async () => {
    render(<PostForm onPostCreated={vi.fn()} />)

    await userEvent.type(screen.getByRole('textbox'), 'a')

    expect(screen.getByRole('button', { name: '投稿' })).toBeEnabled()
  })

  it('280 文字入力可能で残り 0 (境界値: 最大)', async () => {
    render(<PostForm onPostCreated={vi.fn()} />)
    const textarea = screen.getByRole('textbox')

    await userEvent.type(textarea, 'a'.repeat(280))

    expect(screen.getByText('0')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '投稿' })).toBeEnabled()
  })

  it('281 文字以上は投稿ボタンが disabled (境界値: 最大+1)', async () => {
    render(<PostForm onPostCreated={vi.fn()} />)
    const textarea = screen.getByRole('textbox')

    await userEvent.type(textarea, 'a'.repeat(281))

    // 残り -1 の表示
    expect(screen.getByText('-1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '投稿' })).toBeDisabled()
  })

  it('投稿成功で onPostCreated が呼ばれフォームがクリアされる', async () => {
    server.use(http.post('/api/posts', () => HttpResponse.json(samplePost, { status: 201 })))
    const onPostCreated = vi.fn()
    render(<PostForm onPostCreated={onPostCreated} />)

    const textarea = screen.getByRole('textbox')
    await userEvent.type(textarea, 'hello')
    await userEvent.click(screen.getByRole('button', { name: '投稿' }))

    await waitFor(() => expect(onPostCreated).toHaveBeenCalledWith(samplePost))
    expect(textarea).toHaveValue('')
  })

  it('投稿失敗時はサーバーのメッセージを表示する', async () => {
    server.use(
      http.post('/api/posts', () =>
        HttpResponse.json({ message: '投稿内容は280文字以内にしてください' }, { status: 400 })
      )
    )
    render(<PostForm onPostCreated={vi.fn()} />)

    await userEvent.type(screen.getByRole('textbox'), 'hello')
    await userEvent.click(screen.getByRole('button', { name: '投稿' }))

    await waitFor(() =>
      expect(screen.getByText('投稿内容は280文字以内にしてください')).toBeInTheDocument()
    )
  })
})
