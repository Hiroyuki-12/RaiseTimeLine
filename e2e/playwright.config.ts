/**
 * Playwright 設定ファイル。
 *
 * 前提（ポート運用ルール厳守 / CLAUDE.md）:
 *   - フロントエンド: http://localhost:5173（Vite dev server）
 *   - バックエンド  : http://localhost:8080（Spring Boot。/api を Vite がプロキシ）
 *   - PostgreSQL    : localhost:5432
 *
 * E2E は「実ブラウザ → Vite → バックエンド → 実 DB」を貫通して検証するため、
 * 上記 3 つはあらかじめ起動しておく必要がある（e2e-test スキル / README 参照）。
 * フロントエンドだけは webServer で自動起動を試みる（既に起動済みなら再利用する）。
 */

import { defineConfig, devices } from '@playwright/test'

// フロントエンドの URL。テストはここを基準（baseURL）に画面操作する。
const FRONTEND_URL = process.env.E2E_BASE_URL ?? 'http://localhost:5173'

export default defineConfig({
  testDir: './tests',
  // 生成物（trace / レポート）の出力先。git 追跡対象外。
  outputDir: './results/test-artifacts',

  // 各テストは独立ユーザーを作るため並列実行して問題ない。
  fullyParallel: true,
  // テストコードに test.only を残したままのコミットを CI で失敗させる。
  forbidOnly: !!process.env.CI,
  // 実バックエンド（dev）を複数ワーカーで同時に叩くと、稀に一時的な負荷で操作がタイムアウトする。
  // ロジック起因ではない一過性の失敗を吸収するため 1 回リトライする（失敗時の trace は retain-on-failure で残る）。
  retries: process.env.CI ? 2 : 1,
  // dev バックエンドへの同時アクセスによる輻輳を抑えるため、ローカルのワーカー数を控えめにする。
  workers: process.env.CI ? 1 : 3,

  // レポーター: ターミナル表示 + HTML（後から show-report で開ける）。
  reporter: [
    ['list'],
    ['html', { outputFolder: 'results/html-report', open: 'never' }],
  ],

  use: {
    baseURL: FRONTEND_URL,
    // 失敗時に「どういう状況だったか」を必ず残すための記録設定。
    // - trace: 失敗したテストの trace を保持する。各ステップの DOM スナップショット・ネットワーク・
    //          コンソールログ・操作ログを丸ごと記録するので、`show-trace` で当時の状況を再現できる。
    //          on-first-retry だとリトライ前提（ローカルは retries:0）で初回失敗の記録が残らないため
    //          retain-on-failure にする。
    trace: 'retain-on-failure',
    // 失敗時のスクリーンショット（最終状態の見た目）を残す。
    screenshot: 'only-on-failure',
    // 失敗時の操作の流れを動画でも残す（時系列で何が起きたか目で追える）。
    video: 'retain-on-failure',
    // 操作のデフォルトタイムアウト（要素待機など）。dev バックエンドの遅延に余裕を持たせる。
    actionTimeout: 15_000,
  },

  // 期待値（expect）のデフォルト待機時間。ネットワーク往復＋dev バックエンドの遅延を考慮して長めに取る。
  expect: { timeout: 15_000 },

  projects: [
    {
      // 初期はブラウザを Chromium に固定する（後から Firefox/WebKit を projects 追加で拡張可能）。
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // フロントエンド（Vite dev server）を自動起動する。
  // 既に 5173 で起動済みなら reuseExistingServer でそれを使う（二重起動を避ける）。
  // ※ バックエンド・DB はここでは起動しない（スキル / README の手順で別途起動する）。
  webServer: {
    command: 'npm run dev',
    cwd: '../frontend',
    url: FRONTEND_URL,
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
