/**
 * 認証まわりの Playwright フィクスチャ。
 *
 * 提供するもの:
 *   - api      : バックエンド直アクセス用の APIRequestContext（テストデータ準備に使う）
 *   - account  : API で作成した新規ユーザー（このテスト専用）
 *   - loginAs  : 任意のアカウントで「画面から」ログインするヘルパー
 *   - appPage  : account で UI ログイン済みのページ（ログイン後フローのテストはこれを使う）
 *
 * ログインを“画面操作”で行う理由:
 *   アクセストークンはフロントのメモリにのみ保持される設計（XSS 対策）。
 *   API で取得したトークンをブラウザのメモリへ注入するのは困難なので、
 *   実際のログインフォームを通して正規にブラウザのセッション（＋リフレッシュ Cookie）を確立する。
 */

import {
  test as base,
  expect,
  request as playwrightRequest,
  type APIRequestContext,
  type Page,
} from '@playwright/test'
import { registerAccount, type Account } from './api'

// バックエンドの URL。データ準備用の API はここへ直接アクセスする。
const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080'

// ログインフォームに渡せる最小限の資格情報。
type Credentials = { email: string; password: string }

type Fixtures = {
  api: APIRequestContext
  account: Account
  loginAs: (page: Page, credentials: Credentials) => Promise<void>
  appPage: Page
}

export const test = base.extend<Fixtures>({
  // バックエンド直アクセス用の API コンテキスト。テスト終了時に破棄する。
  api: async ({}, use) => {
    const ctx = await playwrightRequest.newContext({ baseURL: API_URL })
    await use(ctx)
    await ctx.dispose()
  },

  // このテスト専用の新規ユーザーを API で作成する。
  account: async ({ api }, use) => {
    const acc = await registerAccount(api)
    await use(acc)
  },

  // 画面からログインする共通操作。ログイン後にホームへ到達するまで待つ。
  loginAs: async ({}, use) => {
    await use(async (page: Page, credentials: Credentials) => {
      await page.goto('/login')
      await page.locator('#email').fill(credentials.email)
      await page.locator('#password').fill(credentials.password)
      await page.getByRole('button', { name: 'ログイン' }).click()
      // ログイン成功で /home に遷移し、ホーム見出しが表示されることを確認する。
      await page.waitForURL('**/home')
      await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
    })
  },

  // account で UI ログイン済みのページ。ログイン後フローのテストはこれを起点にする。
  appPage: async ({ page, account, loginAs }, use) => {
    await loginAs(page, account)
    await use(page)
  },
})

export { expect }
