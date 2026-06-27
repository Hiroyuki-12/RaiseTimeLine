# ==========================================================================
# IAM ロール（ECS タスク用の権限）
# ==========================================================================
# ロールは 2 種類ある。役割が違うので混同しないこと。
# - Execution Role: ECS の「裏方」がコンテナを起動するための権限
#                   （ECR から pull / Logs へ書き込み / SSM の機密値を読む）
# - Task Role     : 起動した「アプリ本体」が AWS API を呼ぶための権限（このアプリでは S3 画像操作）

# --- ECS タスクが assume(引き受け)できることを定義する信頼ポリシー ---
# ecs-tasks.amazonaws.com（ECS タスク）だけがこのロールを使えるようにする。
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# --------------------------------------------------------------------------
# Execution Role（ECS の裏方用）
# --------------------------------------------------------------------------
resource "aws_iam_role" "ecs_execution" {
  name               = "${local.name_prefix}-ecs-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json

  tags = {
    Name = "${local.name_prefix}-ecs-execution-role"
  }
}

# AWS マネージドポリシー。ECR からの pull と CloudWatch Logs への書き込みに必要な
# 標準権限が入っている。自前で書かず公式のものを使うのが安全。
resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# タスク定義の secrets で SSM の機密値を注入するため、該当パラメータの読み取り権限を追加する。
# 対象を「このプロジェクト/環境のパラメータ」に限定し、他の機密は読めないようにする（最小権限）。
data "aws_iam_policy_document" "ecs_execution_ssm" {
  statement {
    sid     = "ReadAppSecrets"
    actions = ["ssm:GetParameters"]
    resources = [
      aws_ssm_parameter.db_password.arn,
      aws_ssm_parameter.jwt_secret.arn,
      aws_ssm_parameter.origin_verify.arn,
    ]
  }
}

resource "aws_iam_role_policy" "ecs_execution_ssm" {
  name   = "${local.name_prefix}-ecs-execution-ssm"
  role   = aws_iam_role.ecs_execution.id
  policy = data.aws_iam_policy_document.ecs_execution_ssm.json
}

# --------------------------------------------------------------------------
# Task Role（アプリ本体用）
# --------------------------------------------------------------------------
resource "aws_iam_role" "ecs_task" {
  name               = "${local.name_prefix}-ecs-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json

  tags = {
    Name = "${local.name_prefix}-ecs-task-role"
  }
}

# アプリが画像 S3 バケットに対して put/get/delete できる権限。
# 対象をこのバケット配下のオブジェクトに限定する（他バケットは触れない）。
data "aws_iam_policy_document" "ecs_task_s3" {
  statement {
    sid = "ImageBucketObjectRW"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:DeleteObject",
    ]
    resources = ["${aws_s3_bucket.images.arn}/*"]
  }
}

resource "aws_iam_role_policy" "ecs_task_s3" {
  name   = "${local.name_prefix}-ecs-task-s3"
  role   = aws_iam_role.ecs_task.id
  policy = data.aws_iam_policy_document.ecs_task_s3.json
}
