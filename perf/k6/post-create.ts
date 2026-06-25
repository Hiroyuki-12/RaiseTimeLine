// ============================================================================
// post-create.ts — 投稿作成の負荷テスト
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
// データの自動クリーンアップについて:
//   このシナリオだけが実データを DB に INSERT する。放置すると負荷テスト投稿が
//   溜まり続けるため、各イテレーションで「作成 → 直後に削除」までを 1 セットにし、
//   テスト終了後に残骸を残さない。DELETE /api/posts/{id} は投稿者本人のみ実行できる
//   認可制御があるが、ここでは作成した本人 (同じ VU のトークン) で削除するので通る。
//   k6 の teardown は VU が作成した ID を受け取れない制約があるため、この
//   「イテレーション内即時削除」が最も確実なクリーンアップ手段になる。
//   作成レイテンシ (p95 しきい値) は tags:{name:'POST /api/posts'} で別集計されるため、
//   削除リクエストを混ぜても作成性能の計測は汚れない。
//
// 負荷プロファイル: 10 VU / 1 分 (VUS / DURATION で上書き可能)。
// 実行: bash perf/run.sh post-create  (または k6 run perf/k6/post-create.ts)
// ============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Options } from 'k6/options';
import { BASE_URL } from './lib/config.ts';
import { loginRandomSeedUser, authHeaders } from './lib/auth.ts';
import { makeHandleSummary } from './lib/summary.ts';

// 実行後に perf/results/post-create.md / .json へレポートを出力する
export const handleSummary = makeHandleSummary('post-create');

const PEAK_VUS = parseInt(__ENV.VUS || '10', 10);
const DURATION = __ENV.DURATION || '1m';

export const options: Options = {
  vus: PEAK_VUS,
  duration: DURATION,
  thresholds: {
    // 書き込みは読み取りより重い想定。p95 700ms を初期ベースラインとする。
    // 削除は別タグ (DELETE /api/posts/{id}) なので、この作成のしきい値には影響しない。
    'http_req_duration{name:POST /api/posts}': ['p(95)<700'],
    http_req_failed: ['rate<0.01'],
  },
};

let __VU_TOKEN: string | null = null;

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

  const created = check(res, {
    'create post 201': (r) => r.status === 201,
  });

  // 作成に成功したら、その投稿をすぐに削除して DB に残骸を残さない。
  if (created) {
    // PostResponse の id (Long) を取り出す。JSONValue 型なので投稿 DTO として扱う。
    const postId = (res.json() as { id: number }).id;
    const del = http.del(`${BASE_URL}/api/posts/${postId}`, null, {
      headers: authHeaders(token),
      // 作成 (POST) のレイテンシ計測を汚さないよう、削除は別メトリクスに分離する。
      tags: { name: 'DELETE /api/posts/{id}' },
    });
    // 成功時は 204 No Content。クリーンアップが効いているかの確認用 (しきい値は課さない)。
    check(del, {
      'delete post 204': (r) => r.status === 204,
    });
  }

  sleep(1); // 投稿の連投間隔を模す
}
