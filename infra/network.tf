# ==========================================================================
# ネットワーク（VPC / サブネット / インターネットゲートウェイ / ルート）
# ==========================================================================
# 設計の要点:
# - Public Subnet ×2: ALB と Fargate タスクを配置。IGW 経由で外部と通信できる。
# - Private Subnet ×2: RDS を配置。外部から到達できない（DB を守るため）。
# - NAT Gateway は置かない（約$32/月のコスト削減）。Fargate は Public に公開IP付きで置き、
#   ECR/S3/Logs への outbound を IGW 経由で確保する。代わりに Security Group で守る。

# --- VPC 本体 ---
# アプリ専用の隔離されたネットワーク空間。この中に全リソースを閉じ込める。
resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr
  # EC2/RDS が VPC 内 DNS 名で解決・名前付与できるようにする（RDS エンドポイント解決等で必要）。
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${local.name_prefix}-vpc"
  }
}

# --- インターネットゲートウェイ ---
# VPC をインターネットに接続する出入り口。Public Subnet の通信はここを通る。
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-igw"
  }
}

# --- Public Subnet（ALB / Fargate 用）×2 ---
# count で AZ 数ぶん作る。map_public_ip_on_launch=true で起動リソースに公開IPを付ける
# （Fargate が IGW 経由で ECR/S3 に出ていくために必要）。
resource "aws_subnet" "public" {
  count                   = length(var.public_subnet_cidrs)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name_prefix}-public-${var.azs[count.index]}"
    Tier = "public"
  }
}

# --- Private Subnet（RDS 用）×2 ---
# 公開IPを付けない。インターネットから直接到達できないため DB の保管に適する。
resource "aws_subnet" "private" {
  count             = length(var.private_subnet_cidrs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = var.azs[count.index]

  tags = {
    Name = "${local.name_prefix}-private-${var.azs[count.index]}"
    Tier = "private"
  }
}

# --- Public 用ルートテーブル ---
# 0.0.0.0/0（全ての外部宛て）を IGW に流す = インターネットに出られる設定。
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "${local.name_prefix}-public-rt"
  }
}

# Public Subnet 各々に上のルートテーブルを関連付ける。
resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# --- Private 用ルートテーブル ---
# 外部宛てルートを持たない（NAT を置かないため）。VPC 内の通信のみ可能。
# RDS は外部に出ていく必要がないので、これで十分かつ安全。
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-private-rt"
  }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}
