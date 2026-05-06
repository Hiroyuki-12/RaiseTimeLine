-- ================================================
-- V4: posts テーブル作成
-- ユーザーが投稿したポストを保存するテーブル。
-- user_id は users.id への外部キー制約を持つ。
-- ================================================

CREATE TABLE posts (
    -- id: 主キー。BIGSERIAL は自動採番の整数
    id          BIGSERIAL       PRIMARY KEY,

    -- user_id: 投稿者の users.id。ユーザー削除時はカスケード削除する
    user_id     BIGINT          NOT NULL,

    -- content: 投稿本文。最大 280 文字（Twitter 準拠）
    content     VARCHAR(280)    NOT NULL,

    -- created_at: 投稿日時。INSERT 時に自動で現在時刻がセットされる
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- updated_at: 最終更新日時。アプリ側で更新する
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- users テーブルへの外部キー制約。ユーザーが削除されたら投稿も削除する
    CONSTRAINT fk_posts_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- タイムライン取得（created_at DESC でページネーション）を高速化するインデックス
CREATE INDEX idx_posts_created_at ON posts (created_at DESC);
