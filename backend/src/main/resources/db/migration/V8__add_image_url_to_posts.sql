-- posts テーブルに画像 URL を保存するカラムを追加する。
-- NULL 許容（画像なし投稿は NULL のまま）。
-- 既存の投稿には影響しない（DEFAULT NULL なので既存行は NULL になる）。
-- 画像は AWS S3 に保存し、S3 の公開 URL をこのカラムに保存する。
ALTER TABLE posts ADD COLUMN image_url VARCHAR(500);
