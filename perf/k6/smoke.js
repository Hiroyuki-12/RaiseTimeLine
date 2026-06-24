// ============================================================================
// smoke.js — 疎通確認シナリオ
//
// 目的: 本格的な負荷をかける前の「動作確認」。1 VU で主要 API を 1 回ずつ叩き、
//   ・バックエンドが起動しているか
//   ・シードユーザーでログインできるか (パスワードハッシュが正しいか)
//   ・各 GET が 200 を返すか
// を短時間で確認する。しきい値はゆるめ。
//
// 実行: k6 run perf/k6/smoke.js
// ============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from './lib/config.js';
import { loginRandomSeedUser, authHeaders } from './lib/auth.js';
import { makeHandleSummary } from './lib/summary.js';

// 実行後に perf/results/smoke.md / .json へレポートを出力する
export const handleSummary = makeHandleSummary('smoke');

export const options = {
  vus: 1, // 仮想ユーザー 1 人
  duration: '30s',
  thresholds: {
    // 疎通確認なので失敗ゼロを期待。1% でも失敗したら異常。
    http_req_failed: ['rate<0.01'],
    // p95 はゆるめ (本格計測ではない)。
    http_req_duration: ['p(95)<800'],
  },
};

export default function () {
  // 1) ログインして accessToken を取得
  const token = loginRandomSeedUser();
  check(token, { 'token acquired': (t) => t !== null });
  if (!token) {
    // ログインできない時点で以降のテストは無意味なので終了
    return;
  }
  const params = { headers: authHeaders(token) };

  // 2) タイムライン (全体) を 1 ページ取得
  const timeline = http.get(`${BASE_URL}/api/posts?page=0&size=20&timeline=all`, {
    ...params,
    tags: { name: 'GET /api/posts (all)' },
  });
  check(timeline, { 'timeline status 200': (r) => r.status === 200 });

  // 3) 取得できた投稿の 1 件目で「投稿詳細」と「コメント一覧」を確認
  let postId = null;
  try {
    const posts = timeline.json();
    if (Array.isArray(posts) && posts.length > 0) {
      postId = posts[0].id;
    }
  } catch (e) {
    // パースできない場合は postId なしのまま進む
  }

  if (postId) {
    const detail = http.get(`${BASE_URL}/api/posts/${postId}`, {
      ...params,
      tags: { name: 'GET /api/posts/{id}' },
    });
    check(detail, { 'post detail status 200': (r) => r.status === 200 });

    const comments = http.get(`${BASE_URL}/api/posts/${postId}/comments`, {
      ...params,
      tags: { name: 'GET /api/posts/{id}/comments' },
    });
    check(comments, { 'comments status 200': (r) => r.status === 200 });
  }

  // 4) ユーザー検索 (loaduser で前方一致)
  const search = http.get(`${BASE_URL}/api/users/search?q=loaduser`, {
    ...params,
    tags: { name: 'GET /api/users/search' },
  });
  check(search, { 'search status 200': (r) => r.status === 200 });

  sleep(1); // 連続アクセスの間隔 (実ユーザーの操作間隔を模す)
}
