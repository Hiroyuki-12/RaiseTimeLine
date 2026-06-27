# ==========================================================================
# Terraform / プロバイダのバージョン固定と tfstate バックエンド設定
# ==========================================================================
# バージョンを固定する理由: チームや CI で同じ挙動を再現できるようにするため。
# 固定しないと、実行する人の環境次第でプロバイダが勝手に上がり、差分や破壊が起きうる。

terraform {
  # Terraform 本体の必要バージョン。1.6 以降を要求する（S3 backend のロック改善等を利用）。
  required_version = ">= 1.6"

  required_providers {
    # AWS プロバイダ。5 系に固定（4→5 で破壊的変更があったため明示する）。
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    # ランダム値生成プロバイダ。ALB を守るカスタムヘッダの秘密値などに使う。
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  # --- tfstate（状態ファイル）の保管先: S3 + DynamoDB ---
  # state にはリソースの実体情報（場合により機密も）が入るため、ローカルに置かず
  # 暗号化された S3 に保管し、DynamoDB で同時実行ロックをかける。
  #
  # 注意: この S3 バケットと DynamoDB テーブルは「鶏卵問題」回避のため、
  #       Terraform 管理外として AWS CLI で初回手動作成する（手順は infra/README.md 参照）。
  #       未作成の状態でも `terraform validate -backend=false` は実行できる。
  backend "s3" {
    bucket         = "raisetimeline-tfstate"   # 手動作成する state 用バケット名
    key            = "infra/terraform.tfstate" # バケット内でのファイルパス
    region         = "ap-northeast-1"          # state バケットのリージョン
    dynamodb_table = "raisetimeline-tflock"    # ロック用 DynamoDB テーブル名
    encrypt        = true                      # state を暗号化して保存する
  }
}
