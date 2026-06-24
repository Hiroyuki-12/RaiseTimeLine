#!/usr/bin/env bash
# ============================================================================
# パフォーマンステスト用 シードデータ投入スクリプト
#
# 何をするか:
#   compose.yaml で起動しているローカル PostgreSQL に対し、seed.sql を流し込んで
#   大量のテストデータ (ユーザー / 投稿 / いいね / コメント / フォロー) を投入する。
#
# ⚠️ ローカル開発 DB 専用。seed.sql は対象テーブルを TRUNCATE する。本番禁止。
#
# 使い方:
#   bash perf/seed/run-seed.sh
#   N_USERS=1000 N_POSTS=100000 bash perf/seed/run-seed.sh   # 規模を変える例
# ============================================================================

# いずれかのコマンドが失敗したら即座に中断する (中途半端な状態で進めない)
set -euo pipefail

# ----------------------------------------------------------------------------
# DB 接続情報。compose.yaml の environment と一致させている。
# ポート 5432 はプロジェクトの固定ポート (CLAUDE.md のポート運用ルール)。
# 環境変数で上書きも可能。
# ----------------------------------------------------------------------------
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-raisetimeline}"
DB_USER="${DB_USER:-raisetimeline}"
DB_PASSWORD="${DB_PASSWORD:-password}"

# 投入規模 (未指定なら seed.sql 側の既定値が使われる)
N_USERS="${N_USERS:-500}"
N_POSTS="${N_POSTS:-50000}"
N_LIKES="${N_LIKES:-200000}"
N_COMMENTS="${N_COMMENTS:-100000}"
N_FOLLOWS="${N_FOLLOWS:-5000}"

# このスクリプト自身の場所を基準に seed.sql のパスを解決する
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED_SQL="${SCRIPT_DIR}/seed.sql"

echo "==> シード投入を開始します (host=${DB_HOST}:${DB_PORT} db=${DB_NAME})"

# PGPASSWORD でパスワードを渡す (対話プロンプトを出さないため)。
# -v でスクリプトに投入規模を渡す。
PGPASSWORD="${DB_PASSWORD}" psql \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d "${DB_NAME}" \
    -v n_users="${N_USERS}" \
    -v n_posts="${N_POSTS}" \
    -v n_likes="${N_LIKES}" \
    -v n_comments="${N_COMMENTS}" \
    -v n_follows="${N_FOLLOWS}" \
    -f "${SEED_SQL}"

echo "==> シード投入が完了しました"
