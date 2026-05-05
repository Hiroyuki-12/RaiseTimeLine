---
name: server-port-policy
description: RaiseTimeLine プロジェクトでサーバー（フロント / バックエンド / DB）を起動するときに必ず適用するポート運用ポリシー。ポート競合時に別ポートへ逃げず、占有プロセスを停止して規定ポートで起動し直すことを強制する。`npm run dev` / `./gradlew bootRun` / `docker compose up` 等のサーバー起動コマンドを実行する直前に必ず参照する。
---

# Server Port Policy (RaiseTimeLine)

## 目的

このリポジトリではフロント/バックエンド/DB のポートが固定されており、Vite dev server のプロキシ設定 (`/api → :8080`) や README の手順がそれに依存している。
別ポートで一時起動してしまうと、フロントから API が叩けず「動作確認したつもり」状態になってしまうため、**必ず規定ポートで起動する**。

## 規定ポート（変更禁止）

| サービス | ポート | 起動コマンド |
| --- | --- | --- |
| フロントエンド (Vite dev server) | `5173` | `cd frontend && npm run dev` |
| バックエンド (Spring Boot) | `8080` | `cd backend && ./gradlew bootRun` |
| PostgreSQL | `5432` | `docker compose up -d` |

## 起動前チェック手順

サーバー起動コマンドを実行する **前に** 必ず以下を行う:

1. 該当ポートを占有しているプロセスを確認

   ```bash
   lsof -i :5173 -i :8080 -i :5432 -P -n
   docker ps --format '{{.Names}} {{.Ports}}'
   ```

2. 占有プロセスがある場合の対処（**プロセスを止める**。別ポートには逃げない）

   - 一般プロセス: `kill <PID>`（応答しなければ `kill -9 <PID>`）
   - Gradle daemon / Spring Boot: `cd backend && ./gradlew --stop`
   - Docker コンテナ: `docker stop <container_name>`（例: `docker stop raisetimeline-db`）
   - Vite dev server: バックグラウンドタスクなら `TaskStop`、そうでなければ該当 PID を kill

3. 規定ポートで起動

4. 起動完了確認:
   - backend: `until curl -sf http://localhost:8080/actuator/health >/dev/null; do sleep 2; done`
   - frontend: `until curl -sf http://localhost:5173/ >/dev/null; do sleep 1; done`

## 禁止事項

以下は **ユーザーが明示的に指示しない限り絶対に行わない**:

- `npm run dev -- --port 5174` のように別ポートで起動する
- `application.properties` の `server.port` を一時的に書き換える
- `compose.yaml` のポートマッピングを書き換える
- ポート競合エラーをそのままにして「動作確認できなかった」と報告する
- 既存プロセスを残したまま「すでに動いているようなのでスキップ」する（古いビルドが動いている可能性があるため）

## 例外

ユーザーが明示的に「別ポートで起動して」と指示した場合のみ、その指示に従う。
その場合も Vite proxy・README 等への影響をユーザーに必ず告知する。

## 参照

このルールは [CLAUDE.md](../../../CLAUDE.md) の「ポート運用ルール」と同期している。片方を更新したら必ずもう片方も更新する。
