# ==========================================================================
# ローカル値（計算済みの共通値）
# ==========================================================================

# 実行中の AWS アカウント情報を取得する。S3 バケット名は「全世界で一意」である必要があるため、
# バケット名にアカウントID を付与して他者との衝突を避ける目的で使う。
data "aws_caller_identity" "current" {}

locals {
  # リソース名の接頭辞。例: raisetimeline-prod-alb のように命名を統一する。
  name_prefix = "${var.project}-${var.env}"

  # S3 バケット名の全世界一意化に使うアカウントID サフィックス。
  account_id = data.aws_caller_identity.current.account_id

  # backend のコンテナイメージ。変数で明示指定が無ければ ECR リポジトリの :latest を使う。
  # （別Issueで backend の Dockerfile を作り、このリポジトリに push する前提）
  backend_image = var.backend_image != "" ? var.backend_image : "${aws_ecr_repository.backend.repository_url}:latest"
}
