/**
 * 認証・セッションのシナリオ E2E（A1〜A7）。
 *
 * 観点（技法）:
 *   - 正常系/異常系の同値分割（ログイン成功 / 失敗）
 *   - 状態遷移（未ログイン→登録→ホーム、ログイン→ログアウト→ログイン）
 *   - 結合部の検証（リロードでのセッション復元、401→自動リフレッシュ→透過リトライ）
 *
 * これらは MSW モックのユニットテストでは検証できない「実 Cookie / 実バックエンド貫通」を確認する。
 */

import { test, expect } from '../fixtures/auth'
import { uniqueAccount } from '../fixtures/unique'
import { registerAccount } from '../fixtures/api'

test.describe('認証・セッション', () => {
  // A1: 画面から新規登録するとホームへ遷移する
  test('A1: 新規登録するとホームに遷移する', async ({ page }) => {
    const acc = uniqueAccount()
    await page.goto('/register')
    await page.locator('#displayName').fill(acc.displayName)
    await page.locator('#username').fill(acc.username)
    await page.locator('#email').fill(acc.email)
    await page.locator('#password').fill(acc.password)
    await page.locator('#passwordConfirm').fill(acc.password)
    await page.getByRole('button', { name: '新規登録' }).click()

    await page.waitForURL('**/home')
    await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
    // サイドバーにログインユーザーの @handle が表示される
    await expect(page.getByText(`@${acc.username}`)).toBeVisible()
  })

  // A2: 登録済みユーザーが画面からログインするとホームへ遷移する
  test('A2: 既存ユーザーでログインできる', async ({ page, api, loginAs }) => {
    const acc = await registerAccount(api)
    await loginAs(page, acc)
    await expect(page).toHaveURL(/\/home/)
  })

  // A3: 誤ったパスワードではログインできず、エラーメッセージが表示される
  // 認証エンドポイントの 401 はリフレッシュ対象外なので、ログイン画面にエラーが表示され画面に留まる。
  test('A3: 誤ったパスワードはエラーメッセージを表示する', async ({ page, api }) => {
    const acc = await registerAccount(api)
    await page.goto('/login')
    await page.locator('#email').fill(acc.email)
    await page.locator('#password').fill('WrongPass999')
    await page.getByRole('button', { name: 'ログイン' }).click()

    // ホームへは遷移せず、エラーメッセージ（赤字の段落）が表示される
    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByText('メールアドレスまたはパスワードが正しくありません')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'ホーム' })).toHaveCount(0)
  })

  // A4: ログアウトするとログイン画面へ戻る
  test('A4: ログアウトするとログイン画面に戻る', async ({ appPage }) => {
    await appPage.getByRole('button', { name: 'ログアウト' }).click()
    await appPage.waitForURL('**/login')
    await expect(appPage.getByRole('button', { name: 'ログイン' })).toBeVisible()
  })

  // A5: ログイン後にリロードしてもセッションが復元される（refresh Cookie 経由）
  test('A5: リロードでセッションが復元される', async ({ appPage }) => {
    // リロードでメモリ上のアクセストークンは消えるが、HttpOnly Cookie から再取得される
    await appPage.reload()
    await expect(appPage.getByRole('heading', { name: 'ホーム' })).toBeVisible()
    await expect(appPage).toHaveURL(/\/home/)
  })

  // A6: 未ログインで保護ページへ直接アクセスするとログインへリダイレクトされる
  test('A6: 未ログインで /home に入るとログインへ飛ばされる', async ({ page }) => {
    // 新しいブラウザコンテキストにはリフレッシュ Cookie が無いため復元に失敗しリダイレクトされる
    await page.goto('/home')
    await page.waitForURL('**/login')
    await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible()
  })

  // A7: アクセストークン失効（401）が起きても、自動リフレッシュ＋リトライで透過的に復帰する
  test('A7: 401 を受けても自動リフレッシュでタイムラインが復帰する', async ({
    page,
    api,
    loginAs,
  }) => {
    const acc = await registerAccount(api)

    // タイムライン取得（GET /api/posts）の「最初の1回だけ」401 を注入する。
    // フロントの axios インターセプターが /api/auth/refresh を呼んで再試行する挙動を検証する。
    let injected = 0
    let refreshCalled = false
    await page.route(
      (url) => url.pathname === '/api/posts',
      async (route) => {
        injected += 1
        if (injected === 1) {
          // 1回目はトークン失効を模して 401 を返す
          await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
        } else {
          // 2回目以降（リフレッシュ後のリトライ）は本物のバックエンドへ流す
          await route.continue()
        }
      },
    )
    // リフレッシュが呼ばれたことを確認するための監視
    page.on('request', (req) => {
      if (req.url().includes('/api/auth/refresh')) refreshCalled = true
    })

    await loginAs(page, acc)

    // 401 が注入されても最終的にホーム（タイムライン領域）が表示される
    await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
    expect(injected).toBeGreaterThanOrEqual(2) // 401 → リトライの2回以上叩かれている
    expect(refreshCalled).toBeTruthy() // 自動リフレッシュが発火した
  })
})
