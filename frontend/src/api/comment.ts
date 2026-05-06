/**
 * コメント API クライアント。
 * auth.ts で作成した apiClient を再利用する（Authorization ヘッダー自動付与・401 リトライ済み）。
 */

import { apiClient } from './auth'

// バックエンドの CommentResponse に対応する型定義
export interface Comment {
  id: number
  postId: number
  authorId: number
  authorUsername: string
  authorDisplayName: string
  content: string
  createdAt: string // ISO 8601 形式の文字列
}

/**
 * コメント一覧取得 API。
 * GET /api/posts/{postId}/comments
 * 指定した投稿のコメントを古い順（created_at 昇順）で取得する。
 * N+1 防止: バックエンドは comments JOIN users の1クエリで取得する。
 *
 * @param postId コメントを取得する投稿の ID
 */
export const fetchComments = async (postId: number): Promise<Comment[]> => {
  const res = await apiClient.get<Comment[]>(`/posts/${postId}/comments`)
  return res.data
}

/**
 * コメント作成 API。
 * POST /api/posts/{postId}/comments
 * 認証済みユーザーが指定した投稿にコメントを追加する。
 *
 * @param postId コメント対象の投稿 ID
 * @param content コメント本文（1〜140 文字）
 */
export const createComment = async (postId: number, content: string): Promise<Comment> => {
  const res = await apiClient.post<Comment>(`/posts/${postId}/comments`, { content })
  return res.data
}

/**
 * コメント更新 API。
 * PUT /api/comments/{id}
 * コメント投稿者本人のみ実行できる。
 *
 * @param id 更新するコメントの ID
 * @param content 更新後のコメント本文（1〜140 文字）
 */
export const updateComment = async (id: number, content: string): Promise<Comment> => {
  const res = await apiClient.put<Comment>(`/comments/${id}`, { content })
  return res.data
}

/**
 * コメント削除 API。
 * DELETE /api/comments/{id}
 * コメント投稿者本人のみ実行できる。
 *
 * @param id 削除するコメントの ID
 */
export const deleteComment = async (id: number): Promise<void> => {
  await apiClient.delete(`/comments/${id}`)
}
