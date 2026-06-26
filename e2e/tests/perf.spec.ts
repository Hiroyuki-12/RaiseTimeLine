/**
 * ブラウザパフォーマンステスト（時間計測）。
 *
 * k6（サーバ API の負荷試験）とは別に、ここでは実ブラウザ（Chromium）で
 * 「描画・遷移・操作のレイテンシ」を計測する。Performance API と Playwright 操作の
 * 実時間計測を組み合わせ、しきい値（budget）で回帰を検知する。
 *
 * 計測結果は results/browser-perf.{md,json} に出力する。
 * しきい値は初回計測のベースライン化を目的とした暫定値で、超過は expect.soft で記録する
 * （1 指標の超過で他の計測が止まらないよう、全指標を採取してから判定する）。
 *
 * 注意: dev server（Vite）での計測のため、本番ビルドより遅めに出る。回帰監視の相対比較に使う。
 */

import { test, expect } from '../fixtures/auth'
import { createPosts } from '../fixtures/api'
import { PerfReport, getNavigationMetrics } from '../lib/perf'

test.describe('ブラウザパフォーマンス', () => {
  test('主要フローのレイテンシと描画指標を計測する', async ({ page, api, account, loginAs }) => {
    const report = new PerfReport()

    // 現実的なタイムライン（描画コスト）を作るため、事前に 30 件の投稿を用意する。
    await createPosts(api, account.accessToken, 30, `perf ${Date.now()}`)

    // --- 指標1: ログイン操作 → ホーム描画完了までのレイテンシ ---
    const loginStart = Date.now()
    await loginAs(page, account)
    report.add('ログイン→ホーム描画', Date.now() - loginStart, 'ms', 2500)

    // --- 指標2: /home へのフル遷移（描画系タイミング）と、最初の投稿カード可視まで ---
    const reloadStart = Date.now()
    await page.reload()
    await page.getByTestId('post-card').first().waitFor({ state: 'visible' })
    report.add('タイムライン初表示（最初の投稿カード可視）', Date.now() - reloadStart, 'ms', 1500)

    // Navigation Timing / Paint Timing をブラウザから取得する
    // しきい値は dev server のウォーム時の実測（数十 ms）に余裕を持たせた回帰検知ライン。
    const nav = await getNavigationMetrics(page)
    if (nav.domContentLoaded != null) report.add('DOMContentLoaded', nav.domContentLoaded, 'ms', 1500)
    if (nav.load != null) report.add('load（ページ読み込み完了）', nav.load, 'ms', 2000)
    if (nav.firstContentfulPaint != null) {
      report.add('First Contentful Paint', nav.firstContentfulPaint, 'ms', 1500)
    }
    // 転送量・リソース数はしきい値なしの参考値（回帰監視用）
    report.add('リソース総転送量', nav.transferSize, 'bytes')
    report.add('リソース数', nav.resourceCount, '件')

    // --- 指標3: 投稿モーダルの開く操作のレスポンス ---
    const modalStart = Date.now()
    await page.getByRole('button', { name: '投稿する' }).click()
    await page.getByPlaceholder('いまどうしてる？').waitFor({ state: 'visible' })
    report.add('投稿モーダル表示', Date.now() - modalStart, 'ms', 600)
    // モーダルを閉じておく（後続操作の邪魔をしないように Escape）
    await page.keyboard.press('Escape')

    // --- 指標4: 無限スクロールの 1 ページ追加読み込み ---
    const cards = page.getByTestId('post-card')
    const before = await cards.count()
    const scrollStart = Date.now()
    await page.getByRole('button', { name: 'さらに読み込む' }).click()
    await expect.poll(async () => await cards.count()).toBeGreaterThan(before)
    report.add('無限スクロール追加読み込み', Date.now() - scrollStart, 'ms', 1500)

    // 結果をファイルに書き出す（md / json）
    report.write()

    // しきい値付き指標を soft 判定（全指標を採取後に、超過があれば失敗扱い）
    for (const m of report.metricsWithBudget()) {
      expect.soft(m.value, `${m.name} がしきい値 ${m.budget}${m.unit} を超過`).toBeLessThanOrEqual(
        m.budget as number,
      )
    }
  })
})
