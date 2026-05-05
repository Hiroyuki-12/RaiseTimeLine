# 技術スタック

環境構築済みの技術と、確定しているバージョンを記載する。「(未導入)」と付記したものは要件として採用予定だが、本リポジトリにはまだ導入されていない。

## フロントエンド

| 分類 | 採用技術 | バージョン |
|------|---------|-----------|
| 言語 | TypeScript | 6.0.x |
| UI ライブラリ | React / React DOM | 19.2.x |
| ビルドツール | Vite | 8.0.x |
| Vite プラグイン | @vitejs/plugin-react | 6.0.x |
| スタイリング | Tailwind CSS / @tailwindcss/vite | 4.2.x |
| HTTP クライアント | Axios | 1.15.x |
| Lint | ESLint | 9.14.x |
| Lint (TS) | @typescript-eslint/eslint-plugin / parser | 8.59.x |
| Lint (React) | eslint-plugin-react-hooks / react-refresh | 7.1.x / 0.5.x |
| Format | Prettier | 3.3.x |
| テスト | Vitest + React Testing Library | (未導入) |

> Next.js は使用しない（SPA 構成）。

## バックエンド

| 分類 | 採用技術 | バージョン |
|------|---------|-----------|
| 言語 | Java (LTS) | 25 |
| フレームワーク | Spring Boot | 4.0.0 |
| ビルドツール | Gradle (Kotlin DSL) | 9.3.1 (Wrapper 同梱) |
| 依存管理プラグイン | io.spring.dependency-management | 1.1.7 |
| Web | spring-boot-starter-web | (Boot 4.0.0 に追従) |
| バリデーション | spring-boot-starter-validation (Jakarta Bean Validation) | (Boot 4.0.0 に追従) |
| ORM | Spring Data JPA (Hibernate) | (Boot 4.0.0 に追従) |
| DB マイグレーション | Flyway (spring-boot-flyway / flyway-database-postgresql) | (Boot 4.0.0 に追従) |
| JDBC ドライバ | org.postgresql:postgresql | (Boot 4.0.0 に追従) |
| 認証 | Spring Security + JWT (JJWT) | (Boot 4.0.0 に追従) |
| AWS SDK | AWS SDK for Java v2 (S3 クライアント) | 2.x |
| テスト | JUnit 5 + Spring Boot Test | (Boot 4.0.0 に追従) |
| 結合テスト | Testcontainers (PostgreSQL) | (未導入) |

## インフラ / ローカル実行

| 分類 | 採用技術 | バージョン |
|------|---------|-----------|
| RDB | PostgreSQL | 16 (alpine) |
| ローカル実行環境 | Docker Compose | — |

## AWS デプロイ

| 分類 | 採用技術 | 備考 |
|------|---------|------|
| IaC | Terraform | 1.6+ (S3 backend + DynamoDB ロック) |
| コンピューティング | EC2 t2.micro (Amazon Linux 2023) | 12ヶ月無料 |
| ロードバランサー | ALB (Application Load Balancer) | HTTPS ターミネーション |
| SSL/TLS 証明書 | ACM (AWS Certificate Manager) | ALB にアタッチ |
| マネージド DB | RDS db.t3.micro / PostgreSQL 16 | 12ヶ月無料 |
| 画像ストレージ | S3 | 投稿画像の永続保存 |
| アクセス権限 | IAM Role | EC2 → S3 アクセス用 |
| リバースプロキシ / 静的配信 | Nginx (AL2023 dnf パッケージ) | EC2 同居 |
| ランタイム (バックエンド) | Docker / Eclipse Temurin 25 JRE | コンテナで実行 |
| 成果物配布 | GitHub Releases (タグ `latest`) | 公開リポジトリ・無料 |

詳細な構成・設計判断は [aws-architecture.md](aws-architecture.md) を参照。

## 補足

- Java は最新 LTS である Java 25 を採用。Spring Boot は最新メジャー 4.0.x を採用。
- React / Vite / Tailwind / TypeScript は TaskManagement と同一バージョンに統一（React 19 / Vite 8 / Tailwind 4 / TypeScript 6）。
- マイグレーションは Spring Boot との親和性から Flyway を採用。
- 画像アップロードは Spring Boot が AWS SDK v2 を使って直接 S3 へアップロードする。
- DB アクセスのテストは Testcontainers により実 PostgreSQL を立ち上げて検証する方針（導入予定）。
