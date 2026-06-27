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
# 書き込みはアプリ(IAM Task Role)経由、配信は CloudFront(OAC)経由に限定する。
# 注意: block_public_policy=true でも、下のバケットポリシーは「特定の CloudFront からのみ」
#       という条件付きで“公開ではない”ため適用できる（フロント用バケットと同じ考え方）。
resource "aws_s3_bucket_public_access_block" "images" {
  bucket                  = aws_s3_bucket.images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# --- バケットポリシー: CloudFront(OAC)からの読み取りのみ許可 ---
# 投稿/プロフィール画像を、バケットを非公開のまま CloudFront 経由で配信するため。
# 直リンクの S3 URL は引き続き 403（CloudFront 経由のみ許可）。
data "aws_iam_policy_document" "images_oac" {
  statement {
    sid       = "AllowCloudFrontServicePrincipalReadOnly"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.images.arn}/*"]

    # 許可する相手を CloudFront サービスに限定する。
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    # さらに「このディストリビューションからの呼び出しのみ」に絞る（他人の CloudFront を弾く）。
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "images" {
  bucket = aws_s3_bucket.images.id
  policy = data.aws_iam_policy_document.images_oac.json

  # パブリックアクセスブロックの適用が完了してからポリシーを当てる（適用順の安定化）。
  depends_on = [aws_s3_bucket_public_access_block.images]
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
