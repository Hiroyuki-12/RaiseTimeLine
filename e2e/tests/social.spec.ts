/**
 * フォロー・検索・プロフィール編集のシナリオ E2E（S1〜S5）。
 *
 * 観点（技法）:
 *   - 状態遷移（フォロー→フォロー中→解除）
 *   - 確認モーダルを挟む破壊的操作（フォロー解除）
 *   - デバウンス検索（入力→300ms→結果表示）と結果からの画面遷移
 *   - プロフィール編集による username 変更と URL 追従
 */

import { test, expect } from '../fixtures/auth'
import { registerAccount, followUser } from '../fixtures/api'
import { uniqueId } from '../fixtures/unique'

test.describe('フォロー・検索・プロフィール', () => {
  // S1: 他人のプロフィールでフォローすると「フォロー中」になる
  test('S1: 他ユーザーをフォローできる', async ({ appPage, api }) => {
    const target = await registerAccount(api)
    await appPage.goto(`/users/${target.username}`)

    await appPage.getByRole('button', { name: 'フォローする' }).click()
    // フォロー後はボタンがフォロー済み状態に変わる。
    // クリック直後はマウスがボタン上に残りホバー表示（フォロー解除）になるため、両表記を許容する。
    await expect(appPage.getByRole('button', { name: /フォロー中|フォロー解除/ })).toBeVisible()
    // 「フォローする」は消えている（=フォロー済みに遷移した）
    await expect(appPage.getByRole('button', { name: 'フォローする' })).toHaveCount(0)
  })

  // S2: フォロー中のユーザーを確認モーダル経由で解除できる
  test('S2: フォローを解除できる', async ({ appPage, api, account }) => {
    const target = await registerAccount(api)
    // 事前に API でフォロー状態にしておく
    await followUser(api, account.accessToken, target.userId)
    await appPage.goto(`/users/${target.username}`)

    // 「フォロー中 / フォロー解除」ボタン（ホバーで表示が変わるため正規表現で掴む）
    await appPage.getByRole('button', { name: /フォロー中|フォロー解除/ }).click()
    // 確認モーダルで解除を確定する
    await appPage.getByTestId('confirm-accept').click()

    await expect(appPage.getByRole('button', { name: 'フォローする' })).toBeVisible()
  })

  // S3: 検索ボックスに入力するとデバウンス後に結果が表示される
  test('S3: ユーザーを検索できる', async ({ appPage, api }) => {
    const target = await registerAccount(api)
    await appPage.goto('/search')

    await appPage.getByPlaceholder('ユーザーを検索...').fill(target.username)
    // 300ms デバウンス後に検索結果として対象ユーザーの @handle が出る
    await expect(appPage.getByText(`@${target.username}`)).toBeVisible()
  })

  // S4: 検索結果をクリックするとプロフィールページへ遷移する
  test('S4: 検索結果からプロフィールへ遷移する', async ({ appPage, api }) => {
    const target = await registerAccount(api)
    await appPage.goto('/search')

    await appPage.getByPlaceholder('ユーザーを検索...').fill(target.username)
    await appPage.getByText(`@${target.username}`).click()

    await expect(appPage).toHaveURL(new RegExp(`/users/${target.username}`))
    await expect(appPage.getByText(target.displayName).first()).toBeVisible()
  })

  // S5: プロフィール編集で username を変えると URL も追従する
  test('S5: プロフィールを編集すると username 変更が URL に反映される', async ({
    appPage,
    account,
  }) => {
    await appPage.goto(`/users/${account.username}`)
    await appPage.getByRole('button', { name: 'プロフィールを編集' }).click()

    const newUsername = `e2e_edit_${uniqueId()}`.slice(0, 50)
    await appPage.getByPlaceholder('username').fill(newUsername)
    await appPage.getByRole('button', { name: '保存する' }).click()

    // username 変更時は URL が新しいハンドルへ置き換わる
    await expect(appPage).toHaveURL(new RegExp(`/users/${newUsername}`))
    await expect(appPage.getByText(`@${newUsername}`)).toBeVisible()
  })
})
