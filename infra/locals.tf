# ==========================================================================
# ローカル値（計算済みの共通値）
# ==========================================================================

locals {
  # リソース名の接頭辞。例: raisetimeline-prod-alb のように命名を統一する。
  name_prefix = "${var.project}-${var.env}"

  # backend のコンテナイメージ。変数で明示指定が無ければ ECR リポジトリの :latest を使う。
  # （別Issueで backend の Dockerfile を作り、このリポジトリに push する前提）
  backend_image = var.backend_image != "" ? var.backend_image : "${aws_ecr_repository.backend.repository_url}:latest"
}
