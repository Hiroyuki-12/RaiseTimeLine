# ==========================================================================
# SSM Parameter Store = 機密値（パスワード等）の金庫
# ==========================================================================
# DB パスワードや JWT シークレットをタスク定義に平文で書くと、
# コンソールや state から漏れる恐れがある。SecureString で暗号化保管し、
# ECS タスク起動時に「参照」だけして環境変数に注入する（ecs.tf の secrets 参照）。

# --- DB パスワード ---
resource "aws_ssm_parameter" "db_password" {
  name = "/${var.project}/${var.env}/db_password"
  # SecureString = AWS 管理キーで暗号化して保存する。平文では保存されない。
  type        = "SecureString"
  value       = var.db_password
  description = "RDS master password for ${local.name_prefix}"

  tags = {
    Name = "${local.name_prefix}-db-password"
  }
}

# --- JWT 署名シークレット ---
resource "aws_ssm_parameter" "jwt_secret" {
  name        = "/${var.project}/${var.env}/jwt_secret"
  type        = "SecureString"
  value       = var.jwt_secret
  description = "JWT signing secret for ${local.name_prefix}"

  tags = {
    Name = "${local.name_prefix}-jwt-secret"
  }
}

# --- CloudFront → ALB を守るカスタムヘッダの秘密値 ---
# CloudFront だけがこの値をヘッダ(X-Origin-Verify)に付け、ALB がその値を検証する。
# 値を知らない第三者は ALB の URL を直接知っても API を叩けない。
# random_password で自動生成し、人が知らなくても運用できるようにする。
resource "random_password" "origin_verify" {
  length  = 32
  special = false # ヘッダ値に使うため記号は避け、英数字のみにする
}

resource "aws_ssm_parameter" "origin_verify" {
  name        = "/${var.project}/${var.env}/origin_verify"
  type        = "SecureString"
  value       = random_password.origin_verify.result
  description = "Shared secret header value between CloudFront and ALB"

  tags = {
    Name = "${local.name_prefix}-origin-verify"
  }
}
