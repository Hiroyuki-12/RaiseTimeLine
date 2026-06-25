---
name: perf-test
description: RaiseTimeLine のバックエンド API に対する k6 パフォーマンステストを必要なとき（オンデマンド）に一気通貫で実行する。環境起動（DB+backend を規定ポートで）→ シードデータ投入 → k6 シナリオ実行（HTML/MD/JSON レポート自動出力）→ 結果報告 → 後片付け（サーバー停止）までを担う。「パフォーマンステストして」「負荷テスト実行して」「k6 回して」「perf テスト」「タイムラインの性能を測って」などで発火する。CI で毎回回すものではなく、リリース前・クエリ/インデックス変更時の回帰確認に使う。
---

# Performance Test Runner (RaiseTimeLine)

`perf/` 配下の k6 負荷テスト一式を、必要なときにオンデマンドで実行するための手順。
テストの実体（シナリオ・シード・レポート設定）は `perf/` にあり、本 skill はその実行と
前後処理（環境起動・後片付け）をオーケストレーションする。詳細は [perf/README.md](../../../perf/README.md) を参照。

## 前提と固定値

- ツール: k6（v2 以上。`perf/k6/*.ts` を **TypeScript のままネイティブ実行**できる）
- 規定ポート（[server-port-policy](../server-port-policy/SKILL.md) と CLAUDE.md に従う・変更禁止）:
  - PostgreSQL `5432` / バックエンド `8080`
- シナリオ: `smoke`（疎通 1VU/30s）/ `timeline`（主シナリオ 0→50VU）/ `post-create`（投稿作成）/ `browse`（閲覧混在）
- 実行ラッパー: `bash perf/run.sh <シナリオ名>`
  - k6 Web ダッシュボードを自動有効化（実行中 http://127.0.0.1:5665 のリアルタイムグラフ＋
    終了後 `perf/results/<名>.html` を出力）。**ダッシュボード UI は英語**（k6 標準のため日本語化不可）。
  - VUS / DURATION / BASE_URL などは環境変数で上書き可: `VUS=100 DURATION=5m bash perf/run.sh timeline`

## 実行フロー

ユーザーから「パフォーマンステストして」等を依頼されたら、以下を順に行う。
どのシナリオを回すか不明なら `smoke`（疎通）→ 必要に応じて他、を提案する。

### 1. k6 の存在確認

```bash
k6 version   # 無ければ `brew install k6` を案内して中断
```

### 2. 環境起動（規定ポート厳守）

[server-port-policy](../server-port-policy/SKILL.md) に従い、別ポートに逃げない。

```bash
# DB（PostgreSQL 5432）
docker compose up -d
# DB が ready になるまで待つ
for i in $(seq 1 20); do docker compose exec -T db pg_isready -U raisetimeline >/dev/null 2>&1 && break; sleep 1; done

# バックエンド（8080）。run_in_background で起動する（Bash の run_in_background:true 推奨）
cd backend && ./gradlew bootRun
```

バックエンド起動完了の確認（`/api/posts` は認証必須なので **HTTP 403 が返れば「起動済み」**）:

```bash
for i in $(seq 1 90); do
  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/posts)
  [ "$code" != "000" ] && { echo "backend up (HTTP $code)"; break; }
  sleep 2
done
```

> 既に 8080/5432 が起動済みなら再起動は不要。占有プロセスが**別物**の場合は
> port-policy に従い停止してから規定ポートで起動し直す。

### 3. シードデータ投入

⚠️ `perf/seed/seed.sql` は対象テーブルを **TRUNCATE** する。**ローカル開発 DB 専用**。

**重要**: このホストには `psql` が入っていないため、`perf/seed/run-seed.sh` は失敗する。
**DB コンテナ内の psql に流し込む**こと（run-seed.sh と同じ `-v` 変数を渡す）:

```bash
docker compose exec -T db psql -U raisetimeline -d raisetimeline \
  -v n_users=500 -v n_posts=50000 -v n_likes=200000 -v n_comments=100000 -v n_follows=5000 \
  < perf/seed/seed.sql
```

規模を変えたいときは `-v n_posts=...` 等の数値を調整する（README の既定値が目安）。
シードは投稿件数を変えなければ初回のみでよい（`post-create` は自分が作ったデータを消すので再投入不要）。

### 4. シナリオ実行

```bash
bash perf/run.sh smoke          # まず疎通（全 check green を確認）
bash perf/run.sh timeline       # 主シナリオ
bash perf/run.sh post-create
bash perf/run.sh browse
```

- 短時間で済ませたいときは `VUS` / `DURATION` を絞る（例: `VUS=5 DURATION=20s bash perf/run.sh post-create`）。
- 各実行で `perf/results/<名>.html`（グラフ）/ `.md`（合否サマリ）/ `.json`（生データ）が出る。
- アクセストークン有効期限は 15 分。各 VU は 1 回ログインして使い回すため、1 試験は 15 分以内に収める。

### 5. クリーンアップの確認（post-create を回したときのみ）

`post-create` は作成した投稿を同一イテレーション内で `DELETE` するため、データは蓄積しない。
それを実測で確認する（実行前後で件数が一致するはず）:

```bash
# 実行前
docker compose exec -T db psql -U raisetimeline -d raisetimeline -tA -c "SELECT count(*) FROM posts;"
# （post-create 実行）
# 実行後 → 同じ件数であること
docker compose exec -T db psql -U raisetimeline -d raisetimeline -tA -c "SELECT count(*) FROM posts;"
```

レポートの `checks` 成功率が 100%（`create post 201` と `delete post 204` の両方）であることも確認する。

### 6. 結果報告

各シナリオの合否（PASS/FAIL）・p95・失敗率を表でまとめて報告する。
グラフを見たい場合は HTML を開くよう案内する（`open perf/results/<名>.html`）か、
ライブで見るなら「実行中に http://127.0.0.1:5665 を開く」と案内する。

### 7. 後片付け（必ずユーザーに確認してから）

検証用に起動したサーバーを停止する。**ユーザーが「止めて」と言ったとき、または明らかに不要なときに実施**。

```bash
# バックエンド（8080 を握っているプロセスを停止）
PID=$(lsof -ti :8080 -sTCP:LISTEN); [ -n "$PID" ] && kill "$PID"
# bootRun を Bash の run_in_background で起動していれば TaskStop でも可
# DB コンテナ停止・削除
docker compose down
# ポートが解放されたことを確認
lsof -i :8080 -i :5432 -sTCP:LISTEN -P -n || echo "stopped"
```

## 注意・禁止事項

- **別ポートに逃げない**（port-policy 厳守）。競合時は占有プロセスを止めて規定ポートで起動し直す。
- **本番 DB に seed.sql を流さない**（TRUNCATE するため）。対象は常にローカルの compose DB。
- HTML ダッシュボードの**英語表記は仕様**（k6 標準）。日本語の合否サマリは `perf/results/<名>.md` を見る。
- `perf/results/` は git 追跡対象外（実行ごとに変わる成果物）。コミットしない。
- 型を直したり k6 スクリプトを変更したら、`cd perf && npm run typecheck`（tsc --noEmit）でエラー 0 を確認する。

## 参照

- 実行手順・シナリオ詳細・しきい値・ボトルネック調査: [perf/README.md](../../../perf/README.md)
- ポート運用: [server-port-policy](../server-port-policy/SKILL.md) / [CLAUDE.md](../../../CLAUDE.md)
