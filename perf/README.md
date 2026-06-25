# パフォーマンステスト (k6)

RaiseTimeLine のバックエンド API に対する**負荷テスト一式**です。
毎コミットで自動実行はせず、**任意のタイミングで手動実行**します
（リリース前・DB クエリ変更時・インデックス見直し時などの回帰確認用）。

- 対象: バックエンド API（ローカル環境限定）
- ツール: [k6](https://k6.io/)（テストシナリオを **TypeScript** で記述。k6 v2 は `.ts` をネイティブ実行）
- 非対象: CI/CD 自動実行 / フロントエンド計測 / 本番環境への負荷掛け

---

## ディレクトリ構成

```
perf/
├── package.json        # 型チェック用の依存（@types/k6 / typescript）と npm スクリプト
├── tsconfig.json       # tsc --noEmit による型チェック設定
├── run.sh              # k6 実行ラッパー（Web ダッシュボードを自動有効化）
├── seed/
│   ├── seed.sql        # 大量テストデータ投入 SQL（TRUNCATE してから投入）
│   └── run-seed.sh     # ローカル DB へ seed.sql を流すラッパー
└── k6/
    ├── lib/
    │   ├── config.ts   # BASE_URL / ユーザー数などの設定（環境変数で上書き可）
    │   ├── auth.ts     # ログインして JWT を取得する共通関数
    │   └── summary.ts  # 実行後に md / json レポートを生成する handleSummary
    ├── smoke.ts        # 疎通確認（1 VU / 30s）
    ├── timeline.ts     # 主シナリオ: タイムライン取得（all / following）
    ├── post-create.ts  # 投稿作成（content のみ。作成した投稿は同 iteration 内で自動削除）
    └── browse.ts       # 閲覧系の混在（詳細 + コメント + 検索）
```

---

## 前提準備

### 1. k6 のインストール

```bash
# macOS
brew install k6

# 確認（v2 以上なら .ts をネイティブ実行できる）
k6 version
```

### 1.5. 型チェック用の依存インストール（任意・初回のみ）

シナリオは TypeScript で記述しています。実行（`k6 run`）だけなら不要ですが、
型チェックやエディタ補完を使う場合は `perf/` で依存をインストールします。

```bash
cd perf && npm install
npm run typecheck   # tsc --noEmit。型エラーがあればここで検出される
```

### 2. DB・バックエンドの起動（規定ポート厳守）

CLAUDE.md のポート運用ルールに従い、必ず規定ポートで起動します
（DB=5432 / backend=8080）。

```bash
# 1) PostgreSQL（compose）
docker compose up -d

# 2) バックエンド（別ターミナル）
cd backend && ./gradlew bootRun
```

---

## 実行手順

### Step 1. シードデータ投入（初回 / データ作り直し時のみ）

> ⚠️ **警告**: `seed.sql` は対象テーブル（users / posts / likes / comments /
> follows / refresh_tokens）を **TRUNCATE で全削除**してから投入します。
> **ローカル開発 DB 専用**です。本番 DB では絶対に実行しないでください。

```bash
bash perf/seed/run-seed.sh
```

既定の投入規模（環境変数で変更可能）:

| 種別 | 件数 | 環境変数 |
| --- | --- | --- |
| users | 500 | `N_USERS` |
| posts | 50,000 | `N_POSTS` |
| likes | 200,000 | `N_LIKES` |
| comments | 100,000 | `N_COMMENTS` |
| follows | 5,000 | `N_FOLLOWS` |

```bash
# 規模を変える例
N_USERS=1000 N_POSTS=100000 bash perf/seed/run-seed.sh
```

投入されるユーザーは `loaduser1@example.com` 〜 `loaduserN@example.com`、
パスワードは全員 `Password123`（k6 がこの規則でログインします）。

### Step 2. 疎通確認

```bash
bash perf/run.sh smoke
```

全チェックが green になれば、ログイン（= シードのパスワードハッシュ）と
主要 GET API が正常に動いています。

`run.sh` は **k6 の Web ダッシュボードを自動で有効化**するラッパーです
（実行中は http://127.0.0.1:5665 でグラフをリアルタイム表示、終了時に HTML を出力）。
ダッシュボード不要なら素の `k6 run perf/k6/smoke.ts` でも実行できます。

### Step 3. 各シナリオ実行

```bash
bash perf/run.sh timeline       # 主シナリオ
bash perf/run.sh post-create
bash perf/run.sh browse

# 負荷を上げる例（VU 数・維持時間を上書き。run.sh は環境変数をそのまま尊重する）
VUS=100 DURATION=5m bash perf/run.sh timeline

# npm スクリプト経由でも実行できる（cd perf 後）
cd perf && npm run perf:timeline
```

主な上書き用環境変数:

| 変数 | 意味 | 既定 |
| --- | --- | --- |
| `BASE_URL` | バックエンド URL | `http://localhost:8080` |
| `VUS` | ピーク仮想ユーザー数 | シナリオ依存 |
| `DURATION` | 定常負荷の維持時間 | シナリオ依存 |
| `SEED_USER_COUNT` | シードユーザー数（seed の `N_USERS` と揃える） | `500` |

---

## レポート出力

`bash perf/run.sh <シナリオ名>` で実行すると、`perf/results/` に 3 種類のレポートが
自動出力されます（`perf/results/` の中身は実行ごとに変わる成果物なので git 追跡対象外）。

| 出力 | 内容 |
| --- | --- |
| **実行中** http://127.0.0.1:5665 | 時系列グラフ（p95 推移・リクエスト数・VU 数など）をブラウザでリアルタイム表示 |
| `perf/results/<シナリオ名>.html` | **グラフ付き HTML レポート**（終了後にブラウザで開いて確認） |
| `perf/results/<シナリオ名>.md` | 人が読む Markdown サマリ（総合判定・p95・しきい値判定・エンドポイント別） |
| `perf/results/<シナリオ名>.json` | k6 の生データ（過去結果との差分比較・機械処理用） |

```bash
bash perf/run.sh timeline
open perf/results/timeline.html   # グラフ付きレポートをブラウザで開く（macOS）
cat  perf/results/timeline.md     # テキストの合否サマリを確認
```

> HTML / リアルタイム表示は `run.sh` が環境変数（`K6_WEB_DASHBOARD` ほか）で
> 自動有効化しています。素の `k6 run perf/k6/timeline.ts` で実行した場合は
> md / json のみ出力され、グラフ付き HTML は生成されません。

## 結果の読み方

k6 実行後のサマリで特に見るべき指標:

- **`http_req_duration` の `p(95)`** … 95% のリクエストがこの時間以内に完了。
  レイテンシの体感に最も近い指標。各シナリオの `thresholds` で合否判定される。
- **`http_req_failed`** … 失敗率。1% 未満が必須（しきい値で判定）。
- **`checks`** … ステータスコード等のアサーション成功率。100% が理想。
- **`iterations` / `http_reqs`** … スループット（処理量）。

`thresholds` を 1 つでも超えると **k6 はゼロ以外の終了コード**で終わるため、
合否を機械的に判定できます。

### 初期しきい値（ベースライン）

| シナリオ | 主なしきい値 |
| --- | --- |
| smoke | `p95 < 800ms`, 失敗率 `< 1%` |
| timeline | `p95 < 500ms`（all / following）, 失敗率 `< 1%` |
| post-create | `p95 < 700ms`, 失敗率 `< 1%` |
| browse | `p95 < 600ms`（各 API）, 失敗率 `< 1%` |

これらは初期値です。初回計測の実測に合わせて各 `.ts` の `thresholds` を
チューニングしてください。

---

## ボトルネック調査のヒント

タイムライン取得（`GET /api/posts`）が遅い場合、SQL の実行計画を確認します。

```bash
# psql で接続
PGPASSWORD=password psql -h localhost -p 5432 -U raisetimeline -d raisetimeline
```

```sql
EXPLAIN ANALYZE
SELECT p.id, p.content,
       (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
       (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) AS comment_count
FROM posts p
INNER JOIN users u ON p.user_id = u.id
ORDER BY p.created_at DESC
LIMIT 20 OFFSET 0;
```

- `idx_posts_created_at` を使った Index Scan になっているか
- いいね数 / コメント数のスカラーサブクエリ（`idx_likes_post_id` /
  `idx_comments_post_id`）がボトルネックになっていないか

を確認します。改善（集計の事前集約、ページング方式の変更など）の判断材料にします。

---

## 注意事項

- アクセストークンの有効期限は **15 分**。各シナリオは VU ごとに 1 回ログインして
  トークンを使い回すため、テスト時間は 15 分以内に収めてください
  （それ以上の長時間試験では再ログイン処理の追加が必要）。
- `post-create.ts` は実データを INSERT しますが、**作成した投稿は同じイテレーション内で
  `DELETE /api/posts/{id}` により自動削除される**ため、テストデータは蓄積しません
  （何度実行しても DB に負荷テスト投稿が残りません）。
