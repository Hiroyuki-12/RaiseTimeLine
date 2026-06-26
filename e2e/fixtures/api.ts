/**
 * バックエンド REST API を直接叩いてテストデータを準備するためのヘルパー群。
 *
 * 方針:
 *   - 検証対象のユーザーフローは画面操作（UI）で行うが、
 *     「フォロー相手」「事前に並んでいる投稿」などの“お膳立て”は API で素早く作る。
 *   - これにより UI 操作を検証本筋だけに絞れ、テストが速く・安定する。
 *   - すべて新規に作るデータなので、既存 DB（perf seed 等）を破壊しない。
 *
 * 接続先はバックエンド（既定 http://localhost:8080）に直接アクセスする。
 * （ブラウザは :5173 経由だが、データ準備はプロキシを介さず直叩きする方がシンプル）
 */

import { type APIRequestContext, expect } from '@playwright/test'
import { uniqueAccount } from './unique'

/** API で作成したアカウント。UI ログインや所有権の判定に使う。 */
export interface Account {
  userId: number
  username: string
  displayName: string
  email: string
  password: string
  /** JWT アクセストークン。以降の API 呼び出しの Authorization ヘッダーに使う。 */
  accessToken: string
}

/** Authorization ヘッダーを組み立てる小さなヘルパー。 */
function authHeaders(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` }
}

/**
 * 新規ユーザーを登録する（POST /api/auth/register）。
 * 一意な username/email を自動生成し、上書きしたい項目だけ overrides で渡せる。
 */
export async function registerAccount(
  request: APIRequestContext,
  overrides: Partial<{ password: string }> = {},
): Promise<Account> {
  const base = uniqueAccount()
  const password = overrides.password ?? base.password
  const res = await request.post('/api/auth/register', {
    data: {
      displayName: base.displayName,
      username: base.username,
      email: base.email,
      password,
      // バックエンドはパスワード一致チェックをするため確認用も同値で送る。
      passwordConfirm: password,
    },
  })
  // 登録に失敗するとテストの前提が崩れるため、ここで明確に落とす。
  expect(res.ok(), `register failed: ${res.status()} ${await res.text()}`).toBeTruthy()
  const body = await res.json()
  return {
    userId: body.userId,
    username: body.username,
    displayName: body.displayName,
    email: base.email,
    password,
    accessToken: body.accessToken,
  }
}

/**
 * 投稿を作成する（POST /api/posts, multipart/form-data）。
 * 作成した投稿の ID を返す。
 */
export async function createPost(
  request: APIRequestContext,
  token: string,
  content: string,
): Promise<number> {
  const res = await request.post('/api/posts', {
    headers: authHeaders(token),
    // バックエンドは multipart で受けるため multipart で送る（画像は付けない）。
    multipart: { content },
  })
  expect(res.ok(), `createPost failed: ${res.status()} ${await res.text()}`).toBeTruthy()
  return (await res.json()).id as number
}

/**
 * 投稿を指定件数だけまとめて作成する（無限スクロール検証などの“お膳立て”用）。
 * 作成順に ID 配列を返す。
 */
export async function createPosts(
  request: APIRequestContext,
  token: string,
  count: number,
  contentPrefix: string,
): Promise<number[]> {
  const ids: number[] = []
  for (let i = 0; i < count; i += 1) {
    // 連番を付けて本文を一意にし、画面上で識別しやすくする。
    ids.push(await createPost(request, token, `${contentPrefix} ${i + 1}`))
  }
  return ids
}

/** コメントを作成する（POST /api/posts/{postId}/comments）。作成したコメントを返す。 */
export async function createComment(
  request: APIRequestContext,
  token: string,
  postId: number,
  content: string,
): Promise<{ id: number; content: string }> {
  const res = await request.post(`/api/posts/${postId}/comments`, {
    headers: authHeaders(token),
    data: { content },
  })
  expect(res.ok(), `createComment failed: ${res.status()} ${await res.text()}`).toBeTruthy()
  return await res.json()
}

/** 指定ユーザーをフォローする（POST /api/users/{userId}/follow）。 */
export async function followUser(
  request: APIRequestContext,
  token: string,
  targetUserId: number,
): Promise<void> {
  const res = await request.post(`/api/users/${targetUserId}/follow`, {
    headers: authHeaders(token),
  })
  expect(res.ok(), `followUser failed: ${res.status()} ${await res.text()}`).toBeTruthy()
}
