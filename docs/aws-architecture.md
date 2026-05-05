# AWS インフラ構成

RaiseTimeLine を AWS にデプロイするためのインフラ構成と設計判断を記録する。
学習目的に加え、実務を想定した構成として **ALB + EC2 + RDS + S3** を採用する。

実際の Terraform コードは `infra/` 配下、操作手順は [`infra/README.md`](../infra/README.md) を参照。

---

## 全体構成図

```
                              [GitHub]
                                 │
                                 │ git clone / Releases (app.jar, frontend-dist.tar.gz)
                                 ▼
                          [EC2 user_data]

                         [ACM 証明書]
                              │
ユーザー ──HTTPS/443──▶ ┌─────────────────────────┐
                       │ ALB (Application LB)    │
                       │  ルーティングルール:      │
                       │  /        → TG (EC2)    │
                       │  /api/*   → TG (EC2)    │
                       └────────────┬────────────┘
                                    │
                    ┌───────────────────────────────────────┐
                    │ EC2 t2.micro (Public Subnet, AZ-a)    │
                    │  ├─ Nginx (80)                        │
                    │  │   ├─ /        → /var/www/html      │  React SPA
                    │  │   └─ /api/*  → 127.0.0.1:8080      │
                    │  └─ Spring Boot (8080) Docker         │  Java 25 + Boot 4
                    │  EBS gp3 8GB (暗号化)                 │
                    └──────────────┬────────────────────────┘
                                   │
                    ┌──────────────┴──────────────────────────┐
                    │                                          │
          ┌─────────────────────┐               ┌────────────────────────┐
          │ RDS db.t3.micro      │               │ S3 バケット             │
          │ (Private Subnets)   │               │ (投稿画像ストレージ)    │
          │ PostgreSQL 16       │               │                        │
          │ 20GB gp3 / Single-AZ│               │ posts/{id}/{uuid}.jpg  │
          └─────────────────────┘               └────────────────────────┘
```

---

## ネットワーク設計

VPC `10.0.0.0/16` 内に以下のサブネットを配置する。

| サブネット | AZ | 用途 |
|-----------|---|------|
| Public | ap-northeast-1a | EC2 (Nginx + Spring Boot) / ALB |
| Private A | ap-northeast-1a | RDS (主) |
| Private C | ap-northeast-1c | RDS Subnet Group の冗長 AZ 要件を満たすためのみ |

- Internet Gateway は Public サブネットからのみ到達可能
- Private サブネットには NAT Gateway を置かない（コスト削減）
- EC2 から S3 へのアクセスは IAM Role 経由（VPC エンドポイントは使用しない）

### セキュリティグループ

| SG | Ingress | Egress |
|----|---------|--------|
| ALB SG | 443 (HTTPS) を 0.0.0.0/0 | 全開放 |
| EC2 SG | 80 を ALB SG からのみ / 22 を `var.my_ip/32` のみ | 全開放 |
| DB SG | 5432 を EC2 SG からのみ | 全開放 |

8080 (Spring Boot) は SG で塞いでおり、Nginx 経由でしかバックエンドに到達できない。

---

## 採用リソース一覧

| AWS サービス | 用途 | 備考 |
|-------------|------|------|
| VPC / Subnet / IGW / Route Table | ネットワーク | — |
| Security Group | アクセス制御 | — |
| ALB (Application Load Balancer) | HTTPS ターミネーション・ルーティング | 月額約 $16〜 |
| ACM (AWS Certificate Manager) | SSL/TLS 証明書 | ALB にアタッチ・無料 |
| EC2 t2.micro | アプリ実行サーバー | 750h/月（12ヶ月無料枠） |
| EBS gp3 8GB (暗号化) | EC2 ルートディスク | 30GB/月（12ヶ月無料枠） |
| RDS db.t3.micro / PostgreSQL 16 | マネージド DB | 750h/月（12ヶ月無料枠） |
| RDS gp3 20GB (暗号化) | DB ストレージ | 20GB（12ヶ月無料枠） |
| S3 | 投稿画像の永続保存 | 5GB/月（12ヶ月無料枠） |
| IAM Role | EC2 → S3 アクセス権限 | — |
| S3 (tfstate) | Terraform 状態管理 | — |
| DynamoDB (tflock) | tfstate ロック | Always Free |

### 採用していないもの

- **NAT Gateway**（約 $30/月）→ プライベートサブネットからの outbound なし
- **Elastic IP** → ALB の DNS でアクセスする運用
- **CloudFront** → Nginx による静的配信で代替
- **Multi-AZ RDS** → Single-AZ で十分
- **WAF** → 学習用途のため不要

---

## ALB 設計

| 項目 | 内容 |
|------|------|
| タイプ | Application Load Balancer |
| スキーム | Internet-facing |
| リスナー | HTTPS:443（HTTP:80 は 443 へリダイレクト） |
| SSL 証明書 | ACM で発行（独自ドメイン必要） |
| ターゲットグループ | EC2 インスタンス（ポート 80） |

**ルーティングルール**（優先度順）:
1. パス `/api/*` → EC2（Spring Boot 経由の API リクエスト）
2. デフォルト `/` → EC2（Nginx による React SPA 配信）

---

## S3 バケット設計（画像ストレージ）

| 項目 | 内容 |
|------|------|
| バケット名 | `raisetimeline-images-{env}` |
| リージョン | ap-northeast-1 |
| アクセス制御 | パブリックアクセス制限（IAM Role 経由のみ書き込み） |
| 読み取り | 画像 URL に署名付き URL を使用、または特定プレフィックスをパブリック読み取り |
| ファイルパス | `posts/{post_id}/{uuid}.{拡張子}` |
| 削除タイミング | 投稿削除時に Spring Boot から S3 DeleteObject を呼び出し |

---

## IAM Role（EC2 用）

EC2 インスタンスに以下のポリシーをアタッチする：

```json
{
  "Effect": "Allow",
  "Action": [
    "s3:PutObject",
    "s3:GetObject",
    "s3:DeleteObject"
  ],
  "Resource": "arn:aws:s3:::raisetimeline-images-*/*"
}
```

---

## デプロイフロー

```
[ローカル Mac]
  ./gradlew bootJar  ─▶ backend/app.jar
  npm run build      ─▶ frontend/dist/  ─▶ frontend-dist.tar.gz
                              │
                              │ make release (gh release upload --clobber)
                              ▼
                       [GitHub Releases (tag: latest)]
                              │
                              │ make deploy / make redeploy
                              │ → terraform apply
                              ▼
                       [EC2 user_data]
                         curl で JAR / dist を取得 → 配置 → docker compose up
                                                  → nginx 設定 → systemctl start
```

- t2.micro では Gradle / npm のビルドは重すぎるため、ローカルでビルドして成果物のみ転送する

---

## tfstate 管理

- **S3 backend** にバージョニング・暗号化・パブリックアクセスブロックを有効化して保管
- **DynamoDB** で同時編集ロック
- S3 / DynamoDB は鶏卵問題回避のため **AWS CLI で初回手動作成**（Terraform 管理外）

---

## 機密値の扱い

| 値 | 保管場所 | コミット |
|----|---------|---------|
| AWS アクセスキー | `~/.aws/credentials` | しない |
| EC2 SSH 秘密鍵 | `~/.ssh/raisetimeline-key.pem` | しない（`*.pem` を gitignore） |
| 自宅 IP (`my_ip`) | `infra/terraform.tfvars` | しない（`*.tfvars` を gitignore） |
| RDS パスワード | `infra/terraform.tfvars` | しない |
| Terraform state | S3（暗号化） | しない（`*.tfstate*` を gitignore） |

---

## 設計判断のメモ

| トピック | 判断 | 理由 |
|---------|------|------|
| ALB 採用 | あり | HTTPS ターミネーション・ルーティングをマネージドに任せる |
| フロント配信 | EC2 上の Nginx | TaskManagement と同様の構成 |
| 画像ストレージ | S3 | EC2 ローカルに持つと再デプロイで消えるため |
| DB の場所 | RDS 切り出し | マネージド運用学習 + TaskManagement と同様の構成 |
| ビルド環境 | ローカル Mac で固定 | t2.micro では OOM するため |
| HTTPS | ALB + ACM | セキュアな通信を学習、独自ドメイン取得が前提 |
