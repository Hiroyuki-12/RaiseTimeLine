/**
 * コメント（返信）のシナリオ E2E（C1〜C4）。
 *
 * 観点（技法）:
 *   - 状態遷移（作成→一覧追加、編集→反映、削除→消滅）
 *   - 境界値（コメント本文 140 / 141 文字での送信可否）
 *
 * UI 上の用語は「返信」だが、機能としては投稿へのコメント。
 * コメントは data-comment-id で一意に特定する。
 */

import { test, expect } from '../fixtures/auth'
import { createPost, createComment } from '../fixtures/api'
import type { Page } from '@playwright/test'

/** 指定 ID のコメント行を返すヘルパー。 */
function commentItem(page: Page, id: number) {
  return page.locator(`[data-testid="comment-item"][data-comment-id="${id}"]`)
}

test.describe('コメント（返信）', () => {
  // C1: 投稿詳細でコメントを投稿すると一覧に追加される
  test('C1: コメントを作成できる', async ({ appPage, api, account }) => {
    const postId = await createPost(api, account.accessToken, `コメント対象 ${Date.now()}`)
    const content = `はじめての返信 ${Date.now()}`

    await appPage.goto(`/posts/${postId}`)
    await appPage.getByPlaceholder(/返信を入力/).fill(content)
    await appPage.getByRole('button', { name: '返信' }).click()

    await expect(appPage.getByText(content)).toBeVisible()
  })

  // C2: 自分のコメントを編集すると本文が更新される
  test('C2: 自分のコメントを編集できる', async ({ appPage, api, account }) => {
    const postId = await createPost(api, account.accessToken, `コメント編集対象 ${Date.now()}`)
    const original = `編集前の返信 ${Date.now()}`
    const edited = `編集後の返信 ${Date.now()}`
    const comment = await createComment(api, account.accessToken, postId, original)

    await appPage.goto(`/posts/${postId}`)
    const item = commentItem(appPage, comment.id)
    await item.getByTitle('編集').click()
    await item.getByRole('textbox').fill(edited)
    await item.getByRole('button', { name: '保存' }).click()

    await expect(item).toContainText(edited)
    await expect(item).not.toContainText(original)
  })

  // C3: 自分のコメントを削除すると一覧から消える
  test('C3: 自分のコメントを削除できる', async ({ appPage, api, account }) => {
    const postId = await createPost(api, account.accessToken, `コメント削除対象 ${Date.now()}`)
    const comment = await createComment(api, account.accessToken, postId, `削除する返信 ${Date.now()}`)

    await appPage.goto(`/posts/${postId}`)
    const item = commentItem(appPage, comment.id)
    await expect(item).toHaveCount(1)
    await item.getByTitle('削除').click()
    await appPage.getByTestId('confirm-accept').click()

    await expect(item).toHaveCount(0)
  })

  // C4: コメント本文の文字数境界（140 は送信可 / 141 は送信不可）
  test('C4: コメント本文 140/141 文字の境界で送信可否が切り替わる', async ({
    appPage,
    api,
    account,
  }) => {
    const postId = await createPost(api, account.accessToken, `コメント境界 ${Date.now()}`)
    await appPage.goto(`/posts/${postId}`)

    const textarea = appPage.getByPlaceholder(/返信を入力/)
    const submit = appPage.getByRole('button', { name: '返信' })

    // 140 文字ちょうど: 送信できる
    await textarea.fill('あ'.repeat(140))
    await expect(submit).toBeEnabled()

    // 141 文字: 上限超過で送信できない
    await textarea.fill('あ'.repeat(141))
    await expect(submit).toBeDisabled()
  })
})
