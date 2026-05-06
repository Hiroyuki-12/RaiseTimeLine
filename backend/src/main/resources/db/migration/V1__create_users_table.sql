-- ================================================
-- V1: users テーブル作成
-- ユーザー情報を保存するテーブル。
-- email と username はそれぞれ重複を許可しない（UNIQUE 制約）。
-- パスワードは平文ではなく BCrypt でハッシュ化した値を保存する。
-- ================================================

CREATE TABLE users (
    -- id: 主キー。BIGSERIAL は自動採番の整数（INSERT するたびに +1 される）
    id            BIGSERIAL     PRIMARY KEY,

    -- email: ログインに使うメールアドレス。重複不可
    email         VARCHAR(255)  NOT NULL,

    -- password_hash: BCrypt でハッシュ化されたパスワード。平文は保存しない
    password_hash VARCHAR(255)  NOT NULL,

    -- username: 画面に表示されるユーザー名。英数字・_・- のみ許可（1〜50文字）
    username      VARCHAR(50)   NOT NULL,

    -- avatar_url: プロフィール画像の URL（S3 等に保存した画像の URL）。未設定可
    avatar_url    TEXT,

    -- bio: プロフィール文。未設定可
    bio           VARCHAR(160),

    -- created_at: レコード作成日時。INSERT 時に自動で現在時刻がセットされる
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- updated_at: レコード更新日時。アプリ側（@PreUpdate 相当）で更新する
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 同じメールアドレスで2つのアカウントを作れないようにする
    CONSTRAINT uq_users_email    UNIQUE (email),

    -- 同じユーザー名で2つのアカウントを作れないようにする
    CONSTRAINT uq_users_username UNIQUE (username)
);
