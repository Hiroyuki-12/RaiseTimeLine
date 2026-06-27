# ==========================================================================
# ECR（Elastic Container Registry）= Docker イメージの保管庫
# ==========================================================================
# ローカルでビルドした backend の Docker イメージをここに push し、
# ECS Fargate がここから pull して起動する。

resource "aws_ecr_repository" "backend" {
  name = "${local.name_prefix}-backend"

  # push のたびに同じタグ(:latest)を上書きできるようにする（MUTABLE）。
  # 学習用途で latest 運用するため。厳密な運用では IMMUTABLE + コミットハッシュtag が望ましい。
  image_tag_mutability = "MUTABLE"

  # push 時にイメージの脆弱性スキャンを自動実行する（既知の CVE を検出してくれる）。
  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${local.name_prefix}-backend"
  }
}

# --- 古いイメージの自動削除（ライフサイクルポリシー） ---
# latest 運用で溜まる過去イメージを放置するとストレージ課金が増えるため、
# 直近 10 個だけ残して古いものは自動削除する。
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 10 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
