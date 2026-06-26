/**
 * 投稿・いいねのシナリオ E2E（P1〜P7）。
 *
 * 観点（技法）:
 *   - 状態遷移（作成→表示、編集→反映、削除→消滅）
 *   - 楽観的更新の正常系/異常系（いいね成功 / 失敗時ロールバック）
 *   - 境界値（投稿本文 280 / 281 文字）
 *   - 無限スクロール（ページネーションの追加読み込み）
 *
 * 投稿カードは data-post-id で一意に特定する（本文を編集すると hasText フィルタが外れるため）。
 */

import { test, expect } from '../fixtures/auth'
import { createPost, createPosts } from '../fixtures/api'
import type { Page } from '@playwright/test'

/** 指定 ID の投稿カードを返すヘルパー。 */
function postCard(page: Page, id: number) {
  return page.locator(`[data-testid="post-card"][data-post-id="${id}"]`)
}

test.describe('投稿・いいね', () => {
  // P1: 投稿モーダルから作成するとタイムライン最上部に出現する
  test('P1: 投稿を作成するとタイムライン最上部に表示される', async ({ appPage }) => {
    const content = `E2E 投稿 ${Date.now()}`
    // サイドバーの「投稿する」でモーダルを開く
    await appPage.getByRole('button', { name: '投稿する' }).click()
    await appPage.getByPlaceholder('いまどうしてる？').fill(content)
    await appPage.getByTestId('post-submit').click()

    // 最上部の投稿カードに本文が表示される
    const firstCard = appPage.getByTestId('post-card').first()
    await expect(firstCard).toContainText(content)
  })

  // P2: 自分の投稿を編集すると本文が更新される
  test('P2: 自分の投稿を編集できる', async ({ appPage, api, account }) => {
    const original = `編集前 ${Date.now()}`
    const edited = `編集後 ${Date.now()}`
    const id = await createPost(api, account.accessToken, original)
    await appPage.reload()

    const card = postCard(appPage, id)
    await card.getByTitle('編集').click()
    // 編集モードのテキストエリアを書き換えて保存する
    await card.getByRole('textbox').fill(edited)
    await card.getByRole('button', { name: '保存' }).click()

    await expect(card).toContainText(edited)
    await expect(card).not.toContainText(original)
  })

  // P3: 自分の投稿を削除するとタイムラインから消える
  test('P3: 自分の投稿を削除できる', async ({ appPage, api, account }) => {
    const id = await createPost(api, account.accessToken, `削除対象 ${Date.now()}`)
    await appPage.reload()

    const card = postCard(appPage, id)
    await expect(card).toHaveCount(1)
    await card.getByTitle('削除').click()
    // 確認モーダルで削除を確定する
    await appPage.getByTestId('confirm-accept').click()

    await expect(card).toHaveCount(0)
  })

  // P4: いいねは楽観的更新で即座に反映される
  test('P4: いいねが楽観的更新で即時反映される', async ({ appPage, api, account }) => {
    const id = await createPost(api, account.accessToken, `いいね対象 ${Date.now()}`)
    await appPage.reload()

    const likeButton = postCard(appPage, id).getByTestId('post-like-button')
    // いいね前: 🤍 0
    await expect(likeButton).toContainText('0')
    await likeButton.click()
    // いいね後: ❤️ 1（API 完了を待たず即時に反映される）
    await expect(likeButton).toContainText('1')
    await expect(likeButton).toContainText('❤️')
  })

  // P5: いいね API が失敗したら楽観的更新がロールバックされる
  test('P5: いいね失敗時はロールバックされる', async ({ appPage, api, account }) => {
    const id = await createPost(api, account.accessToken, `いいね失敗 ${Date.now()}`)
    await appPage.reload()

    // いいね追加（POST /api/likes）を 500 で失敗させる
    await appPage.route(
      (url) => url.pathname === '/api/likes',
      (route) => route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
    )

    const likeButton = postCard(appPage, id).getByTestId('post-like-button')
    await likeButton.click()
    // 一瞬 1 になった後、API 失敗で 0 / 🤍 に戻る
    await expect(likeButton).toContainText('0')
    await expect(likeButton).toContainText('🤍')
  })

  // P6: 25 件用意して下までスクロールすると次ページが追加読み込みされる
  test('P6: 無限スクロールで次ページが読み込まれる', async ({ appPage, api, account }) => {
    // 1 ページ 20 件なので、25 件あれば 2 ページ目が存在する
    await createPosts(api, account.accessToken, 25, `スクロール ${Date.now()}`)
    await appPage.reload()

    const cards = appPage.getByTestId('post-card')
    // 初回は 20 件（PAGE_SIZE）読み込まれる
    await expect(cards).toHaveCount(20)
    // フォールバックの「さらに読み込む」ボタンで確実に次ページを読み込む
    await appPage.getByRole('button', { name: 'さらに読み込む' }).click()
    // 追加読み込み後は 20 件より増える
    await expect.poll(async () => await cards.count()).toBeGreaterThan(20)
  })

  // P7: 投稿本文の文字数境界（280 は送信可 / 281 は送信不可）
  test('P7: 投稿本文 280/281 文字の境界で送信可否が切り替わる', async ({ appPage }) => {
    await appPage.getByRole('button', { name: '投稿する' }).click()
    const textarea = appPage.getByPlaceholder('いまどうしてる？')
    const submit = appPage.getByTestId('post-submit')

    // 280 文字ちょうど: 送信できる
    await textarea.fill('あ'.repeat(280))
    await expect(submit).toBeEnabled()

    // 281 文字: 上限超過で送信ボタンが無効になる
    await textarea.fill('あ'.repeat(281))
    await expect(submit).toBeDisabled()
  })
})
