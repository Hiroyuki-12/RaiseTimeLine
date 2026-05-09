/**
 * post.ts の単体テスト。
 *
 * 対象: fetchTimeline / fetchPost / createPost (FormData) / updatePost / deletePost / addLike /
 * removeLike / fetchNewCount
 *
 * 技法: 同値分割 + 境界値 (timeline=all/following) + デシジョンテーブル (409/404 エラー)
 */
import { http, HttpResponse } from 'msw'
import { server } from '../test/server'
import {
  addLike,
  createPost,
  deletePost,
  fetchNewCount,
  fetchPost,
  fetchTimeline,
  removeLike,
  updatePost,
  type Post,
} from './post'

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

describe('post API', () => {
  describe('fetchTimeline', () => {
    it('page / size / timeline をクエリパラメータとして送る', async () => {
      let capturedUrl: URL | null = null
      server.use(
        http.get('/api/posts', ({ request }) => {
          capturedUrl = new URL(request.url)
          return HttpResponse.json([samplePost])
        })
      )

      const result = await fetchTimeline(2, 10, 'following')

      expect(result).toEqual([samplePost])
      expect(capturedUrl!.searchParams.get('page')).toBe('2')
      expect(capturedUrl!.searchParams.get('size')).toBe('10')
      expect(capturedUrl!.searchParams.get('timeline')).toBe('following')
    })

    it('timeline 省略時はデフォルトで all になる', async () => {
      let capturedTimeline: string | null = null
      server.use(
        http.get('/api/posts', ({ request }) => {
          capturedTimeline = new URL(request.url).searchParams.get('timeline')
          return HttpResponse.json([])
        })
      )

      await fetchTimeline(0, 20)

      expect(capturedTimeline).toBe('all')
    })
  })

  describe('createPost', () => {
    it('content のみで送ると multipart に content フィールドだけが入る', async () => {
      let capturedFormData: FormData | null = null
      server.use(
        http.post('/api/posts', async ({ request }) => {
          capturedFormData = await request.formData()
          return HttpResponse.json(samplePost, { status: 201 })
        })
      )

      await createPost('hello')

      expect(capturedFormData!.get('content')).toBe('hello')
      expect(capturedFormData!.get('image')).toBeNull()
    })

    it('image を渡すと multipart に image フィールドが追加される', async () => {
      let capturedFormData: FormData | null = null
      server.use(
        http.post('/api/posts', async ({ request }) => {
          capturedFormData = await request.formData()
          return HttpResponse.json(samplePost, { status: 201 })
        })
      )
      const file = new File(['dummy'], 'a.png', { type: 'image/png' })

      await createPost('hello', file)

      expect(capturedFormData!.get('content')).toBe('hello')
      // MSW は File を別 realm で生成するため instanceof でなく shape で検証する
      const sentImage = capturedFormData!.get('image') as File
      expect(sentImage).toBeTruthy()
      expect(sentImage.size).toBeGreaterThan(0)
    })
  })

  describe('updatePost / deletePost', () => {
    it('updatePost は PUT /posts/{id} に content を送る', async () => {
      let capturedBody: { content: string } | null = null
      server.use(
        http.put('/api/posts/:id', async ({ request, params }) => {
          capturedBody = (await request.json()) as { content: string }
          return HttpResponse.json({ ...samplePost, id: Number(params.id), content: 'updated' })
        })
      )

      const result = await updatePost(7, 'updated')

      expect(result.id).toBe(7)
      expect(capturedBody).toEqual({ content: 'updated' })
    })

    it('deletePost は DELETE /posts/{id} を呼ぶ', async () => {
      let calledId: string | null = null
      server.use(
        http.delete('/api/posts/:id', ({ params }) => {
          calledId = String(params.id)
          return new HttpResponse(null, { status: 204 })
        })
      )

      await deletePost(7)

      expect(calledId).toBe('7')
    })
  })

  describe('fetchPost', () => {
    it('GET /posts/{id} の結果を返す', async () => {
      server.use(http.get('/api/posts/:id', () => HttpResponse.json(samplePost)))

      const result = await fetchPost(1)

      expect(result).toEqual(samplePost)
    })
  })

  describe('addLike / removeLike', () => {
    it('addLike: 正常時は postId をボディに送る', async () => {
      let capturedBody: { postId: number } | null = null
      server.use(
        http.post('/api/likes', async ({ request }) => {
          capturedBody = (await request.json()) as { postId: number }
          return new HttpResponse(null, { status: 204 })
        })
      )

      await addLike(5)

      expect(capturedBody).toEqual({ postId: 5 })
    })

    it('addLike: 409 (既にいいね済み) は例外を伝播する', async () => {
      server.use(
        http.post('/api/likes', () =>
          HttpResponse.json({ message: '既にいいね済みです' }, { status: 409 })
        )
      )

      await expect(addLike(5)).rejects.toThrow()
    })

    it('removeLike: 404 (未いいね) は例外を伝播する', async () => {
      server.use(
        http.delete('/api/likes/:postId', () =>
          HttpResponse.json({ message: 'いいねが見つかりません' }, { status: 404 })
        )
      )

      await expect(removeLike(5)).rejects.toThrow()
    })
  })

  describe('fetchNewCount', () => {
    it('since クエリで件数を取得する', async () => {
      let capturedSince: string | null = null
      server.use(
        http.get('/api/posts/new-count', ({ request }) => {
          capturedSince = new URL(request.url).searchParams.get('since')
          return HttpResponse.json({ count: 3 })
        })
      )

      const result = await fetchNewCount('2026-05-09T00:00:00')

      expect(result).toEqual({ count: 3 })
      expect(capturedSince).toBe('2026-05-09T00:00:00')
    })
  })
})
