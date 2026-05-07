-- フォロー関係を管理するテーブル
-- follower_id: フォローする側（能動）
-- followee_id: フォローされる側（受動）
-- UNIQUE制約でDB レベルの重複フォローを防止する
CREATE TABLE follows (
  id          BIGSERIAL PRIMARY KEY,
  follower_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  followee_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_follows UNIQUE (follower_id, followee_id)
);

-- フォロー中一覧（自分がフォローしている人）を高速に取得するためのインデックス
CREATE INDEX idx_follows_follower_id ON follows(follower_id);

-- フォロワー一覧（自分をフォローしている人）を高速に取得するためのインデックス
CREATE INDEX idx_follows_followee_id ON follows(followee_id);
