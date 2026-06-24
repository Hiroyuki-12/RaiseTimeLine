// ============================================================================
// timeline.js — 主シナリオ: タイムライン取得の負荷テスト
//
// 目的: 最も性能リスクの高い GET /api/posts を集中的に叩く。
//   このクエリは投稿ごとに「いいね数 / コメント数 / 自分のいいね有無」を
//   スカラーサブクエリで集計するため、データ量が増えると重くなりやすい。
//   全体タイムライン(all)とフォロー中タイムライン(following)の両方を測る。
//
// 負荷プロファイル: 0 → 50 VU まで増やし、維持し、減らす (合計 ~3 分)。
//   VU 数・時間は環境変数で上書き可能:
//     k6 run -e VUS=100 -e DURATION=5m perf/k6/timeline.js
//
// 実行: k6 run perf/k6/timeline.js
// ============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from './lib/config.js';
import { loginRandomSeedUser, authHeaders } from './lib/auth.js';
import { makeHandleSummary } from './lib/summary.js';

// 実行後に perf/results/timeline.md / .json へレポートを出力する
export const handleSummary = makeHandleSummary('timeline');

// 環境変数で上書き可能なピーク VU 数と維持時間
const PEAK_VUS = parseInt(__ENV.VUS || '50', 10);
const HOLD = __ENV.DURATION || '1m';

export const options = {
  // ramping-vus: 段階的に VU を増減させ、徐々に負荷を上げて挙動を観察する。
  stages: [
    { duration: '30s', target: PEAK_VUS }, // ウォームアップ (0→ピーク)
    { duration: HOLD, target: PEAK_VUS }, // ピーク維持 (定常負荷で計測)
    { duration: '30s', target: 0 }, // クールダウン (ピーク→0)
  ],
  thresholds: {
    // タイムラインは一覧表示の中核。p95 500ms 以内を初期ベースラインとする。
    // (この数値は初回計測の実測を見て README のチューニング指針に従い調整する)
    'http_req_duration{name:GET /api/posts (all)}': ['p(95)<500'],
    'http_req_duration{name:GET /api/posts (following)}': ['p(95)<500'],
    // 失敗率は 1% 未満を必須とする。
    http_req_failed: ['rate<0.01'],
  },
};

// VU 起動時に 1 回だけ呼ばれる。ログインして accessToken を確保する。
// 毎リクエストでログインすると認証 API に負荷が偏るため、VU ごとに 1 回だけ取得する。
// (テスト時間はアクセストークン有効期限 15 分以内に収める前提)
export function setup() {
  return {};
}

export default function () {
  // VU ごとに 1 回ログイン (k6 は default 関数を繰り返し呼ぶため、
  // __ITER === 0 のときだけログインしてトークンを使い回す)
  if (!__VU_TOKEN) {
    __VU_TOKEN = loginRandomSeedUser();
  }
  const token = __VU_TOKEN;
  check(token, { 'token acquired': (t) => t !== null });
  if (!token) {
    return;
  }
  const params = { headers: authHeaders(token) };

  // ランダムなページを取得 (常に 1 ページ目だけだとキャッシュ的に有利になりすぎる)
  const page = Math.floor(Math.random() * 5); // 0..4 ページ目
  const size = 20;

  // 全体タイムライン
  const all = http.get(`${BASE_URL}/api/posts?page=${page}&size=${size}&timeline=all`, {
    ...params,
    tags: { name: 'GET /api/posts (all)' },
  });
  check(all, { 'all timeline 200': (r) => r.status === 200 });

  // フォロー中タイムライン (follows を JOIN するため別クエリ)
  const following = http.get(
    `${BASE_URL}/api/posts?page=${page}&size=${size}&timeline=following`,
    {
      ...params,
      tags: { name: 'GET /api/posts (following)' },
    },
  );
  check(following, { 'following timeline 200': (r) => r.status === 200 });

  sleep(1); // 実ユーザーのスクロール間隔を模す
}

// VU ごとにトークンを保持するためのグローバル変数。
// k6 の VU は独立した JS コンテキストを持つため、これで VU 単位の状態を保てる。
let __VU_TOKEN = null;
