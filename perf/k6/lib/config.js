// ============================================================================
// k6 共通設定
//
// 環境変数 (k6 run -e KEY=VALUE で渡す) から設定を読み込む。
// 既定値を用意しているので、何も渡さなくてもローカル環境で動く。
// ============================================================================

// バックエンドのベース URL。
// 既定は Spring Boot の固定ポート 8080 (CLAUDE.md のポート運用ルール)。
// k6 はブラウザではなく直接 HTTP を叩くので、Vite プロキシ(5173)ではなく
// バックエンドに直接アクセスする。
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// シードした負荷テストユーザーの総数。seed.sql の n_users と一致させる。
// k6 はこの範囲でランダムにユーザーを選んでログインする。
export const SEED_USER_COUNT = parseInt(__ENV.SEED_USER_COUNT || '500', 10);

// シードユーザー共通のパスワード (seed.sql の固定 BCrypt ハッシュの平文)。
export const SEED_PASSWORD = __ENV.SEED_PASSWORD || 'Password123';

// 1..SEED_USER_COUNT の範囲でランダムな整数を返す。
// ログインするユーザーをばらけさせ、特定ユーザーへの偏りを避けるため。
export function randomUserIndex() {
  return Math.floor(Math.random() * SEED_USER_COUNT) + 1;
}

// インデックスから loaduserN のメールアドレスを組み立てる。
// seed.sql の命名規則 (loaduser1@example.com ...) と一致させている。
export function emailForIndex(index) {
  return `loaduser${index}@example.com`;
}
