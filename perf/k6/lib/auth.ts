// ============================================================================
// k6 認証ヘルパー
//
// バックエンドは JWT 認証。タイムライン取得など大半の API は
// Authorization: Bearer <accessToken> が必須。
// ここでログインして accessToken を取得する共通関数を提供する。
// ============================================================================

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SEED_PASSWORD, randomUserIndex, emailForIndex } from './config.ts';

// 指定したメールアドレスでログインし、accessToken を返す。
// レスポンス (AuthResponse) は { accessToken, userId, username, displayName }。
// ログイン失敗時は null を返すので、戻り値は string | null。
export function login(email: string, password: string): string | null {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /api/auth/login' }, // メトリクスをエンドポイント単位で集計するため
    },
  );

  // ログインが 200 で accessToken を含むことを確認する。
  // これが失敗する場合、シードユーザーのパスワードハッシュが不正な可能性が高い。
  const ok = check(res, {
    'login status is 200': (r) => r.status === 200,
    'login returns accessToken': (r) => {
      try {
        return typeof r.json('accessToken') === 'string';
      } catch (e) {
        return false;
      }
    },
  });

  if (!ok) {
    return null;
  }
  // res.json('accessToken') の戻り値は JSONValue 型なので、上の check で
  // string であることを確認済みのうえで string として扱う。
  return res.json('accessToken') as string;
}

// ランダムなシードユーザーでログインして accessToken を返す。
// 各 VU (仮想ユーザー) が別々のユーザーとしてアクセスし、負荷を分散させる。
export function loginRandomSeedUser(): string | null {
  const email = emailForIndex(randomUserIndex());
  return login(email, SEED_PASSWORD);
}

// accessToken から Authorization ヘッダーを組み立てる。
// 認証必須 API を叩くときに params.headers として渡す。
export function authHeaders(token: string): Record<string, string> {
  return {
    Authorization: `Bearer ${token}`,
  };
}
