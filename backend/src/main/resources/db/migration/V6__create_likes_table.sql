-- =====================================================
-- V6: likes テーブル作成
-- ユーザーが投稿に対するいいねを保存するテーブル。
-- (post_id, user_id) の UNIQUE 制約により、1ユーザーが同じ投稿に2回以上
-- いいねできないことを DB レベルで保証する（アプリ側だけでは競合状態が起きうる）。
-- =====================================================

CREATE TABLE likes (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 投稿が削除されたらいいねも一緒に削除する（孤立レコード防止）
    CONSTRAINT fk_likes_post_id FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    -- ユーザーが削除されたらそのユーザーのいいねも削除する
    CONSTRAINT fk_likes_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    -- 1ユーザー = 1投稿 = 1いいね を DB レベルで強制する
    CONSTRAINT uq_likes_post_user UNIQUE (post_id, user_id)
);

-- いいね集計（post_id でフィルタ）を高速化するインデックス
-- タイムライン集計サブクエリ（COUNT WHERE post_id = ?）でも使われる
CREATE INDEX idx_likes_post_id ON likes (post_id);
