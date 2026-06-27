# ==========================================================================
# ALB（Application Load Balancer）= バックエンドの受付・振り分け係
# ==========================================================================
# 役割:
# - CloudFront から来た /api/* リクエストを受け取り、生きている Fargate タスクへ振り分ける
# - Fargate タスクは IP が変わるため、ALB が「変わらない宛先」として吸収する
# - ヘルスチェックで死んだタスクを外す
# - カスタムヘッダ検証で「CloudFront 経由のみ」を担保する

# --- ALB 本体 ---
resource "aws_lb" "main" {
  name               = "${local.name_prefix}-alb"
  internal           = false # インターネット向け（CloudFront から到達させる）
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id # Public Subnet（2AZ）に配置

  tags = {
    Name = "${local.name_prefix}-alb"
  }
}

# --- ターゲットグループ ---
# ALB が振り分ける先のグループ。Fargate は「IP 単位」で登録されるため target_type=ip。
resource "aws_lb_target_group" "backend" {
  name        = "${local.name_prefix}-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  # ヘルスチェック: Spring Boot Actuator の /actuator/health を叩いて生死を判定する。
  # （別Issueで backend に actuator を追加する前提。未追加だと unhealthy になる点に注意）
  health_check {
    enabled             = true
    path                = "/actuator/health"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30 # 30秒ごとにチェック
    timeout             = 5  # 5秒で応答が無ければ失敗
    healthy_threshold   = 2  # 2回連続成功で healthy
    unhealthy_threshold = 3  # 3回連続失敗で unhealthy（振り分け対象から外す）
  }

  tags = {
    Name = "${local.name_prefix}-tg"
  }
}

# --- リスナー（HTTP:80） ---
# ドメイン未取得のため ALB は HTTP で受ける。
# ユーザー⇄CloudFront 間が HTTPS なのでエンドユーザー通信は暗号化される。
# デフォルトアクションは「403 で拒否」にしておき、正規ルート（次のルール）だけ通す。
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # 既定動作: カスタムヘッダ検証を通らないリクエストは固定レスポンス 403 で拒否する。
  # これにより ALB の DNS を直接叩く第三者を遮断する。
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Forbidden"
      status_code  = "403"
    }
  }
}

# --- リスナールール: CloudFront 経由のみ転送 ---
# CloudFront が付与する X-Origin-Verify ヘッダの値が、SSM で生成した秘密値と一致する
# リクエストだけを Fargate(ターゲットグループ)へ転送する。
# 一致しなければ上の default_action(403) に落ちる = 直アクセス遮断。
resource "aws_lb_listener_rule" "forward_from_cloudfront" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }

  condition {
    http_header {
      http_header_name = "X-Origin-Verify"
      values           = [random_password.origin_verify.result]
    }
  }
}
