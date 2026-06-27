# ==========================================================================
# セキュリティグループ（仮想ファイアウォール）
# ==========================================================================
# 多層防御の考え方で「必要な通信だけ」を許可する。
# ALB → Fargate → RDS の一方向だけ通し、各層は直前の層からしか入れないようにする。

# --- ALB 用 SG ---
# インターネットからの HTTP(80) を受ける。
# ※ドメイン未取得のため ALB は HTTP。ユーザー⇄CloudFront 間が HTTPS なので
#   エンドユーザーの通信は暗号化される。CloudFront→ALB はカスタムヘッダ秘密値で保護する。
resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb-sg"
  description = "ALB: allow HTTP from anywhere (CloudFront origin)"
  vpc_id      = aws_vpc.main.id

  # 80 番を全開放する。ALB は CloudFront から呼ばれる入口なので 0.0.0.0/0 で受ける必要がある。
  # 「CloudFront 以外の直アクセス」は SG では防げないため、ALB のリスナールールで
  # カスタムヘッダ X-Origin-Verify を検証して弾く（alb.tf 参照）。
  ingress {
    description = "HTTP from internet (fronted by CloudFront)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # 後ろの Fargate へ転送するため、外向きは全許可。
  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-alb-sg"
  }
}

# --- Fargate 用 SG ---
# アプリ(8080)へは ALB からのみ許可する。公開IPを持つが、ここで入口を絞るため
# インターネットから直接 8080 に到達することはできない（公開IPでも安全）。
resource "aws_security_group" "fargate" {
  name        = "${local.name_prefix}-fargate-sg"
  description = "Fargate: allow 8080 only from ALB SG"
  vpc_id      = aws_vpc.main.id

  # 8080 への ingress は「ALB の SG から」だけに限定する（送信元を SG 参照で指定）。
  ingress {
    description     = "App port from ALB only"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # ECR からのイメージ取得・S3 への画像アップロード・CloudWatch へのログ送信・RDS 接続のため
  # 外向きは全許可（NAT を置かない代わりに IGW 経由で出る）。
  egress {
    description = "All outbound (ECR/S3/Logs/RDS)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-fargate-sg"
  }
}

# --- RDS 用 SG ---
# PostgreSQL(5432)へは Fargate からのみ許可する。これでアプリ以外からは DB に触れない。
resource "aws_security_group" "db" {
  name        = "${local.name_prefix}-db-sg"
  description = "RDS: allow 5432 only from Fargate SG"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from Fargate only"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.fargate.id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-db-sg"
  }
}
