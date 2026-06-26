# RaiseTimeLine

[![CI](https://github.com/Hiroyuki-12/RaiseTimeLine/actions/workflows/ci.yml/badge.svg)](https://github.com/Hiroyuki-12/RaiseTimeLine/actions/workflows/ci.yml)

X（旧 Twitter）のタイムライン形式をベースにした学習目的の SNS アプリケーション。AI Engineer Course の学習課題として、要件定義から実装・AWS デプロイまでの一連の開発プロセスを経験することを目的に開発している。

投稿・コメント・いいね・フォローなど基本的な SNS 機能を複数ユーザーで利用できる SPA。インプレッション数表示・リツイートは持たないシンプルな設計が特徴。

## 目次

- [機能一覧](#機能一覧)
- [プロジェクト構成](#プロジェクト構成)
- [技術スタック](#技術スタック)
- [ローカル開発環境のセットアップ](#ローカル開発環境のセットアップ)
- [ポート運用ルール](#ポート運用ルール)
- [AWS デプロイ](#aws-デプロイ)
- [ドキュメント一覧](#ドキュメント一覧)
- [開発フロー](#開発フロー)

## 機能一覧

| 機能ID | 機能名 | 概要 |
|--------|--------|------|
| F-01 | ログイン・ユーザー登録 | メールアドレス＋パスワードで認証。JWT トークンを発行 |
| F-02 | タイムライン | 全体／フォロー中の 2 種類をタブ切り替えで表示。新着順 |
| F-03 | 投稿 | テキスト（280 文字）＋画像 1 枚の投稿・削除。画像は S3 保存 |
| F-04 | コメント | 投稿へのコメント投稿・削除。件数をタイムラインに表示 |
| F-05 | いいね | 投稿へのいいね追加・取消。1 ユーザー 1 投稿 1 回まで |
| F-06 | プロフィール | ユーザー情報の表示・編集。他ユーザーのプロフィールからフォロー操作が可能 |
| F-07 | フォロー/フォロワー | フォロー・アンフォロー。フォロー中／フォロワー一覧の表示 |
| F-08 | ユーザー検索 | ユーザー名によるインクリメンタル検索 |

**スコープ外（実装しない機能）:** インプレッション数・リツイート・DM・通知・ハッシュタグ

## プロジェクト構成

```
RaiseTimeLine/
├── backend/             Spring Boot 4.0 + Java 25 (REST API)
├── frontend/            React 19 + TypeScript + Vite + Tailwind CSS (SPA)
├── infra/               Terraform: AWS デプロイ用 IaC
├── compose.yaml         PostgreSQL 16 (ローカル開発用)
├── docs/                設計・要件・インフラ構成ドキュメント
├── CLAUDE.md            Claude Code 用の運用ルール
└── README.md
```

## 技術スタック

主要なバージョンの抜粋。詳細は [docs/tech-stack.md](docs/tech-stack.md) を参照。

### フロントエンド

- React 19.2 / React DOM 19.2
- TypeScript 6.0
- Vite 8.0
- Tailwind CSS 4.2
- Axios 1.15
- ESLint 9 / Prettier 3

### バックエンド

- Java 25 (LTS)
- Spring Boot 4.0.0
- Spring Data JPA (Hibernate) + Flyway
- Spring Security + JWT (JJWT)
- AWS SDK for Java v2 (S3 クライアント)
- Gradle 9.3.1 (Kotlin DSL, Wrapper 同梱)

### データベース / インフラ

- PostgreSQL 16
  - ローカル開発: Docker Compose
  - AWS デプロイ時: RDS db.t3.micro
- AWS デプロイ: ALB + EC2 t2.micro + Nginx + RDS + S3（詳細は [docs/aws-architecture.md](docs/aws-architecture.md)）
- IaC: Terraform 1.6+ (`infra/`)
- 成果物配布: GitHub Releases (`make release`)

## ローカル開発環境のセットアップ

### 前提

- Java 25（Gradle Toolchain により自動取得される場合あり）
- Node.js（Vite 8 / React 19 が動作するバージョン）
- Docker Desktop（Compose v2）

### 1. データベース起動 (port 5432)

```bash
docker compose up -d
```

`raisetimeline` データベースが起動する。スキーマは Flyway がバックエンド起動時に自動適用する。

### 2. バックエンド起動 (port 8080)

```bash
cd backend
./gradlew bootRun
```

REST API が `http://localhost:8080` で待ち受ける。

### 3. フロントエンド起動 (port 5173)

```bash
cd frontend
npm install
npm run dev
```

ブラウザで <http://localhost:5173> を開く。Vite dev server が `/api` を `http://localhost:8080` にプロキシするため、CORS 設定は不要。

### 主要コマンド

| 場所 | コマンド | 用途 |
|---|---|---|
| `frontend/` | `npm run dev` | 開発サーバ起動 |
| `frontend/` | `npm run build` | 型チェック + プロダクションビルド |
| `frontend/` | `npm run lint` | ESLint 実行 |
| `frontend/` | `npm run typecheck` | TypeScript 型チェック (`tsc -b --noEmit`) |
| `frontend/` | `npm run format` | Prettier 整形 |
| `backend/` | `./gradlew bootRun` | アプリ起動 |
| `backend/` | `./gradlew test` | テスト実行 |
| `backend/` | `./gradlew spotlessCheck` | コードフォーマット検査 (Spotless / google-java-format) |
| `backend/` | `./gradlew spotlessApply` | コードフォーマット自動修正 |
| `backend/` | `./gradlew build` | ビルド (Spotless / テスト含む) |
| ルート | `docker compose up -d` | PostgreSQL 起動 |
| ルート | `docker compose down` | PostgreSQL 停止 |

## ポート運用ルール

このプロジェクトで使用するポートは以下に固定する。**別ポートでの代替起動は禁止**（プロキシ設定や URL 前提が崩れて動作確認にならないため）。

| サービス | ポート |
|---|---|
| フロントエンド (Vite) | 5173 |
| バックエンド (Spring Boot) | 8080 |
| PostgreSQL | 5432 |

ポートが競合した場合は、`lsof -i :<port>` で占有プロセスを特定して停止し、本来のポートで起動し直す。詳細は [CLAUDE.md](CLAUDE.md#ポート運用ルール厳守) を参照。

## AWS デプロイ

学習目的で本アプリを AWS 上で稼働させるための IaC を `infra/` に用意している（Terraform）。
**ALB + EC2 + RDS + S3** 構成で、HTTPS 終端を ALB で行うシンプルな設計。

- 構成図 / 設計判断: [docs/aws-architecture.md](docs/aws-architecture.md)
- 初回セットアップ・運用コマンド: [infra/README.md](infra/README.md)

主な運用コマンド（リポジトリルートの `Makefile`）:

| コマンド | やること |
|---|---|
| `make release` | JAR + frontend dist を GitHub Releases に upload |
| `make deploy` | terraform apply（初回） |
| `make redeploy` | EC2 を作り直して新成果物を取得 |
| `make ssh` / `make logs` / `make status` | EC2 操作・状態確認 |
| `make destroy` | 全リソース削除（課題提出後の必須作業） |

> 12 ヶ月の無料枠を超えると EC2 / RDS が課金対象。**使わなくなったら必ず `make destroy`** すること。

## ドキュメント一覧

| 種別 | ドキュメント |
|---|---|
| 要件定義 | [docs/requirements.md](docs/requirements.md) |
| 非機能要件 | [docs/non-functional-requirements.md](docs/non-functional-requirements.md) |
| 機能一覧 | [docs/features.md](docs/features.md) |
| 画面一覧 | [docs/screen-list.md](docs/screen-list.md) |
| 画面設計書 | [docs/screen-design.md](docs/screen-design.md) |
| ER 図 / DB 設計 | [docs/er-diagram.md](docs/er-diagram.md) |
| AWS インフラ構成 | [docs/aws-architecture.md](docs/aws-architecture.md) |
| 技術スタック | [docs/tech-stack.md](docs/tech-stack.md) |
| 機能定義：認証 | [docs/features/auth.md](docs/features/auth.md) |
| 機能定義：タイムライン | [docs/features/timeline.md](docs/features/timeline.md) |
| 機能定義：投稿 | [docs/features/post.md](docs/features/post.md) |
| 機能定義：コメント | [docs/features/comment.md](docs/features/comment.md) |
| 機能定義：いいね | [docs/features/like.md](docs/features/like.md) |
| 機能定義：プロフィール | [docs/features/profile.md](docs/features/profile.md) |
| 機能定義：フォロー/フォロワー | [docs/features/follow.md](docs/features/follow.md) |
| 機能定義：ユーザー検索 | [docs/features/search.md](docs/features/search.md) |
| 運用ルール (Claude Code 用) | [CLAUDE.md](CLAUDE.md) |
| パフォーマンステスト (k6・手動実行) | [perf/README.md](perf/README.md) |

## 開発フロー

`main` への直接 push は禁止。すべて Issue → ブランチ → PR の流れで進める。

1. GitHub の Issue テンプレート（Feature / Bug / Task / Docs）から Issue を起票
2. `<type>/#<issue>-<slug>` 形式（例: `feat/#12-add-timeline`）でブランチを作成
3. 変更をコミット・push
4. PR を作成し、本文に `Closes #<issue>` を含める
5. レビューコメントを全解決した上で squash / rebase でマージ

詳細なルール（ブランチ命名、PR 規約、main ブランチ保護設定）は [CLAUDE.md](CLAUDE.md) を参照。
