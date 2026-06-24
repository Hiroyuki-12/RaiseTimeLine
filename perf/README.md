# パフォーマンステスト (k6)

RaiseTimeLine のバックエンド API に対する**負荷テスト一式**です。
毎コミットで自動実行はせず、**任意のタイミングで手動実行**します
（リリース前・DB クエリ変更時・インデックス見直し時などの回帰確認用）。

- 対象: バックエンド API（ローカル環境限定）
- ツール: [k6](https://k6.io/)（テストシナリオを JavaScript で記述）
- 非対象: CI/CD 自動実行 / フロントエンド計測 / 本番環境への負荷掛け

---

## ディレクトリ構成

```
perf/
├── seed/
│   ├── seed.sql        # 大量テストデータ投入 SQL（TRUNCATE してから投入）
│   └── run-seed.sh     # ローカル DB へ seed.sql を流すラッパー
└── k6/
    ├── lib/
    │   ├── config.js   # BASE_URL / ユーザー数などの設定（環境変数で上書き可）
    │   └── auth.js     # ログインして JWT を取得する共通関数
    ├── smoke.js        # 疎通確認（1 VU / 30s）
    ├── timeline.js     # 主シナリオ: タイムライン取得（all / following）
    ├── post-create.js  # 投稿作成（content のみ）
    └── browse.js       # 閲覧系の混在（詳細 + コメント + 検索）
```

---

## 前提準備

### 1. k6 のインストール

```bash
# macOS
brew install k6

# 確認
k6 version
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
k6 run perf/k6/smoke.js
```

全チェックが green になれば、ログイン（= シードのパスワードハッシュ）と
主要 GET API が正常に動いています。

### Step 3. 各シナリオ実行

```bash
k6 run perf/k6/timeline.js      # 主シナリオ
k6 run perf/k6/post-create.js
k6 run perf/k6/browse.js

# 負荷を上げる例（VU 数・維持時間を上書き）
k6 run -e VUS=100 -e DURATION=5m perf/k6/timeline.js
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

各シナリオは実行後に `perf/results/` へレポートを自動出力します
（`perf/results/` の中身は実行ごとに変わる成果物なので git 追跡対象外）。

| ファイル | 内容 |
| --- | --- |
| `perf/results/<シナリオ名>.md` | 人が読む Markdown レポート（総合判定・p95・しきい値判定・エンドポイント別） |
| `perf/results/<シナリオ名>.json` | k6 の生データ（過去結果との差分比較・機械処理用） |

```bash
k6 run perf/k6/timeline.js
cat perf/results/timeline.md   # 結果レポートを確認
```

### グラフ付き HTML レポート（任意）

k6 標準の Web ダッシュボード機能で、時系列グラフ付きの HTML レポートも出せます。

```bash
# 実行中はブラウザで http://127.0.0.1:5665 を開くとリアルタイム表示。
# 終了時に HTML ファイルとして書き出す。
K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_EXPORT=perf/results/timeline.html \
  k6 run perf/k6/timeline.js
```

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

これらは初期値です。初回計測の実測に合わせて各 `.js` の `thresholds` を
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
- `post-create.js` は実データを INSERT します。多用するとデータが増えるので、
  必要に応じて Step 1 のシード再投入でリセットしてください。
