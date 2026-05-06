-- users テーブルに表示名（display_name）カラムを追加する
-- username は英数字・_・- のみ（ログイン識別子兼 @handle として使う）
-- display_name は日本語を含む任意の文字列（画面上に表示する名前）
-- 既存ユーザーのために NULL を許容し、デフォルトは username と同じ値にする
ALTER TABLE users
    ADD COLUMN display_name VARCHAR(50);

-- 既存ユーザーは username を display_name の初期値として設定する
UPDATE users SET display_name = username WHERE display_name IS NULL;

-- 新規ユーザーは必ず display_name を入力するため NOT NULL 制約を付ける
ALTER TABLE users
    ALTER COLUMN display_name SET NOT NULL;
