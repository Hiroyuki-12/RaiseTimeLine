# E2E テスト (Playwright)

RaiseTimeLine の **E2E（シナリオ）テスト**と**ブラウザパフォーマンステスト**一式です。
実ブラウザ（Chromium）から「フロント → Vite プロキシ → バックエンド → 実 DB」を貫通して、
ユーザー操作目線で主要フローを検証します。MSW モックのユニットテスト（`frontend/`）では
検出できない結合部（JWT/Cookie・401 自動リフレッシュ・実 DB 整合）を守るのが目的です。

- 対象: ログイン後の主要フロー全体（認証 / 投稿 / コメント / フォロー / 検索 / プロフィール）
- ツール: [Playwright](https://playwright.dev/)（TypeScript をネイティブ実行）
- ブラウザ: Chromium（初期。`playwright.config.ts` の `projects` に追加すれば拡張可能）
- 非対象: 画像/S3 連携の実検証（テキスト投稿中心。詳細は下記）

---

## ディレクトリ構成

```
e2e/
├── package.json            # @playwright/test と npm スクリプト
├── playwright.config.ts    # baseURL=5173 / chromium / webServer（フロント自動起動）
├── tsconfig.json           # 型チェック用（tsc --noEmit）
├── fixtures/
│   ├── unique.ts           # 衝突しない username/email を払い出す
│   ├── api.ts              # バックエンド直叩きでテストデータを準備（登録/投稿/コメント/フォロー）
│   └── auth.ts             # Playwright フィクスチャ（api / account / loginAs / appPage）
├── lib/
│   └── perf.ts             # Performance API 収集・レポート出力
├── tests/
│   ├── auth.spec.ts        # A1〜A7 認証・セッション
│   ├── post.spec.ts        # P1〜P7 投稿・いいね
│   ├── comment.spec.ts     # C1〜C4 コメント（返信）
│   ├── social.spec.ts      # S1〜S5 フォロー・検索・プロフィール
│   └── perf.spec.ts        # ブラウザパフォーマンス計測
└── results/                # trace / レポート出力（git 追跡対象外）
```

---

## テストデータの方針

各テストは実行時に `/api/auth/register` で**一意なユーザーを新規作成**し、必要なデータ
（投稿・コメント・フォロー関係）も API で用意する**自己完結方式**です。

- 既存 DB を **TRUNCATE しない**（`perf/` の負荷試験用 seed とは別物。DB を破壊しません）
- username/email は実行ごとに一意なので、繰り返し実行しても衝突しません
- 検証対象のユーザー操作だけを画面（UI）で行い、お膳立ては API で素早く作ります

---

## 前提準備（規定ポート厳守 / CLAUDE.md）

E2E は実バックエンド・実 DB を貫通するため、以下を**規定ポートで**起動しておきます。

```bash
# 1) PostgreSQL（5432）
docker compose up -d

# 2) バックエンド（8080）
cd backend && ./gradlew bootRun
```

フロントエンド（5173）は Playwright の `webServer` が自動起動します
（既に `npm run dev` で起動済みなら、それを再利用します）。手動起動でも構いません。

### 依存インストール（初回のみ）

```bash
cd e2e
npm install
npx playwright install chromium   # ブラウザ本体の取得
```

---

## 実行手順

```bash
cd e2e

# シナリオ E2E（auth / post / comment / social）
npm run test:scenario

# ブラウザパフォーマンス計測
npm run test:perf

# すべて実行
npm test

# HTML レポートを開く（実行後）
npm run report
```

ブラウザを見ながら実行したい場合は `npm run test:headed`、
特定ファイルだけなら `npx playwright test tests/auth.spec.ts` のように指定します。

主な上書き用環境変数:

| 変数 | 意味 | 既定 |
| --- | --- | --- |
| `E2E_BASE_URL` | フロントエンドの URL | `http://localhost:5173` |
| `E2E_API_URL` | バックエンドの URL（データ準備の直叩き先） | `http://localhost:8080` |

---

## レポート出力

| 出力 | 内容 |
| --- | --- |
| `results/html-report/` | Playwright の HTML レポート（`npm run report` で開く。失敗時の trace 付き） |
| `results/browser-perf.md` | ブラウザパフォーマンスの人間可読サマリ（各指標の実測値・しきい値・合否） |
| `results/browser-perf.json` | 同 生データ（過去結果との比較・機械処理用） |
| `results/test-artifacts/` | **失敗時の記録**（スクショ / 動画 / trace / ページ状態スナップショット） |

### 失敗時に残る記録（どういう状況で落ちたかを後から追える）

テストが失敗すると、そのテストの `results/test-artifacts/<テスト名>/` に以下が自動保存される
（`playwright.config.ts` の `trace/screenshot/video: *-on-failure` 設定による）。

| ファイル | 内容 |
| --- | --- |
| `test-failed-1.png` | 失敗時点の画面スクリーンショット（最終状態の見た目） |
| `video.webm` | テスト開始〜失敗までの操作の流れを記録した動画 |
| `trace.zip` | 各操作ステップの DOM スナップショット・ネットワーク・コンソールログ・操作ログの完全記録 |
| `error-context.md` | 失敗時のページ状態（アクセシビリティツリー）のテキストスナップショット |

```bash
cat results/browser-perf.md                                       # ブラウザ性能の合否サマリ
npx playwright show-report results/html-report                    # 失敗の記録（動画/スクショ/trace）を一覧で確認
npx playwright show-trace results/test-artifacts/<テスト名>/trace.zip   # 操作を 1 ステップずつ巻き戻して状況を再現
```

---

## カバーするシナリオ

| ファイル | ケース |
| --- | --- |
| auth.spec.ts | A1 登録 / A2 ログイン / A3 ログイン失敗 / A4 ログアウト / A5 セッション復元 / A6 未認証ガード / A7 401自動リフレッシュ |
| post.spec.ts | P1 作成 / P2 編集 / P3 削除 / P4 いいね楽観的更新 / P5 いいね失敗ロールバック / P6 無限スクロール / P7 文字数境界 |
| comment.spec.ts | C1 作成 / C2 編集 / C3 削除 / C4 文字数境界 |
| social.spec.ts | S1 フォロー / S2 解除 / S3 検索 / S4 検索→遷移 / S5 プロフィール編集（URL 追従） |
| perf.spec.ts | ログイン→描画 / タイムライン初表示 / FCP・load / モーダル開閉 / 無限スクロール |

---

## ブラウザパフォーマンステストについて

`perf.spec.ts` は Performance API（Navigation Timing / Paint Timing）と Playwright 操作の
実時間計測で、ブラウザの体感性能を計測します。しきい値は**初回計測のベースライン化**を
目的とした暫定値です。実測に合わせて各指標の budget を調整してください
（`tests/perf.spec.ts` の `report.add(name, value, unit, budget)` の第4引数）。

dev server（Vite）での計測のため、本番ビルドより遅めに出ます。**回帰監視の相対比較**に使います。

---

## 注意事項

- アクセストークンの有効期限は 15 分。各テストはその範囲で完結します。
- 画像/S3 連携の実検証は本スイートのスコープ外です（テキスト投稿中心）。
  ファイル選択 UI の動作確認や S3 疎通テストが必要になったら別途追加します。
- ポート競合時は別ポートに逃げず、占有プロセスを停止して規定ポートで起動し直してください
  （CLAUDE.md / server-port-policy）。
- CI で毎回回すものではなく、リリース前・結合部に関わる変更時の回帰確認に使います。
