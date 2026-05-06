-- =====================================================
-- V5: comments テーブル作成
-- ユーザーが投稿に対して書くコメントを保存するテーブル。
-- post_id は posts.id への外部キー制約を持つ（投稿削除時にカスケード削除される）。
-- user_id は users.id への外部キー制約を持つ（ユーザー削除時にカスケード削除される）。
-- =====================================================

CREATE TABLE comments (
    id         BIGSERIAL    PRIMARY KEY,
    post_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    -- コメント本文。最大140文字（仕様 F-04 に従う）
    content    VARCHAR(140) NOT NULL,
    -- サーバー側で自動セットする作成日時
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 投稿が削除されたらコメントも一緒に削除する（孤立レコード防止）
    CONSTRAINT fk_comments_post_id FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    -- ユーザーが削除されたらそのユーザーのコメントも削除する
    CONSTRAINT fk_comments_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- コメント一覧取得（post_id でフィルタ）を高速化するインデックス
-- タイムライン集計サブクエリ（COUNT WHERE post_id = ?）でも使われる
CREATE INDEX idx_comments_post_id ON comments (post_id);
