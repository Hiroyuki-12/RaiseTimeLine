// ============================================================================
// post-create.js — 投稿作成の負荷テスト
//
// 目的: 書き込み系 API (POST /api/posts) の性能を測る。INSERT のコストと
//   トランザクションのスループットを確認する。画像なし (content のみ) で投稿する。
//
// メモ (multipart について):
//   POST /api/posts は consumes = multipart/form-data。k6 は body オブジェクトに
//   「ファイル部 (http.file)」が 1 つでも含まれると multipart/form-data で送信し、
//   無ければ application/x-www-form-urlencoded で送ってしまう (→ 415 になる)。
//   そこで、バックエンドが読まないダミーのファイル部 (_dummy) を 1 つ入れて
//   multipart 送信を強制する。image 部は付けないので S3 アップロードは発生しない
//   (= AWS 認証情報なしで動く)。content だけで投稿できる仕様を利用している。
//
// 負荷プロファイル: 10 VU / 1 分 (VUS / DURATION で上書き可能)。
// 実行: k6 run perf/k6/post-create.js
// ============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from './lib/config.js';
import { loginRandomSeedUser, authHeaders } from './lib/auth.js';
import { makeHandleSummary } from './lib/summary.js';

// 実行後に perf/results/post-create.md / .json へレポートを出力する
export const handleSummary = makeHandleSummary('post-create');

const PEAK_VUS = parseInt(__ENV.VUS || '10', 10);
const DURATION = __ENV.DURATION || '1m';

export const options = {
  vus: PEAK_VUS,
  duration: DURATION,
  thresholds: {
    // 書き込みは読み取りより重い想定。p95 700ms を初期ベースラインとする。
    'http_req_duration{name:POST /api/posts}': ['p(95)<700'],
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

  // multipart/form-data を強制するためのダミーファイル部 (_dummy) を含める。
  // content は投稿本文 (1〜280 文字)。
  const body = {
    content: `負荷テスト投稿 VU${__VU} iter${__ITER} ${Date.now()}`,
    // バックエンドが読まないダミー部。これがあると k6 は multipart で送る。
    _dummy: http.file('x', 'dummy.txt', 'text/plain'),
  };

  const res = http.post(`${BASE_URL}/api/posts`, body, {
    headers: authHeaders(token), // Content-Type は k6 が multipart で自動設定する
    tags: { name: 'POST /api/posts' },
  });

  check(res, {
    'create post 201': (r) => r.status === 201,
  });

  sleep(1); // 投稿の連投間隔を模す
}
