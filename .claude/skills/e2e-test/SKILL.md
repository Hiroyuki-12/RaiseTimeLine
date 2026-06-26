---
name: e2e-test
description: RaiseTimeLine の E2E（Playwright）テストとブラウザパフォーマンステストを必要なとき（オンデマンド）に一気通貫で実行する。環境起動（DB+backend を規定ポートで、frontend は自動起動）→ Playwright 実行（シナリオ + ブラウザ性能計測）→ 結果報告（HTML/MD レポート）→ 後片付け（サーバー停止）までを担う。「E2E テストして」「シナリオテスト回して」「Playwright 実行して」「ブラウザパフォーマンス測って」「画面の動作確認を自動で」などで発火する。CI で毎回回すものではなく、リリース前・結合部（認証/Cookie/DB 整合）に関わる変更時の回帰確認に使う。
---

# E2E Test Runner (RaiseTimeLine)

`e2e/` 配下の Playwright テスト一式を、必要なときにオンデマンドで実行するための手順。
テストの実体（シナリオ・フィクスチャ・性能計測）は `e2e/` にあり、本 skill はその実行と
前後処理（環境起動・後片付け）をオーケストレーションする。詳細は [e2e/README.md](../../../e2e/README.md) を参照。

## 前提と固定値

- ツール: Playwright（`@playwright/test`。`tests/*.spec.ts` を TypeScript のまま実行）/ ブラウザは Chromium
- 規定ポート（[server-port-policy](../server-port-policy/SKILL.md) と CLAUDE.md に従う・変更禁止）:
  - PostgreSQL `5432` / バックエンド `8080` / フロントエンド `5173`
- テストデータ: 各テストが `/api/auth/register` で**一意ユーザーを新規作成**する自己完結方式。
  **DB を TRUNCATE しない**（perf の seed とは別物。既存データを破壊しない）。
- フロントエンド（5173）は `playwright.config.ts` の `webServer` が**自動起動／既存再利用**する。
  DB(5432) と backend(8080) は本 skill で起動する。

## 実行フロー

ユーザーから「E2E テストして」等を依頼されたら、以下を順に行う。
何を回すか不明なら、シナリオ（auth/post/comment/social）→ 必要に応じて perf、を提案する。

### 1. 依存の確認（初回のみ）

```bash
cd e2e && npm install && npx playwright install chromium
```

### 2. 環境起動（規定ポート厳守）

[server-port-policy](../server-port-policy/SKILL.md) に従い、別ポートに逃げない。

```bash
# DB（PostgreSQL 5432）
docker compose up -d
for i in $(seq 1 20); do docker compose exec -T db pg_isready -U raisetimeline >/dev/null 2>&1 && break; sleep 1; done

# バックエンド（8080）。Bash の run_in_background:true で起動する
cd backend && ./gradlew bootRun
```

バックエンド起動確認（`/api/posts` は認証必須なので **HTTP 403 が返れば「起動済み」**）:

```bash
for i in $(seq 1 90); do
  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/posts)
  [ "$code" != "000" ] && { echo "backend up (HTTP $code)"; break; }
  sleep 2
done
```

> 既に 8080/5432 が起動済みなら再起動不要。占有プロセスが**別物**の場合は port-policy に従い
> 停止してから規定ポートで起動し直す。フロントは Playwright が自動で立ち上げる（既存があれば再利用）。

### 3. テスト実行

```bash
cd e2e

# シナリオ E2E（auth / post / comment / social）
npm run test:scenario

# ブラウザパフォーマンス計測
npm run test:perf

# すべて
npm test
```

- 特定ファイルだけ: `npx playwright test tests/auth.spec.ts`
- ブラウザ表示しながら: `npm run test:headed`
- 上書き用環境変数: `E2E_BASE_URL`（フロント URL）/ `E2E_API_URL`（バックエンド URL）

### 4. 結果報告

- シナリオ: 各 spec の PASS/FAIL を表でまとめて報告。失敗があれば trace を確認する
  （`npx playwright show-trace e2e/results/test-artifacts/.../trace.zip`）。
- ブラウザ性能: `cat e2e/results/browser-perf.md` で各指標の実測値・しきい値・合否を報告。
  初回はしきい値が暫定値のため、実測に合わせて `tests/perf.spec.ts` の budget を調整する旨を伝える。
- HTML レポート: `cd e2e && npm run report`（`results/html-report/` を開く）を案内。

### 5. 後片付け（必ずユーザーに確認してから）

検証用に起動したサーバーを停止する。**ユーザーが「止めて」と言ったとき、または明らかに不要なときに実施**。

```bash
# バックエンド（8080 を握っているプロセスを停止）
PID=$(lsof -ti :8080 -sTCP:LISTEN); [ -n "$PID" ] && kill "$PID"
# Playwright が起動した frontend dev server（5173）も停止する
PID=$(lsof -ti :5173 -sTCP:LISTEN); [ -n "$PID" ] && kill "$PID"
# DB コンテナ停止・削除
docker compose down
lsof -i :8080 -i :5432 -i :5173 -sTCP:LISTEN -P -n || echo "stopped"
```

## 注意・禁止事項

- **別ポートに逃げない**（port-policy 厳守）。競合時は占有プロセスを止めて規定ポートで起動し直す。
- テストは新規データのみ作成し DB を破壊しないが、念のため**本番 DB には向けない**（常にローカル）。
- `e2e/results/` は git 追跡対象外（実行ごとに変わる成果物）。コミットしない。
- spec / フィクスチャを変更したら `cd e2e && npm run typecheck`（tsc --noEmit）でエラー 0 を確認する。
- 画像/S3 連携の実検証はスコープ外（テキスト投稿中心）。必要になったら別途追加する。
- CI で毎回回すものではない。リリース前・結合部に関わる変更時の回帰確認に使う。

## 参照

- 実行手順・シナリオ詳細・レポートの見方: [e2e/README.md](../../../e2e/README.md)
- ポート運用: [server-port-policy](../server-port-policy/SKILL.md) / [CLAUDE.md](../../../CLAUDE.md)
- サーバ API の負荷試験（k6）: [perf-test](../perf-test/SKILL.md)
