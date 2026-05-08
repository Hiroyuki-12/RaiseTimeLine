/**
 * ユーザープロフィール・フォロー機能の API クライアント。
 * auth.ts で作成した apiClient を再利用する（Authorization ヘッダー自動付与・401 リトライ済み）。
 */

import { apiClient } from './auth'
import type { Post } from './post'

/** バックエンドの UserProfileResponse に対応する型定義 */
export interface UserProfile {
  id: number
  username: string
  displayName: string
  avatarUrl: string | null
  bio: string | null
  followingCount: number
  followerCount: number
  /** 現在ログイン中のユーザーがこのユーザーをフォローしているか */
  isFollowing: boolean
  /** このプロフィールが自分自身のものか（編集ボタン/フォローボタンの切り替えに使う） */
  isOwnProfile: boolean
}

/** フォロー中・フォロワー一覧の各行に対応する型定義 */
export interface UserSummary {
  id: number
  username: string
  displayName: string
  avatarUrl: string | null
  isFollowing: boolean
}

/** プロフィール編集リクエストの型定義 */
export interface UpdateProfileRequest {
  username: string
  displayName: string
  bio: string
}

/**
 * プロフィール取得 API。
 * GET /api/users/{username}
 * フォロー数・フォロワー数・isFollowing・isOwnProfile を含む。
 *
 * @param username @handle 形式のユーザー名
 */
export const fetchUserProfile = async (username: string): Promise<UserProfile> => {
  const res = await apiClient.get<UserProfile>(`/users/${username}`)
  return res.data
}

/**
 * プロフィール更新 API。
 * PUT /api/users/me
 * 自分のユーザー名・表示名・自己紹介を更新する。
 *
 * @param data 更新内容
 */
export const updateProfile = async (data: UpdateProfileRequest): Promise<UserProfile> => {
  const res = await apiClient.put<UserProfile>('/users/me', data)
  return res.data
}

/**
 * ユーザーの投稿一覧取得 API。
 * GET /api/users/{username}/posts
 * プロフィールページに表示する投稿を新しい順で返す。
 *
 * @param username @handle 形式のユーザー名
 */
export const fetchUserPosts = async (username: string): Promise<Post[]> => {
  const res = await apiClient.get<Post[]>(`/users/${username}/posts`)
  return res.data
}

/**
 * フォロー API。
 * POST /api/users/{userId}/follow
 * 指定ユーザーをフォローする。
 *
 * @param userId フォローするユーザーの ID
 */
export const followUser = async (userId: number): Promise<void> => {
  await apiClient.post(`/users/${userId}/follow`)
}

/**
 * アンフォロー API。
 * DELETE /api/users/{userId}/follow
 * 指定ユーザーのフォローを解除する。
 *
 * @param userId アンフォローするユーザーの ID
 */
export const unfollowUser = async (userId: number): Promise<void> => {
  await apiClient.delete(`/users/${userId}/follow`)
}

/**
 * フォロー中一覧取得 API。
 * GET /api/users/{username}/following
 * 指定ユーザーがフォローしているユーザー一覧を返す。
 *
 * @param username @handle 形式のユーザー名
 */
export const fetchFollowing = async (username: string): Promise<UserSummary[]> => {
  const res = await apiClient.get<UserSummary[]>(`/users/${username}/following`)
  return res.data
}

/**
 * フォロワー一覧取得 API。
 * GET /api/users/{username}/followers
 * 指定ユーザーをフォローしているユーザー一覧を返す。
 *
 * @param username @handle 形式のユーザー名
 */
export const fetchFollowers = async (username: string): Promise<UserSummary[]> => {
  const res = await apiClient.get<UserSummary[]>(`/users/${username}/followers`)
  return res.data
}

/**
 * ユーザー検索 API。
 * GET /api/users/search?q=キーワード
 * ユーザー名の部分一致で検索し、isFollowing フラグ付きで返す（最大 20 件）。
 *
 * @param q 検索キーワード（1文字以上）
 */
export const searchUsers = async (q: string): Promise<UserSummary[]> => {
  const res = await apiClient.get<UserSummary[]>('/users/search', { params: { q } })
  return res.data
}

/**
 * アバター画像アップロード API。
 * POST /api/users/me/avatar (multipart/form-data)
 * 画像を S3 に保存し、更新後のプロフィールを返す。
 *
 * @param file アップロードする画像ファイル（JPEG または PNG、2MB 以下）
 */
export const uploadAvatar = async (file: File): Promise<UserProfile> => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await apiClient.post<UserProfile>('/users/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data
}
