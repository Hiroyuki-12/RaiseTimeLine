# 技術スタック

環境構築済みの技術と、確定しているバージョンを記載する。「(未導入)」と付記したものは要件として採用予定だが、本リポジトリにはまだ導入されていない。

## フロントエンド

| 分類 | 採用技術 | バージョン |
|------|---------|-----------|
| 言語 | TypeScript | 6.0.x |
| UI ライブラリ | React / React DOM | 19.2.x |
| ルーティング | react-router-dom | 7.15.x |
| ビルドツール | Vite | 8.0.x |
| Vite プラグイン | @vitejs/plugin-react | 6.0.x |
| スタイリング | プレーン CSS（`src/index.css` のグローバルリセット）+ React インラインスタイル | — |
| HTTP クライアント | Axios | 1.16.x |
| Lint | ESLint | 10.2.x |
| Lint (TS) | typescript-eslint | 8.58.x |
| Lint (React) | eslint-plugin-react-hooks / react-refresh | 7.1.x / 0.5.x |
| Lint (a11y) | eslint-plugin-jsx-a11y | 6.10.x |
| テスト | Vitest + React Testing Library + MSW | 4.1.x / 16.3.x / 2.14.x |

> Next.js は使用しない（SPA 構成）。
> Tailwind CSS は採用しておらず、スタイルはコンポーネント内のインラインスタイルと `src/index.css` のグローバルリセットで構成する。
> Prettier は導入しておらず、フォーマットは ESLint と Spotless（バックエンド）で担保する。

## バックエンド

| 分類 | 採用技術 | バージョン |
|------|---------|-----------|
| 言語 | Java (LTS) | 25 |
| フレームワーク | Spring Boot | 4.0.0 |
| ビルドツール | Gradle (Kotlin DSL) | 9.3.1 (Wrapper 同梱) |
| 依存管理プラグイン | io.spring.dependency-management | 1.1.7 |
| Web | spring-boot-starter-web | (Boot 4.0.0 に追従) |
| バリデーション | spring-boot-starter-validation (Jakarta Bean Validation) | (Boot 4.0.0 に追従) |
| 運用エンドポイント | spring-boot-starter-actuator（`/actuator/health` を ALB/ECS ヘルスチェックに使用） | (Boot 4.0.0 に追従) |
| ORM | MyBatis (mybatis-spring-boot-starter) | 4.0.0 |
| DB マイグレーション | Flyway (spring-boot-starter-flyway / flyway-database-postgresql) | (Boot 4.0.0 に追従) |
| JDBC ドライバ | org.postgresql:postgresql | (Boot 4.0.0 に追従) |
| 認証 | Spring Security + JWT (JJWT) | JJWT 0.12.6 |
| API ドキュメント | springdoc-openapi (Swagger UI / `/swagger-ui.html`) | 2.8.0 |
| AWS SDK | AWS SDK for Java v2 (S3 クライアント) | 2.25.23 |
| コードフォーマット | Spotless (google-java-format / AOSP) | プラグイン 7.0.4 |
| テスト | JUnit 5 + Spring Boot Test (Mockito 同梱) | (Boot 4.0.0 に追従) |
| 結合テスト | Testcontainers (PostgreSQL) | 1.20.4 |

## インフラ / ローカル実行

| 分類 | 採用技術 | バージョン |
|------|---------|-----------|
| RDB | PostgreSQL | 16 (alpine) |
| ローカル実行環境 | Docker Compose | — |

## AWS デプロイ

**EC2 を使わない CloudFront + S3 + ECS Fargate + ALB + RDS** 構成。

| 分類 | 採用技術 | 備考 |
|------|---------|------|
| IaC | Terraform | 1.6+ (S3 backend + DynamoDB ロック) |
| CDN / 配信 | CloudFront | フロント静的配信・`/api/*` を ALB へ・画像を S3 から配信（OAC で S3 非公開） |
| 静的ホスティング | S3（フロント `dist/`） | CloudFront オリジン（OAC 経由・直接公開しない） |
| コンピューティング | ECS Fargate (0.25 vCPU / 0.5 GB) | サーバーレスでコンテナ実行（EC2 不使用） |
| コンテナレジストリ | ECR | バックエンド Docker イメージの格納先 |
| ロードバランサー | ALB (Application Load Balancer) | CloudFront からのみ受け付ける（X-Origin-Verify ヘッダ検証） |
| マネージド DB | RDS db.t3.micro / PostgreSQL 16 | プライベートサブネット・12ヶ月無料 |
| 画像ストレージ | S3（投稿・プロフィール画像） | CloudFront + OAC で配信 |
| 機密値 | SSM Parameter Store | DB パスワード等をタスク定義へ注入 |
| アクセス権限 | IAM Role | ECS タスク → S3 / SSM アクセス用 |
| ネットワーク | VPC `10.0.0.0/16`（2AZ） | ALB→Fargate→RDS の多層セキュリティグループ |
| ランタイム (バックエンド) | Docker / Eclipse Temurin 25 JRE (linux/amd64) | Fargate 上で実行 |

詳細な構成・設計判断は [aws-architecture.md](aws-architecture.md) を参照。

## 補足

- Java は最新 LTS である Java 25 を採用。Spring Boot は最新メジャー 4.0.x を採用。
- React / Vite / TypeScript は TaskManagement と同一バージョンに統一（React 19 / Vite 8 / TypeScript 6）。スタイリングは Tailwind ではなくインラインスタイル + プレーン CSS を採用。
- ORM は自動 SQL 生成の Spring Data JPA ではなく、手書き SQL が明示的で学習しやすい MyBatis を採用。
- マイグレーションは Spring Boot との親和性から Flyway を採用。
- 画像アップロードは Spring Boot が AWS SDK v2 を使って直接 S3 へアップロードする。
- DB アクセスのテストは Testcontainers により実 PostgreSQL を立ち上げて検証する（導入済み）。
