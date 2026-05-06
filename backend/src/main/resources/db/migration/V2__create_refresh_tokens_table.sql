-- リフレッシュトークンを管理するテーブル
-- アクセストークン（15分）が期限切れになったとき、このテーブルのトークンを使って再発行する
-- HttpOnly Cookie で送られてくるため JS からは直接見えない（XSS に強い設計）
CREATE TABLE refresh_tokens (
  id         BIGSERIAL PRIMARY KEY,
  -- どのユーザーのトークンかを紐付ける。ユーザー削除時に自動的に削除される（CASCADE）
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- UUID v4 の文字列トークン。JWT ではなく不透明トークンとして DB で検証する
  token      VARCHAR(512) NOT NULL UNIQUE,
  -- 有効期限（7日間）。期限切れのトークンは拒否する
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- トークン文字列での検索は毎回発生するためインデックスを付ける
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
-- ユーザー単位でトークンを削除する（全デバイスログアウト）用
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
