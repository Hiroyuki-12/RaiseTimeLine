// ============================================================================
// browse.js — 閲覧系の混在シナリオ
//
// 目的: 実ユーザーの「眺める」行動を模す。タイムラインを開いて投稿を 1 件選び、
//   詳細とコメントを見て、ついでにユーザー検索する、という一連の読み取りを
//   まとめて負荷にかける。read 系 API の総合的なレスポンスを測る。
//
// 負荷プロファイル: 0 → 30 VU / 合計 ~2 分。
// 実行: k6 run perf/k6/browse.js
// ============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from './lib/config.js';
import { loginRandomSeedUser, authHeaders } from './lib/auth.js';
import { makeHandleSummary } from './lib/summary.js';

// 実行後に perf/results/browse.md / .json へレポートを出力する
export const handleSummary = makeHandleSummary('browse');

const PEAK_VUS = parseInt(__ENV.VUS || '30', 10);
const HOLD = __ENV.DURATION || '1m';

export const options = {
  stages: [
    { duration: '30s', target: PEAK_VUS },
    { duration: HOLD, target: PEAK_VUS },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // 閲覧系の各 API は p95 600ms 以内を初期ベースラインとする。
    'http_req_duration{name:GET /api/posts (all)}': ['p(95)<600'],
    'http_req_duration{name:GET /api/posts/{id}}': ['p(95)<600'],
    'http_req_duration{name:GET /api/posts/{id}/comments}': ['p(95)<600'],
    'http_req_duration{name:GET /api/users/search}': ['p(95)<600'],
    http_req_failed: ['rate<0.01'],
  },
};

let __VU_TOKEN = null;

export default function () {
  if (!__VU_TOKEN) {
    __VU_TOKEN = loginRandomSeedUser();
  }
  const token = __VU_TOKEN;
  check(token, { 'token acquired': (t) => t !== null });
  if (!token) {
    return;
  }
  const params = { headers: authHeaders(token) };

  // 1) タイムラインを開く (ランダムなページ)
  const page = Math.floor(Math.random() * 5);
  const timeline = http.get(`${BASE_URL}/api/posts?page=${page}&size=20&timeline=all`, {
    ...params,
    tags: { name: 'GET /api/posts (all)' },
  });
  check(timeline, { 'timeline 200': (r) => r.status === 200 });

  // 2) 一覧からランダムに 1 件選び、詳細とコメントを見る
  let postId = null;
  try {
    const posts = timeline.json();
    if (Array.isArray(posts) && posts.length > 0) {
      postId = posts[Math.floor(Math.random() * posts.length)].id;
    }
  } catch (e) {
    // パース失敗時は postId なしで進む
  }

  if (postId) {
    const detail = http.get(`${BASE_URL}/api/posts/${postId}`, {
      ...params,
      tags: { name: 'GET /api/posts/{id}' },
    });
    check(detail, { 'detail 200': (r) => r.status === 200 });

    const comments = http.get(`${BASE_URL}/api/posts/${postId}/comments`, {
      ...params,
      tags: { name: 'GET /api/posts/{id}/comments' },
    });
    check(comments, { 'comments 200': (r) => r.status === 200 });
  }

  // 3) ユーザー検索 (loaduser で前方一致 / 最大 20 件)
  const search = http.get(`${BASE_URL}/api/users/search?q=loaduser`, {
    ...params,
    tags: { name: 'GET /api/users/search' },
  });
  check(search, { 'search 200': (r) => r.status === 200 });

  sleep(1); // 閲覧操作の間隔を模す
}
