# ==========================================================================
# S3（React フロントエンドの静的ファイル配信バケット）
# ==========================================================================
# npm run build の成果物(dist)を置く。直接公開はせず、CloudFront(OAC)経由でのみ読ませる。

resource "aws_s3_bucket" "frontend" {
  # フロント用バケット名。画像バケットと別物。全世界で一意にするため接頭辞＋アカウントID を付ける。
  bucket = "${local.name_prefix}-frontend-${local.account_id}"

  # force_destroy=true: バケット内にオブジェクト（dist のファイル群）が残っていても
  # terraform destroy でバケットごと一括削除できるようにする。
  # 「apply → 動作確認 → すぐ destroy」という学習用途の運用を一発で通すため許容する。
  # 本番では誤削除でデータを失うリスクがあるため false にすべき設定。
  force_destroy = true

  tags = {
    Name = "${local.name_prefix}-frontend"
  }
}

# パブリックアクセスを全ブロック。配信は CloudFront 経由のみにする（直アクセスさせない）。
resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 暗号化。
resource "aws_s3_bucket_server_side_encryption_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# --- バケットポリシー: CloudFront(OAC)からの読み取りのみ許可 ---
# 「この CloudFront ディストリビューションからの GetObject だけ許す」と条件付けする。
# これにより非公開バケットを保ちつつ CloudFront だけが配信できる。
data "aws_iam_policy_document" "frontend_oac" {
  statement {
    sid       = "AllowCloudFrontServicePrincipalReadOnly"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]

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

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = data.aws_iam_policy_document.frontend_oac.json
}
