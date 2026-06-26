/**
 * アクセシビリティ（a11y）E2E。主要画面を実ブラウザで開き、axe-core で WCAG 違反を検出する。
 *
 * 観点（技法）:
 *   - 主要画面の網羅（ログイン / 新規登録 / 認証後ホーム）
 *   - 静的解析（eslint-plugin-jsx-a11y）では見つけられない「実際に描画された DOM」の問題を検出する。
 *     例: コントラスト比、フォーカス可能要素の重なり、aria 属性の実行時の整合性など。
 *
 * 判定方針:
 *   重大度 critical / serious の違反を 0 件であることを必須とする（リリースを止めるレベル）。
 *   moderate / minor はレポートに出すが失敗にはしない（段階的に改善するため）。
 *
 * 前提（ポート運用ルール厳守 / CLAUDE.md）:
 *   - フロントエンド: http://localhost:5173（webServer で自動起動を試みる）
 *   - バックエンド  : http://localhost:8080（事前起動が必要。e2e-test スキル / README 参照）
 *   - PostgreSQL    : localhost:5432
 */

import AxeBuilder from '@axe-core/playwright'
import { test, expect } from '../fixtures/auth'
import type { Page } from '@playwright/test'

// 検査対象とする WCAG のレベル。2.0/2.1 の A・AA を対象にする（一般的な達成目標）。
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

// 「重大」とみなす違反の重大度。これらが 1 件でもあればテストを失敗させる。
const BLOCKING_IMPACTS = ['critical', 'serious']

/**
 * 指定ページで axe を実行し、重大な違反が無いことを検証する共通ヘルパー。
 * 違反が出た場合は、どのルール・どの要素かをエラーメッセージに含めて原因を追いやすくする。
 */
async function expectNoSeriousA11yViolations(page: Page, label: string) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()

  // critical / serious のみ抽出（リリースブロッカー）。
  const blocking = results.violations.filter((v) => BLOCKING_IMPACTS.includes(v.impact ?? ''))

  // moderate / minor は参考情報としてログに残す（失敗にはしない）。
  const advisory = results.violations.filter((v) => !BLOCKING_IMPACTS.includes(v.impact ?? ''))
  if (advisory.length > 0) {
    const summary = advisory.map((v) => `${v.id}(${v.impact}) x${v.nodes.length}`).join(', ')
    console.log(`[a11y][${label}] 参考: 軽微な違反 ${advisory.length} 件 -> ${summary}`)
  }

  // 重大な違反があれば、ルール ID・重大度・該当要素を並べて失敗させる。
  const detail = blocking
    .map((v) => {
      const targets = v.nodes.map((n) => n.target.join(' ')).join(' / ')
      return `- [${v.impact}] ${v.id}: ${v.help}\n    要素: ${targets}`
    })
    .join('\n')

  expect(blocking, `「${label}」で重大な a11y 違反:\n${detail}`).toEqual([])
}

test.describe('アクセシビリティ（axe-core）', () => {
  // AX1: ログイン画面（未認証で誰でも到達できる入口）
  test('AX1: ログイン画面に重大な a11y 違反がない', async ({ page }) => {
    await page.goto('/login')
    // フォームが描画されてから検査する（読み込み途中の DOM で誤検出しないため）。
    await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible()
    await expectNoSeriousA11yViolations(page, 'ログイン画面')
  })

  // AX2: 新規登録画面（入力項目が多くラベル紐付けの問題が出やすい）
  test('AX2: 新規登録画面に重大な a11y 違反がない', async ({ page }) => {
    await page.goto('/register')
    await expect(page.getByRole('button', { name: '新規登録' })).toBeVisible()
    await expectNoSeriousA11yViolations(page, '新規登録画面')
  })

  // AX3: ホーム / タイムライン（認証後の中心画面。タブ・投稿欄などの操作要素が多い）
  test('AX3: ホーム画面に重大な a11y 違反がない', async ({ appPage }) => {
    // appPage は UI ログイン済みのページ。ホーム見出しが見えるまで待ってから検査する。
    await expect(appPage.getByRole('heading', { name: 'ホーム' })).toBeVisible()
    await expectNoSeriousA11yViolations(appPage, 'ホーム画面')
  })
})
