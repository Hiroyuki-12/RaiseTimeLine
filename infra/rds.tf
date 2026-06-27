# ==========================================================================
# RDS（マネージド PostgreSQL）
# ==========================================================================
# DB を自前で運用せず AWS に任せる。パッチ・バックアップ・監視が楽になる。

# --- DB サブネットグループ ---
# RDS をどのサブネットに置けるかを定義する。Private Subnet（2AZ）を指定し、
# 外部から到達できない場所に DB を配置する。2AZ 必要なのは RDS の冗長要件のため。
resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "${local.name_prefix}-db-subnet-group"
  }
}

# --- RDS インスタンス本体 ---
resource "aws_db_instance" "main" {
  identifier = "${local.name_prefix}-db"

  # エンジン: アプリのローカル開発と揃えて PostgreSQL 16 を使う。
  engine         = "postgres"
  engine_version = "16"

  # 無料枠対象のインスタンスクラス・ストレージ。
  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  # 保存データを暗号化する。ディスク盗難・スナップショット流出時にも中身を守るため。
  storage_encrypted = true

  # 接続情報。パスワードは tfvars 経由（コミットしない）。
  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  # 配置とアクセス制御。
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  # publicly_accessible=false: インターネットから直接 DB に接続させない（Fargate 経由のみ）。
  publicly_accessible = false
  # Single-AZ（multi_az=false）: 学習用途のためコストを抑える。本番は true を検討。
  multi_az = false

  # バックアップを 7 日保持する。誤操作や障害からの復旧用。
  backup_retention_period = 7

  # 学習用の運用設定:
  # - skip_final_snapshot=true: destroy 時に最終スナップショットを作らない（課題提出後に消しやすく）
  # - deletion_protection=false: terraform destroy で消せるようにする
  # 本番ではどちらも逆（スナップショット作成・削除保護ON）にすべき。
  skip_final_snapshot = true
  deletion_protection = false

  # マイナーバージョンの自動アップグレードを許可（セキュリティパッチ追従）。
  auto_minor_version_upgrade = true

  tags = {
    Name = "${local.name_prefix}-db"
  }
}
