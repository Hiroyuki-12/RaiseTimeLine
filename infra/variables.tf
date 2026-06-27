# ==========================================================================
# 入力変数の定義
# ==========================================================================
# 実際の値は terraform.tfvars（gitignore 対象・コミットしない）で渡す。
# サンプルは terraform.tfvars.example を参照。

# --- 基本情報 ---
variable "project" {
  description = "プロジェクト名。リソース名やタグの接頭辞に使う"
  type        = string
  default     = "raisetimeline"
}

variable "env" {
  description = "環境名（prod / stg など）。リソース名やタグに使い、環境ごとに分離する"
  type        = string
  default     = "prod"
}

variable "region" {
  description = "メインリージョン。ポリシー上 ap-northeast-1（東京）に固定"
  type        = string
  default     = "ap-northeast-1"
}

# --- ネットワーク ---
variable "vpc_cidr" {
  description = "VPC の CIDR ブロック"
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs" {
  description = "使用するアベイラビリティゾーン（2AZ。ALB と RDS Subnet Group の冗長要件を満たす）"
  type        = list(string)
  default     = ["ap-northeast-1a", "ap-northeast-1c"]
}

variable "public_subnet_cidrs" {
  description = "Public Subnet の CIDR（ALB / Fargate を配置）。azs と同じ順序・同じ個数"
  type        = list(string)
  default     = ["10.0.0.0/24", "10.0.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "Private Subnet の CIDR（RDS を配置）。azs と同じ順序・同じ個数"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
}

# --- RDS ---
variable "db_name" {
  description = "作成するデータベース名"
  type        = string
  default     = "raisetimeline"
}

variable "db_username" {
  description = "DB マスターユーザー名"
  type        = string
  default     = "raisetimeline"
}

variable "db_password" {
  description = "DB マスターパスワード。tfvars で渡し、コミットしない（SSM にも保管する）"
  type        = string
  sensitive   = true # ログや plan 出力に平文表示されないようにする
}

variable "db_instance_class" {
  description = "RDS インスタンスクラス。無料枠対象の db.t3.micro"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "RDS のストレージ容量(GB)。無料枠は 20GB まで"
  type        = number
  default     = 20
}

# --- アプリ（ECS Fargate） ---
variable "backend_image" {
  description = "ECS が起動する backend の Docker イメージ。別Issueで ECR に push 後、タグを指定する"
  type        = string
  default     = "" # 空なら ECR リポジトリの :latest を使う（locals で組み立て）
}

variable "backend_desired_count" {
  description = "起動し続ける Fargate タスク数。学習用は 1、冗長化するなら 2"
  type        = number
  default     = 1
}

variable "backend_cpu" {
  description = "タスクの CPU ユニット（256 = 0.25 vCPU）"
  type        = number
  default     = 256
}

variable "backend_memory" {
  description = "タスクのメモリ(MiB)（512 = 0.5 GB）"
  type        = number
  default     = 512
}

variable "jwt_secret" {
  description = "JWT 署名用シークレット（32文字以上）。tfvars で渡し、コミットしない（SSM に保管）"
  type        = string
  sensitive   = true
}

# --- 画像 S3 ---
variable "images_bucket_name" {
  description = "投稿・プロフィール画像を保存する S3 バケット名（全世界で一意である必要がある）"
  type        = string
  default     = "raisetimeline-images-prod"
}
