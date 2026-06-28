# infra/ — AWS インフラ（Terraform）

RaiseTimeLine を **EC2 を使わずに** AWS で動かすための IaC。
構成・設計判断は [`../docs/aws-architecture.md`](../docs/aws-architecture.md)、入門解説は
[`../docs/aws-architecture-learning-notes.md`](../docs/aws-architecture-learning-notes.md) を参照。

構成: **CloudFront + S3（フロント）+ ECS Fargate + ALB + RDS + S3（画像）+ ECR + SSM**

---

## 前提

- Terraform 1.6 以上
- AWS CLI 設定済み（`aws configure` で認証情報を登録。キーはコミットしない）
- リージョンは ap-northeast-1（東京）
- 独自ドメインなし運用: フロントは `*.cloudfront.net`、CloudFront→ALB はオリジン HTTP

---

## ファイル構成

| ファイル | 内容 |
|---|---|
| `versions.tf` | Terraform / プロバイダのバージョン固定・tfstate(S3)バックエンド |
| `providers.tf` | AWS プロバイダ・共通タグ |
| `variables.tf` / `locals.tf` | 入力変数・計算値 |
| `network.tf` | VPC / Subnet / IGW / Route |
| `security_groups.tf` | ALB / Fargate / RDS の SG |
| `ecr.tf` | backend イメージの保管庫 |
| `ssm.tf` | 機密値（DBパス・JWT・オリジン検証ヘッダ） |
| `rds.tf` | PostgreSQL（db.t3.micro / Private） |
| `iam.tf` | ECS Execution Role / Task Role |
| `alb.tf` | ALB・ターゲットグループ・ヘッダ検証ルール |
| `ecs.tf` | クラスタ・タスク定義・サービス・Logs |
| `s3_images.tf` / `s3_frontend.tf` | 画像 / フロント配信バケット |
| `cloudfront.tf` | 単一入口（OAC・2オリジン・SPAフォールバック） |
| `outputs.tf` | URL・各種名前の出力 |

---

## 初回セットアップ

### 1. tfstate 用の S3 / DynamoDB を手動作成（鶏卵問題の回避）

`versions.tf` の backend が参照する保管先を、Terraform 管理外として先に作る。

```bash
# state 保管バケット（バージョニング・暗号化）
aws s3api create-bucket \
  --bucket raisetimeline-tfstate \
  --region ap-northeast-1 \
  --create-bucket-configuration LocationConstraint=ap-northeast-1
aws s3api put-bucket-versioning \
  --bucket raisetimeline-tfstate \
  --versioning-configuration Status=Enabled

# state ロック用 DynamoDB テーブル
aws dynamodb create-table \
  --table-name raisetimeline-tflock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region ap-northeast-1
```

### 2. tfvars を用意（機密値）

```bash
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars を編集して db_password / jwt_secret を設定する
# ※ terraform.tfvars はコミットしない（.gitignore 済み）
```

### 3. 初期化

```bash
terraform init
```

---

## コードチェック（apply せず検証だけ）

実リソースを作らず、コードの妥当性だけ確認する場合:

```bash
terraform fmt -check     # 整形チェック
terraform validate       # 構文・参照の検証
# backend 未作成の状態で検証だけしたいとき:
terraform init -backend=false && terraform validate
```

---

## デプロイ

```bash
terraform plan    # 変更内容の確認
terraform apply   # 実リソース作成（課金が発生する）
```

### アプリのデプロイ（apply 後）

```bash
# 出力値を取得
ECR=$(terraform output -raw ecr_repository_url)
DIST=$(terraform output -raw cloudfront_distribution_id)
FRONT=$(terraform output -raw frontend_bucket)
CLUSTER=$(terraform output -raw ecs_cluster_name)
SERVICE=$(terraform output -raw ecs_service_name)

# --- バックエンド: ビルド → ECR push → サービス更新 ---
# （backend に Dockerfile を追加する別Issue 完了後に実行できる）
cd ../backend
./gradlew bootJar
aws ecr get-login-password --region ap-northeast-1 \
  | docker login --username AWS --password-stdin "${ECR%/*}"
docker build -t "$ECR:latest" .
docker push "$ECR:latest"
aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --force-new-deployment

# --- フロントエンド: ビルド → S3 同期 → キャッシュ無効化 ---
cd ../frontend
npm run build
aws s3 sync dist/ "s3://$FRONT" --delete
aws cloudfront create-invalidation --distribution-id "$DIST" --paths "/*"
```

公開 URL は `terraform output cloudfront_domain` で確認できる。

---

## 後片付け（課金停止）

```bash
terraform destroy
```

> ALB と Fargate は無料枠対象外（合計 約 $25〜30/月）。RDS/CloudFront/S3 は 12ヶ月無料枠内。
> **使わなくなったら必ず `terraform destroy`** すること。
> （tfstate 用の S3/DynamoDB は Terraform 管理外なので、不要なら手動で削除する）

---

## 注意点

- ALB のヘルスチェックは `/actuator/health` を叩く。backend には Spring Boot Actuator が
  追加済み（`build.gradle` の `spring-boot-starter-actuator`）で、DB 接続状態を含めて UP/DOWN を返す。
- `application.properties` は環境変数で上書きできるよう対応済み（`${ENV_VAR:default}` 形式）。
  タスク定義は `SPRING_DATASOURCE_URL` 等の環境変数と SSM 機密値を注入する前提で書かれている。
- backend を更新したら、イメージを rebuild & ECR push 後に
  `aws ecs update-service --force-new-deployment` でローリング更新する（`terraform apply` は不要）。
