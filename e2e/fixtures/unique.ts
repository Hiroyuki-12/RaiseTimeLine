/**
 * テストごとに衝突しない一意な識別子を払い出すユーティリティ。
 *
 * E2E は「各テスト実行時に /api/auth/register で新規ユーザーを作る」自己完結方式を採るため、
 * username / email が他のテストや過去の実行と被らないようにする必要がある。
 * 実行時刻（base36）＋プロセス内連番を組み合わせて一意性を担保する。
 */

// このプロセス内で単調増加する連番（並列ワーカー間はプロセスが別なので衝突しない）。
let counter = 0

/** 一意な短い文字列を返す（例: "lq3x9f2a"）。 */
export function uniqueId(): string {
  counter += 1
  // 並列ワーカーは別プロセスで動くため、同一ミリ秒に複数ワーカーが同じ ID を作りうる。
  // Date.now()（時刻）＋ process.pid（ワーカー別）＋ 連番 ＋ 乱数 を組み合わせて確実に一意化する。
  const rand = Math.floor(Math.random() * 1e6).toString(36)
  return `${Date.now().toString(36)}${process.pid.toString(36)}${counter}${rand}`
}

/** 新規登録に使える一意なアカウント情報を生成する。 */
export function uniqueAccount(): {
  username: string
  email: string
  password: string
  displayName: string
} {
  const id = uniqueId()
  // username はバックエンド制約（英数字・_・-、1〜50文字）に従う。先頭を固定接頭辞にして検索・識別しやすくする。
  const username = `e2e_${id}`.slice(0, 50)
  return {
    username,
    email: `${username}@example.com`,
    // 「英字と数字を含む8文字以上」というバリデーションを満たすパスワード。
    password: 'Passw0rd123',
    displayName: `E2E ${id}`,
  }
}
