/**
 * ポスト API クライアント。
 * auth.ts で作成した apiClient を再利用する（Authorization ヘッダー自動付与・401 リトライ済み）。
 */

import { apiClient } from './auth'

// バックエンドの PostResponse に対応する型定義
export interface Post {
  id: number
  content: string
  authorId: number
  authorUsername: string
  authorDisplayName: string
  createdAt: string // ISO 8601 形式の文字列（例: "2024-01-15T12:34:56"）
  updatedAt: string
  likeCount: number // いいね数（SQL 集計サブクエリで取得。投稿件数に関係なく1クエリで済む）
  commentCount: number // コメント数（同上）
  liked: boolean // 現在ログイン中のユーザーがこの投稿をいいね済みかどうか
}

/**
 * タイムライン取得 API（ページネーション付き）。
 * GET /api/posts?page=&size=
 * ポストを新しい順で取得する。
 *
 * @param page 0 始まりのページ番号
 * @param size 1 ページあたりの件数
 */
export const fetchTimeline = async (page: number, size: number): Promise<Post[]> => {
  const res = await apiClient.get<Post[]>('/posts', { params: { page, size } })
  return res.data
}

/**
 * 新着件数確認 API（ポーリング用）。
 * GET /api/posts/new-count?since=<ISOString>
 * 指定日時より後に作成された投稿件数を返す。
 * フロントエンドが 30 秒ごとに呼び出して新着の有無を確認する。
 *
 * @param since この日時（ISO 文字列）より後の新着件数を返す
 */
export const fetchNewCount = async (since: string): Promise<{ count: number }> => {
  const res = await apiClient.get<{ count: number }>('/posts/new-count', { params: { since } })
  return res.data
}

/**
 * ポスト作成 API。
 * POST /api/posts
 * 認証済みユーザーが新しい投稿を作成する。
 *
 * @param content 投稿本文（1〜280 文字）
 */
export const createPost = async (content: string): Promise<Post> => {
  const res = await apiClient.post<Post>('/posts', { content })
  return res.data
}

/**
 * ポスト編集 API。
 * PUT /api/posts/{id}
 * 投稿者本人のみ実行できる。
 *
 * @param id      編集するポストの ID
 * @param content 更新後の投稿本文（1〜280 文字）
 */
export const updatePost = async (id: number, content: string): Promise<Post> => {
  const res = await apiClient.put<Post>(`/posts/${id}`, { content })
  return res.data
}

/**
 * ポスト削除 API。
 * DELETE /api/posts/{id}
 * 投稿者本人のみ実行できる。
 *
 * @param id 削除するポストの ID
 */
export const deletePost = async (id: number): Promise<void> => {
  await apiClient.delete(`/posts/${id}`)
}

/**
 * 投稿1件取得 API（投稿詳細画面用）。
 * GET /api/posts/{id}
 * いいね数・コメント数・liked フラグを含む PostResponse を返す。
 *
 * @param id 取得する投稿の ID
 */
export const fetchPost = async (id: number): Promise<Post> => {
  const res = await apiClient.get<Post>(`/posts/${id}`)
  return res.data
}

/**
 * いいね追加 API。
 * POST /api/likes
 * 既にいいね済みの場合はバックエンドが 409 を返す。
 *
 * @param postId いいね対象の投稿 ID
 */
export const addLike = async (postId: number): Promise<void> => {
  await apiClient.post('/likes', { postId })
}

/**
 * いいね削除 API。
 * DELETE /api/likes/{postId}
 * いいねが存在しない場合はバックエンドが 404 を返す。
 *
 * @param postId いいねを取り消す投稿 ID
 */
export const removeLike = async (postId: number): Promise<void> => {
  await apiClient.delete(`/likes/${postId}`)
}
