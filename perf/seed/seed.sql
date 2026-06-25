-- ============================================================================
-- パフォーマンステスト用 シードデータ投入 SQL
--
-- なぜ必要か:
--   負荷テストは「空の DB」では意味がない。タイムライン取得
--   (GET /api/posts) は投稿ごとに いいね数 / コメント数 / 自分のいいね有無 を
--   サブクエリで集計しているため、現実的なデータ量が無いとボトルネックが
--   一切表面化しない。そこで本スクリプトで大量データを一括投入する。
--
-- ⚠️ 警告: このスクリプトは対象テーブルを TRUNCATE で全削除してから投入する。
--   絶対に本番 DB では実行しないこと。ローカル開発用 DB (compose.yaml の
--   PostgreSQL) 専用。
--
-- 投入規模はコマンドラインから上書きできる (run-seed.sh 参照)。
--   例) psql ... -v n_users=1000 -v n_posts=100000 -f seed.sql
-- ============================================================================

-- エラーが出たら即座に中断する (中途半端なデータを残さない)
\set ON_ERROR_STOP on

-- ----------------------------------------------------------------------------
-- 投入規模の既定値。-v で渡されていればそちらを優先する。
-- :{?var} は「変数 var が定義済みか」を判定する psql の構文。
-- ----------------------------------------------------------------------------
\if :{?n_users}
\else
  \set n_users 500
\endif
\if :{?n_posts}
\else
  \set n_posts 50000
\endif
\if :{?n_likes}
\else
  \set n_likes 200000
\endif
\if :{?n_comments}
\else
  \set n_comments 100000
\endif
\if :{?n_follows}
\else
  \set n_follows 5000
\endif

\echo '== シード投入開始 =='
\echo 'users    =' :n_users
\echo 'posts    =' :n_posts
\echo 'likes    =' :n_likes
\echo 'comments =' :n_comments
\echo 'follows  =' :n_follows

-- すべてを 1 トランザクションで実行する。途中で失敗したら全部ロールバックされ、
-- 中途半端なデータが残らない。
BEGIN;

-- ----------------------------------------------------------------------------
-- 既存データの全削除。
--   RESTART IDENTITY: BIGSERIAL の採番を 1 に戻す (id が必ず 1 から始まる前提で
--   以降の INSERT が user_id / post_id をランダム参照できるようにするため)。
--   CASCADE: 外部キーで連なる子テーブルもまとめて削除する。
-- refresh_tokens も users 削除に巻き込まれるため一緒に消える。
-- ----------------------------------------------------------------------------
TRUNCATE TABLE likes, comments, follows, posts, refresh_tokens, users
  RESTART IDENTITY CASCADE;

-- ----------------------------------------------------------------------------
-- 1) users: ログイン可能なテストユーザーを大量投入
--   password_hash は固定の BCrypt ハッシュ (平文 "Password123")。
--   なぜ全員同じハッシュか:
--     BCrypt は照合時にハッシュ内のソルトを使うため、同じハッシュでも
--     "Password123" でログインできる。k6 から全ユーザーへ同一パスワードで
--     ログインさせたいので、生成済みの固定ハッシュを共通で埋め込む。
--   email/username は loaduser1..N で連番。k6 側もこの規則で組み立てる。
-- ----------------------------------------------------------------------------
INSERT INTO users (email, password_hash, username, display_name, bio, created_at, updated_at)
SELECT
    'loaduser' || g || '@example.com',
    -- "Password123" の BCrypt ハッシュ (strength 10)。Spring の
    -- BCryptPasswordEncoder で照合可能 ($2y も検証できる)。
    '$2y$10$UYvG/8mfJhaZ3DOr6tgjqeQC1oWZ7cfv.uD108VG9hoBjwQ6ogQ8y',
    'loaduser' || g,
    'ロードユーザー' || g,
    '負荷テスト用ユーザー',
    NOW() - (random() * interval '365 days'),
    NOW()
FROM generate_series(1, :n_users) AS g;

-- ----------------------------------------------------------------------------
-- 2) posts: 投稿を大量投入
--   user_id は 1..n_users の範囲でラウンドロビン的に割り当てる。
--   created_at を過去 90 日にランダム分散させ、created_at DESC の
--   ページング (idx_posts_created_at) が現実的に効く状態を作る。
-- ----------------------------------------------------------------------------
INSERT INTO posts (user_id, content, created_at, updated_at)
SELECT
    (g % :n_users) + 1,
    '負荷テスト投稿 #' || g || ' いいねやコメントの集計コストを測るためのダミー本文です。',
    NOW() - (random() * interval '90 days'),
    NOW()
FROM generate_series(1, :n_posts) AS g;

-- ----------------------------------------------------------------------------
-- 3) likes: いいねをランダムに投入
--   (post_id, user_id) に UNIQUE 制約があるため、ランダム生成だと衝突しうる。
--   ON CONFLICT DO NOTHING で重複はスキップする (実件数は指定よりやや少なくなる
--   が、負荷計測の目的上問題ない)。
-- ----------------------------------------------------------------------------
INSERT INTO likes (post_id, user_id, created_at)
SELECT
    floor(random() * :n_posts)::bigint + 1,
    floor(random() * :n_users)::bigint + 1,
    NOW() - (random() * interval '90 days')
FROM generate_series(1, :n_likes) AS g
ON CONFLICT (post_id, user_id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 4) comments: コメントをランダムに投入 (UNIQUE 制約なし)
-- ----------------------------------------------------------------------------
INSERT INTO comments (post_id, user_id, content, created_at)
SELECT
    floor(random() * :n_posts)::bigint + 1,
    floor(random() * :n_users)::bigint + 1,
    '負荷テストコメント #' || g,
    NOW() - (random() * interval '90 days')
FROM generate_series(1, :n_comments) AS g;

-- ----------------------------------------------------------------------------
-- 5) follows: フォロー関係をランダムに投入
--   自分自身はフォローできない (follower <> followee)。
--   (follower_id, followee_id) UNIQUE のため衝突は ON CONFLICT でスキップ。
-- ----------------------------------------------------------------------------
INSERT INTO follows (follower_id, followee_id, created_at)
SELECT follower_id, followee_id, NOW() - (random() * interval '90 days')
FROM (
    SELECT
        floor(random() * :n_users)::bigint + 1 AS follower_id,
        floor(random() * :n_users)::bigint + 1 AS followee_id
    FROM generate_series(1, :n_follows) AS g
) AS candidates
WHERE follower_id <> followee_id
ON CONFLICT (follower_id, followee_id) DO NOTHING;

COMMIT;

-- ----------------------------------------------------------------------------
-- 投入結果の確認 (件数を表示)
-- ----------------------------------------------------------------------------
\echo '== 投入結果 =='
SELECT
    (SELECT count(*) FROM users)    AS users,
    (SELECT count(*) FROM posts)    AS posts,
    (SELECT count(*) FROM likes)    AS likes,
    (SELECT count(*) FROM comments) AS comments,
    (SELECT count(*) FROM follows)  AS follows;

-- PostgreSQL の実行計画はテーブル統計に依存する。大量投入直後は統計が古いため
-- ANALYZE して最新化しておく (EXPLAIN ANALYZE の結果を正しくするため)。
ANALYZE users, posts, likes, comments, follows;

\echo '== シード投入完了 =='
