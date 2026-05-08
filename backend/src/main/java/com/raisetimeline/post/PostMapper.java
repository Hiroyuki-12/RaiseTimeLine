package com.raisetimeline.post;

import com.raisetimeline.post.dto.PostResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * posts テーブルへの SQL 操作を定義する MyBatis Mapper インターフェース。 具体的な SQL は PostMapper.xml に書く。 @Mapper
 * アノテーションにより Spring Boot が起動時に実装を自動生成する。 BackendApplication の @MapperScan に
 * "com.raisetimeline.post" を追加する必要がある。
 */
@Mapper
public interface PostMapper {

    /**
     * posts テーブルに1件 INSERT する。 useGeneratedKeys=true により INSERT 後の id が post.id にセットされる。
     *
     * @param post 登録するポスト（id は null でOK。INSERT 後に自動セットされる）
     */
    void insert(Post post);

    /**
     * INSERT ... RETURNING を使って投稿を1件挿入し、DB 生成タイムスタンプを含む PostResponse を返す。 insert + findById
     * の2クエリを1クエリに削減（N+1 修正）。
     *
     * @param userId 投稿者の ID
     * @param content 投稿本文
     * @param currentUserEmail 現在のユーザーのメールアドレス（liked 判定に使う）
     * @return 挿入後の PostResponse（id・タイムスタンプ含む）
     */
    PostResponse insertAndReturn(
            @Param("userId") Long userId,
            @Param("content") String content,
            @Param("imageUrl") String imageUrl,
            @Param("currentUserEmail") String currentUserEmail);

    /**
     * UPDATE ... RETURNING を使って投稿を更新し、更新後の PostResponse を返す。 update + findById の2クエリを1クエリに削減（N+1
     * 修正）。
     *
     * @param id 更新対象の投稿 ID
     * @param content 新しい本文
     * @param currentUserEmail 現在のユーザーのメールアドレス（liked 判定に使う）
     * @return 更新後の PostResponse
     */
    PostResponse updateAndReturn(
            @Param("id") Long id,
            @Param("content") String content,
            @Param("currentUserEmail") String currentUserEmail);

    /**
     * ポストをページネーション付きで投稿日時の降順で取得する（タイムライン用）。 users テーブルと JOIN し、さらに likes/comments の COUNT サブクエリで
     * N+1 なしに いいね数・コメント数・liked フラグを取得する。
     *
     * @param offset 取得開始位置（0 始まり）
     * @param limit 取得件数
     * @param currentUserEmail 現在のユーザーのメールアドレス（liked 判定に使う）
     * @return PostResponse のリスト（新しい順）
     */
    List<PostResponse> findPageOrderByCreatedAtDesc(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("currentUserEmail") String currentUserEmail);

    /**
     * 指定日時より後に作成された投稿件数を返す（ポーリングによる新着チェック用）。
     *
     * @param since この日時より後に作成されたものをカウントする
     * @return 新着件数
     */
    long countNewerThan(@Param("since") LocalDateTime since);

    /**
     * ID でポストを1件検索する。 編集・削除前のオーナーチェックに使う。
     *
     * @param id 検索するポスト ID
     * @return 見つかった場合 Optional<Post>、見つからなければ Optional.empty()
     */
    Optional<Post> findById(Long id);

    /**
     * ID でポストを1件検索し、いいね数・コメント数・liked フラグを含む PostResponse を返す。 投稿詳細画面（GET /api/posts/{id}）で使う。
     *
     * @param id 検索するポスト ID
     * @param currentUserEmail 現在のユーザーのメールアドレス（liked 判定に使う）
     * @return 見つかった場合 Optional<PostResponse>、見つからなければ Optional.empty()
     */
    Optional<PostResponse> findByIdAsResponse(
            @Param("id") Long id, @Param("currentUserEmail") String currentUserEmail);

    /**
     * 指定ユーザーの投稿を新しい順に取得する（プロフィールページの投稿一覧用）。 いいね数・コメント数・liked フラグを集計サブクエリで取得するため N+1 は発生しない。
     *
     * @param userId 投稿者のユーザー ID
     * @param currentUserEmail 現在のユーザーのメールアドレス（liked 判定に使う）
     * @return PostResponse のリスト（新しい順）
     */
    List<PostResponse> findByUserIdOrderByCreatedAtDesc(
            @Param("userId") Long userId, @Param("currentUserEmail") String currentUserEmail);

    /**
     * フォロー中のユーザーの投稿をページネーション付きで新しい順に取得する（フォロー中タイムライン用）。
     *
     * <p>follows テーブルと INNER JOIN してログイン中ユーザーがフォローしている人の投稿のみ取得する。 全員タイムラインと同じ集計サブクエリで N+1 なしに
     * いいね数・コメント数・liked フラグを取得する。
     *
     * @param userId 現在ログイン中のユーザー ID（フォロー関係の基準）
     * @param offset 取得開始位置（0 始まり）
     * @param limit 取得件数
     * @param currentUserEmail 現在のユーザーのメールアドレス（liked 判定に使う）
     * @return PostResponse のリスト（新しい順）
     */
    List<PostResponse> findFollowingPageOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("currentUserEmail") String currentUserEmail);

    /**
     * ポストの content と updated_at を更新する。
     *
     * @param post content と id がセット済みのポストオブジェクト
     */
    void update(Post post);

    /**
     * ID でポストを1件削除する。
     *
     * @param id 削除するポスト ID
     */
    void deleteById(Long id);
}
