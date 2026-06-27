# ==========================================================================
# ECS Fargate（コンテナ実行基盤）= バックエンドを EC2 無しで動かす場所
# ==========================================================================

# --- CloudWatch Logs ロググループ ---
# Fargate コンテナの標準出力（アプリの JSON ログ）を集約する先。
# retention で 30 日経過したログは自動削除し、保管コストを抑える。
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${local.name_prefix}-backend"
  retention_in_days = 30

  tags = {
    Name = "${local.name_prefix}-backend-logs"
  }
}

# --- ECS クラスタ ---
# タスクをまとめる論理的な箱。Fargate ではサーバー群の管理は不要で、箱だけ用意する。
resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  # コンテナ単位のメトリクス（CPU/メモリ）を CloudWatch で見られるようにする。
  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Name = "${local.name_prefix}-cluster"
  }
}

# --- タスク定義（コンテナの設計図） ---
# 「どのイメージを・どのCPU/メモリで・どんな環境変数で動かすか」を定義する。
resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name_prefix}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc" # Fargate 必須。タスクが ENI(=IP) を持つ
  cpu                      = var.backend_cpu
  memory                   = var.backend_memory

  # 裏方用ロール（ECR pull / Logs / SSM 読み取り）とアプリ本体用ロール（S3）を割り当てる。
  execution_role_arn = aws_iam_role.ecs_execution.arn
  task_role_arn      = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = local.backend_image
      essential = true

      # コンテナの 8080 を公開。ALB のターゲットグループがこのポートに振り分ける。
      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      # --- 平文で渡してよい設定（機密でないもの）は environment で渡す ---
      # application.properties をこれらの環境変数で上書きする想定（別Issueの backend 改修）。
      environment = [
        {
          name = "SPRING_DATASOURCE_URL"
          # RDS のエンドポイント:ポート/DB名 を組み立てる。
          value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${var.db_name}"
        },
        { name = "SPRING_DATASOURCE_USERNAME", value = var.db_username },
        { name = "AWS_S3_BUCKET_NAME", value = aws_s3_bucket.images.bucket },
        { name = "AWS_S3_REGION", value = var.region },
        # 画像配信のベース URL。images バケットは非公開(OAC)のため直リンク S3 URL は 403 になる。
        # backend はこの CloudFront ドメイン配下の URL を imageUrl として返す（/media/* で S3 へ届く）。
        {
          name  = "APP_MEDIA_BASE_URL"
          value = "https://${aws_cloudfront_distribution.main.domain_name}"
        },
        # CORS 許可オリジン。CloudFront 配下では同一オリジンでもブラウザが Origin ヘッダを送るため、
        # 本番ドメインを許可しないと Spring の CORS フィルタが 403 を返し、ログイン等が全て失敗する。
        {
          name  = "APP_CORS_ALLOWED_ORIGINS"
          value = "https://${aws_cloudfront_distribution.main.domain_name}"
        },
        # 本番プロファイル。Cookie の Secure 付与など環境差をここで切り替える想定。
        { name = "SPRING_PROFILES_ACTIVE", value = var.env },
      ]

      # --- 機密値は secrets で SSM から注入する（平文をタスク定義に残さない） ---
      secrets = [
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn },
        { name = "APP_JWT_SECRET", valueFrom = aws_ssm_parameter.jwt_secret.arn },
      ]

      # 標準出力を CloudWatch Logs へ送る設定。
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.backend.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "backend"
        }
      }
    }
  ])

  tags = {
    Name = "${local.name_prefix}-backend"
  }
}

# --- ECS サービス（タスクを「動かし続ける」管理者） ---
# 指定数のタスクを維持し、落ちたら自動で再起動、デプロイ時は入れ替えを行う。
resource "aws_ecs_service" "backend" {
  name            = "${local.name_prefix}-backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count
  launch_type     = "FARGATE"

  # ネットワーク設定: Public Subnet に配置し公開IPを付与（NAT 無しで ECR/S3 へ出るため）。
  # 公開IPでも SG(8080 は ALB のみ) で守られているため外部直アクセスはできない。
  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.fargate.id]
    assign_public_ip = true
  }

  # ALB のターゲットグループに、起動したタスクの IP を自動登録する。
  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = 8080
  }

  # タスク起動直後はアプリ初期化中でヘルスチェックに通らないため、猶予を与える。
  # Spring Boot の起動は Fargate(0.25 vCPU) 上で実測 約85秒かかる（コンテキスト初期化＋Flyway＋Tomcat）。
  # 猶予が起動時間より短いと、起動完了前に ALB ヘルスチェック失敗でタスクが kill され、
  # 新リビジョンへローリング更新できず（旧タスクが残り続ける）デプロイが収束しない。
  # そのため起動実測値に十分な余裕を持たせて 180 秒にする。
  health_check_grace_period_seconds = 180

  # ALB リスナールールが先に出来てからサービスを作る（登録先が無いと失敗するため）。
  depends_on = [aws_lb_listener_rule.forward_from_cloudfront]

  tags = {
    Name = "${local.name_prefix}-backend"
  }

  # デプロイ（イメージ更新）は CI/CLI 側で force-new-deployment するため、
  # Terraform は desired_count を管理しすぎないよう task_definition の差分のみ扱う。
  lifecycle {
    ignore_changes = [desired_count]
  }
}
