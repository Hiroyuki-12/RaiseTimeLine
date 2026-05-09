/**
 * comment.ts の単体テスト。
 * 対象: fetchComments / createComment / updateComment / deleteComment
 */
import { http, HttpResponse } from 'msw'
import { server } from '../test/server'
import {
  createComment,
  deleteComment,
  fetchComments,
  updateComment,
  type Comment,
} from './comment'

const sample: Comment = {
  id: 10,
  postId: 1,
  authorId: 1,
  authorUsername: 'alice',
  authorDisplayName: 'アリス',
  content: 'hi',
  createdAt: '2026-05-09T00:00:00',
}

describe('comment API', () => {
  it('fetchComments: GET /posts/{postId}/comments', async () => {
    server.use(http.get('/api/posts/:postId/comments', () => HttpResponse.json([sample])))

    const result = await fetchComments(1)

    expect(result).toEqual([sample])
  })

  it('createComment: POST /posts/{postId}/comments に content を送る', async () => {
    let capturedBody: unknown = null
    server.use(
      http.post('/api/posts/:postId/comments', async ({ request }) => {
        capturedBody = await request.json()
        return HttpResponse.json(sample, { status: 201 })
      })
    )

    await createComment(1, 'hi')

    expect(capturedBody).toEqual({ content: 'hi' })
  })

  it('updateComment: PUT /comments/{id} に content を送る', async () => {
    let capturedBody: unknown = null
    server.use(
      http.put('/api/comments/:id', async ({ request }) => {
        capturedBody = await request.json()
        return HttpResponse.json({ ...sample, content: 'updated' })
      })
    )

    const result = await updateComment(10, 'updated')

    expect(capturedBody).toEqual({ content: 'updated' })
    expect(result.content).toBe('updated')
  })

  it('deleteComment: DELETE /comments/{id}', async () => {
    let calledId: string | null = null
    server.use(
      http.delete('/api/comments/:id', ({ params }) => {
        calledId = String(params.id)
        return new HttpResponse(null, { status: 204 })
      })
    )

    await deleteComment(10)

    expect(calledId).toBe('10')
  })
})
