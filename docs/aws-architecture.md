# AWS インフラ構成

RaiseTimeLine を AWS にデプロイするためのインフラ構成と設計判断を記録する。
方針は **EC2 を使わない CloudFront + S3 + ECS Fargate + ALB + RDS** 構成。

実際の Terraform コードは `infra/` 配下、操作手順は [`infra/README.md`](../infra/README.md) を参照。
各サービスの役割を入門レベルで噛み砕いた解説は [`aws-architecture-learning-notes.md`](./aws-architecture-learning-notes.md)（学習メモ）にある。

> 設計変更の経緯: 当初は ALB + EC2 + RDS + S3 構成だったが、サーバー（EC2）を自前で管理しない方針に切り替え、
> フロントは S3 + CloudFront の静的配信、バックエンドは ECS Fargate に移行した。旧構成は Git 履歴を参照。

---

## 全体構成図

```
                          ┌──────────┐
                          │ ユーザー  │
                          └────┬─────┘
                               │ HTTPS
                               ▼
                ┌────────────────────────────────┐
                │   CloudFront（単一の入口 / CDN） │  ACM(us-east-1)
                └───────┬───────────────┬─────────┘
                  /（画面）│               │ /api/*（API）
                          ▼               ▼
                    ┌──────────┐    ┌──────────┐
                    │   S3     │    │   ALB    │  ACM(ap-northeast-1)
                    │ (React)  │    │ (HTTPS)  │  X-Origin-Verify 検証
                    │ 非公開/OAC│    └────┬─────┘
                    └──────────┘         │
                                         ▼
                                 ┌──────────────────┐
                                 │  ECS Fargate     │  Public Subnet ×2
                                 │  (Spring Boot)   │  0.25vCPU/0.5GB
                                 └───┬──────────┬───┘
                                     ▼          ▼
                                ┌────────┐  ┌──────────┐
                                │  RDS   │  │ S3(画像) │
                                │ (DB)   │  │          │
                                │Private │  └──────────┘
                                └────────┘
```

| サービス | 役割 |
|---|---|
| CloudFront | 単一の入口・CDN・HTTPS 終端。`/` は S3、`/api/*` は ALB へ振り分け |
| S3（フロント） | React ビルド成果物の静的配信（非公開 + OAC） |
| ALB | HTTPS 終端・Fargate への振り分け・ヘルスチェック・CloudFront 限定アクセス |
| ECS Fargate | Spring Boot コンテナの実行（EC2 を管理しない） |
| RDS | PostgreSQL（マネージド DB） |
| S3（画像） | 投稿・プロフィール画像の永続保存 |
| ECR / SSM / CloudWatch Logs | イメージ保管 / 機密値注入 / ログ集約 |

---

## 主要な設計判断

| トピック | 判断 | 理由 |
|---------|------|------|
| EC2 撤廃 | フロント=S3+CloudFront / バックエンド=Fargate | サーバー管理（OS パッチ・SSH・プロセス監視）をなくす。静的配信に常駐サーバーは不要、バックエンドはコンテナ実行で足りる |
| バックエンド実行基盤 | ECS Fargate | コンテナ運用の標準。Spring Boot の常駐 + Flyway 起動時マイグレーションと相性が良い（Lambda は冷起動・起動時処理が不向き） |
| 単一オリジン | CloudFront 一つに集約し `/api/*` を ALB へ | リフレッシュトークンが `SameSite=Lax` Cookie のため同一サイト必須。フロントは相対 `/api` で無改修、CORS も不要になる |
| ALB 採用 | あり | Fargate タスクの IP 変動を吸収（宛先固定）・HTTPS 終端・ヘルスチェックを担う |
| ALB 直アクセス遮断 | CloudFront のカスタムヘッダ `X-Origin-Verify` を検証 | SG だけでは ALB を 0.0.0.0/0 に開く必要があり防げない。ヘッダ秘密値で「CloudFront 経由のみ」を担保 |
| NAT Gateway 不採用 | Fargate を Public Subnet + 公開IP + SG 制限で運用 | NAT は約 $32/月。ingress を ALB SG のみに絞れば公開 IP でも安全 |
| DB | RDS db.t3.micro / Single-AZ / Private | 12ヶ月無料枠。マネージドでパッチ・バックアップを委譲 |
| 機密値 | SSM Parameter Store(SecureString) | DB パスワード・JWT シークレットをコンテナへ安全に注入。コミットしない |

---

## ネットワーク / セキュリティ

VPC `10.0.0.0/16`、2 AZ（ap-northeast-1a / 1c）。

| サブネット | 用途 |
|-----------|------|
| Public ×2 | ALB / Fargate タスク |
| Private ×2 | RDS（Subnet Group は 2AZ 要件のため 2 つ） |

### セキュリティグループ

| SG | Ingress | 補足 |
|----|---------|------|
| ALB SG | 443 を 0.0.0.0/0 | 直アクセスは ALB リスナールールで `X-Origin-Verify` を検証して遮断 |
| Fargate SG | 8080 を ALB SG からのみ | 公開 IP を持つが ingress を絞るため外部到達不可 |
| DB SG | 5432 を Fargate SG からのみ | — |

> NAT を置かない代わりに Fargate を Public Subnet に配置し、ECR/S3/Logs への outbound を IGW 経由で確保する。
> 本番要件が上がる場合は Private Subnet + VPC エンドポイントへ移行する。

---

## CloudFront

| 項目 | 内容 |
|------|------|
| オリジン1 | S3（OAC で非公開バケットを CloudFront からのみ読む） |
| オリジン2 | ALB（カスタムヘッダ `X-Origin-Verify: <秘密値>` を付与） |
| 既定ビヘイビア | `/` → S3。`redirect-to-https` |
| API ビヘイビア | `/api/*` → ALB。キャッシュ無効・全ヘッダ/Cookie 転送 |
| SPA フォールバック | 403/404 を `/index.html`(200) に置換し React Router に委譲 |
| 証明書 | ACM（us-east-1 必須）。独自ドメイン未取得なら `*.cloudfront.net` |

## ALB

| 項目 | 内容 |
|------|------|
| タイプ | Application Load Balancer（Internet-facing） |
| リスナー | HTTPS:443（ACM ap-northeast-1）。HTTP:80 → 443 リダイレクト |
| ターゲットグループ | target-type=ip（Fargate タスク IP を登録） |
| ヘルスチェック | `GET /actuator/health` |
| アクセス制限 | リスナールールで `X-Origin-Verify` 不一致を 403 |

## ECS / Fargate

| 項目 | 内容 |
|------|------|
| 起動タイプ | Fargate（0.25 vCPU / 0.5 GB、希望タスク数 1） |
| コンテナ | ECR の `raisetimeline-backend:latest`、8080 待受 |
| ログ | awslogs → CloudWatch Logs（アプリは ECS 形式 JSON 出力済み） |
| 環境変数 | DB 接続情報・S3 バケット名を注入 |
| 機密値 | DB パスワード・`APP_JWT_SECRET` を SSM(SecureString) から注入 |
| 権限 | Task Role=S3 読み書き / Execution Role=ECR pull + Logs + SSM 読み取り |

## RDS

| 項目 | 内容 |
|------|------|
| エンジン / インスタンス | PostgreSQL 16 / db.t3.micro |
| 配置 | Private Subnet、`publicly_accessible=false` |
| ストレージ | gp3 20GB・暗号化、Single-AZ |
| マイグレーション | Spring Boot 起動時に Flyway が自動適用 |

## S3

| バケット | 内容 |
|---------|------|
| フロント配信 | 非公開（パブリックアクセスブロック全有効）。CloudFront OAC 経由のみ。`frontend/dist` を sync |
| 画像 | `raisetimeline-images-{env}`。`posts/{post_id}/{uuid}.{ext}`。投稿削除時に DeleteObject |

---

## コスト目安（学習用）

| 項目 | 月額目安 |
|------|---------|
| ALB | 約 $16（無料枠外） |
| Fargate（0.25vCPU/0.5GB 常時1） | 約 $9 |
| RDS db.t3.micro / CloudFront / S3 / ECR | 無料枠内 |
| **合計** | **約 $25〜30** |

### 採用しなかったもの

- **EC2 / Nginx**（方針転換で撤廃）
- **NAT Gateway**（約 $32/月）→ Public Subnet + 公開IP + SG 制限で代替
- **VPC エンドポイント**（Interface 型は月額課金）→ 同上
- **App Runner / Lambda** → コンテナ運用の学習価値と Spring Boot との相性から Fargate を選択
- **Multi-AZ RDS / WAF** → 学習用途のため不要

---

## アプリ側に必要な改修（別 Issue で対応）

| # | 改修 | 理由 |
|---|------|------|
| 1 | backend に `Dockerfile` 追加 | Fargate 用イメージを ECR へ push する |
| 2 | `spring-boot-starter-actuator` 追加・`/actuator/health` を permitAll | ALB / ECS のヘルスチェック先 |
| 3 | `application.properties` の環境変数化（DB/JWT/S3） | 機密値を SSM/環境変数で注入 |
| 4 | リフレッシュ Cookie に本番時 `Secure` 付与 | HTTPS 配信のため必須 |
| 5 | `allowedOrigins` の環境変数化 | 単一オリジンで実質不要だが将来拡張に備える |
| 6 | フロント | 変更不要（相対 `/api` のまま動作） |

---

## デプロイフロー

```
[バックエンド]
  ./gradlew bootJar → docker build → ECR へ push
  → aws ecs update-service --force-new-deployment

[フロントエンド]
  npm run build → aws s3 sync dist/ s3://<front-bucket>
  → aws cloudfront create-invalidation
```

---

## tfstate / 機密値

- tfstate: S3 backend（バージョニング・暗号化・パブリックブロック）+ DynamoDB ロック。S3/DynamoDB は初回 CLI で手動作成（Terraform 管理外）。
- 機密値: RDS パスワード・JWT シークレット・CloudFront ヘッダ秘密値は SSM(SecureString)。`*.tfvars` / `*.tfstate*` は gitignore。AWS 認証情報は `~/.aws/credentials`。
