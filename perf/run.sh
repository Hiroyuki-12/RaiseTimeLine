#!/usr/bin/env bash
# ============================================================================
# perf/run.sh — k6 シナリオ実行ラッパー (Web ダッシュボード自動有効化)
#
# なぜラッパーが必要か:
#   k6 の Web ダッシュボード (時系列グラフ付きの「画面で見られる」レポート) は
#   スクリプトの options からは制御できず、環境変数でしか有効化できない。
#   毎回手で付け忘れないよう、ここで自動設定してから k6 を起動する。
#
# 使い方:
#   bash perf/run.sh <シナリオ名>       例) bash perf/run.sh timeline
#   シナリオ名は perf/k6/<name>.ts の <name> 部分 (smoke / timeline / post-create / browse)。
#
#   VUS / DURATION / BASE_URL などの上書きは環境変数で渡せる:
#     VUS=100 DURATION=5m bash perf/run.sh timeline
#
# 実行中:
#   ブラウザで http://127.0.0.1:5665 を開くと、p95 推移・リクエスト数・VU 数などの
#   時系列グラフをリアルタイムに確認できる。
# 終了後:
#   perf/results/<name>.html … グラフ付きレポート (ブラウザで開く)
#   perf/results/<name>.md   … 合否サマリ (handleSummary が生成)
#   perf/results/<name>.json … 生データ (差分比較用)
# ============================================================================

set -euo pipefail

# このスクリプトのあるディレクトリ (perf/) を基準にする。どこから呼んでも動くように。
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 第 1 引数 = シナリオ名。未指定なら使い方を表示して終了する。
NAME="${1:-}"
if [[ -z "$NAME" ]]; then
  echo "使い方: bash perf/run.sh <シナリオ名>" >&2
  echo "  シナリオ名: smoke / timeline / post-create / browse" >&2
  exit 1
fi

SCENARIO="$SCRIPT_DIR/k6/$NAME.ts"
# 指定シナリオが存在しなければ、タイプミスを早期に弾く。
if [[ ! -f "$SCENARIO" ]]; then
  echo "シナリオが見つかりません: $SCENARIO" >&2
  echo "  perf/k6/ にある .ts ファイル名 (拡張子なし) を指定してください。" >&2
  exit 1
fi

# レポート出力先 (results/) を用意する。
RESULTS_DIR="$SCRIPT_DIR/results"
mkdir -p "$RESULTS_DIR"

# Web ダッシュボードを有効化する環境変数。
# - K6_WEB_DASHBOARD: リアルタイム表示 (http://127.0.0.1:5665) を有効化。
# - K6_WEB_DASHBOARD_EXPORT: 終了時にグラフ付き HTML を書き出す先。
# - K6_WEB_DASHBOARD_PERIOD: 時系列グラフの集計粒度 (細かめにして変化を見やすく)。
export K6_WEB_DASHBOARD=true
export K6_WEB_DASHBOARD_EXPORT="$RESULTS_DIR/$NAME.html"
export K6_WEB_DASHBOARD_PERIOD="${K6_WEB_DASHBOARD_PERIOD:-2s}"

echo "▶ k6 run $NAME (Web ダッシュボード: http://127.0.0.1:5665)"
# 第 2 引数以降は k6 にそのまま渡す (例: -e BASE_URL=... などの追加オプション)。
# macOS の bash 3.2 では set -u 下で空の "$@" が unbound 扱いになるため、
# 追加引数の有無で分岐して安全に渡す。
shift || true
if [[ "$#" -gt 0 ]]; then
  k6 run "$@" "$SCENARIO"
else
  k6 run "$SCENARIO"
fi

echo "✔ レポート: $RESULTS_DIR/$NAME.html / .md / .json"
