# ==========================================================================
# 出力値（apply 後に表示され、デプロイ作業で使う情報）
# ==========================================================================

output "cloudfront_domain" {
  description = "アプリの公開 URL（ここにブラウザでアクセスする）"
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "cloudfront_distribution_id" {
  description = "デプロイ時のキャッシュ無効化(invalidation)で使う CloudFront ID"
  value       = aws_cloudfront_distribution.main.id
}

output "ecr_repository_url" {
  description = "backend イメージの push 先（docker push する URL）"
  value       = aws_ecr_repository.backend.repository_url
}

output "frontend_bucket" {
  description = "フロント成果物の同期先 S3 バケット（aws s3 sync の宛先）"
  value       = aws_s3_bucket.frontend.bucket
}

output "images_bucket" {
  description = "画像保存用 S3 バケット名"
  value       = aws_s3_bucket.images.bucket
}

output "alb_dns_name" {
  description = "ALB の DNS 名（CloudFront のオリジン。直アクセスはヘッダ検証で 403）"
  value       = aws_lb.main.dns_name
}

output "rds_endpoint" {
  description = "RDS の接続エンドポイント（VPC 内からのみ到達可能）"
  value       = aws_db_instance.main.address
}

output "ecs_cluster_name" {
  description = "ECS クラスタ名（force-new-deployment で使う）"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS サービス名（force-new-deployment で使う）"
  value       = aws_ecs_service.backend.name
}
