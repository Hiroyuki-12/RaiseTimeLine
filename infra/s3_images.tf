# ==========================================================================
# S3（投稿・プロフィール画像の保管庫）
# ==========================================================================
# アプリ(Fargate)が Task Role 経由で put/get/delete する。一般公開はしない。

resource "aws_s3_bucket" "images" {
  bucket = var.images_bucket_name

  # force_destroy=true: バケット内に画像オブジェクト（バージョン含む）が残っていても
  # terraform destroy でバケットごと一括削除できるようにする。
  # 「apply → 動作確認 → すぐ destroy」という学習用途の運用を一発で通すため許容する。
  # 本番では誤削除でデータを失うリスクがあるため false にすべき設定。
  force_destroy = true

  tags = {
    Name = "${local.name_prefix}-images"
  }
}

# パブリックアクセスを全面ブロックする。
# 画像といえど直接バケットを公開すると、URL 総当たりや想定外参照のリスクがあるため、
# アクセスはアプリ(IAM)経由に限定する。
resource "aws_s3_bucket_public_access_block" "images" {
  bucket                  = aws_s3_bucket.images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# サーバーサイド暗号化を有効化（保存データを暗号化）。
resource "aws_s3_bucket_server_side_encryption_configuration" "images" {
  bucket = aws_s3_bucket.images.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# バージョニングを有効化。誤削除・誤上書きからの復旧用。
resource "aws_s3_bucket_versioning" "images" {
  bucket = aws_s3_bucket.images.id
  versioning_configuration {
    status = "Enabled"
  }
}
