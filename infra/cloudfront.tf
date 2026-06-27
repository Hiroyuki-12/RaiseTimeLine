# ==========================================================================
# CloudFront（単一の入口 / CDN / HTTPS 終端）
# ==========================================================================
# 1つの CloudFront に 2 つのオリジンをぶら下げ、URL のパスで振り分ける:
#   - 既定(/)     → S3（React 静的ファイル）
#   - /api/*      → ALB（バックエンド）
# こうして「フロントと API を同一ドメイン」にする。理由は docs/aws-architecture.md 参照
# （SameSite=Lax のリフレッシュ Cookie を成立させ、フロント無改修・CORS不要にするため）。

# --- OAC（Origin Access Control） ---
# CloudFront が S3 に署名付きでアクセスするための仕組み。
# これにより S3 を非公開のまま CloudFront だけに読ませられる。
resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${local.name_prefix}-frontend-oac"
  description                       = "OAC for frontend S3 bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always" # 常に署名する
  signing_protocol                  = "sigv4"
}

# --- マネージドキャッシュポリシーの参照 ---
# AWS が用意済みのポリシーを ID 直書きせず名前で引く。
# CachingOptimized: 静的アセット向け（よくキャッシュする）。
data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}
# CachingDisabled: API 向け（キャッシュしない）。
data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}
# AllViewer: リクエストの全ヘッダ/クエリ/Cookie をオリジンへ転送する（API に必要）。
data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewer"
}

# --- CloudFront ディストリビューション本体 ---
resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  comment             = "${local.name_prefix} single entry (S3 + ALB)"
  default_root_object = "index.html" # / にアクセスされたら index.html を返す

  # ===== オリジン1: フロント S3 =====
  origin {
    origin_id                = "s3-frontend"
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  # ===== オリジン2: バックエンド ALB =====
  origin {
    origin_id   = "alb-backend"
    domain_name = aws_lb.main.dns_name

    custom_origin_config {
      http_port  = 80
      https_port = 443
      # ドメイン未取得で ALB は HTTP のため、CloudFront→ALB は HTTP で接続する。
      # （ユーザー⇄CloudFront は HTTPS なのでエンドユーザー通信は暗号化される）
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    # ALB を「CloudFront 経由のみ」に限定するための合言葉ヘッダ。
    # ALB のリスナールール(alb.tf)がこの値を検証し、一致しなければ 403 で弾く。
    custom_header {
      name  = "X-Origin-Verify"
      value = random_password.origin_verify.result
    }
  }

  # ===== 既定ビヘイビア: / → S3（React 配信） =====
  default_cache_behavior {
    target_origin_id       = "s3-frontend"
    viewer_protocol_policy = "redirect-to-https" # http で来ても https に飛ばす
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
    compress               = true # gzip/brotli 圧縮で配信を高速化
  }

  # ===== APIビヘイビア: /api/* → ALB（キャッシュ無効・全転送） =====
  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "alb-backend"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
    compress                 = true
  }

  # ===== SPA フォールバック =====
  # React Router は /timeline などの URL を JS 側で処理する。S3 にはそのファイルが無いため
  # 403/404 が返る。それを index.html(200) に差し替えて、ルーティングを React に委ねる。
  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 10
  }
  custom_error_response {
    error_code            = 404
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 10
  }

  # 配信地域の制限はかけない（誰でもアクセス可）。
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # 証明書: 独自ドメイン未取得のため CloudFront 既定の証明書(*.cloudfront.net)を使う。
  # 独自ドメインを使う場合は acm_certificate_arn(us-east-1) と aliases を指定する。
  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = {
    Name = "${local.name_prefix}-cf"
  }
}
