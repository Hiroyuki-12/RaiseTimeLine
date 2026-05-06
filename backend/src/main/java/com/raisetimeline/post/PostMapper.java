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
     * ポストをページネーション付きで投稿日時の降順で取得する（タイムライン用）。 users テーブルと JOIN して投稿者の displayName・username も取得する。
     *
     * @param offset 取得開始位置（0 始まり）
     * @param limit 取得件数
     * @return PostResponse のリスト（新しい順）
     */
    List<PostResponse> findPageOrderByCreatedAtDesc(
            @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 指定日時より後に作成された投稿件数を返す（ポーリングによる新着チェック用）。
     *
     * @param since この日時より後に作成されたものをカウントする
     * @return 新着件数
     */
    long countNewerThan(@Param("since") LocalDateTime since);

    /**
     * ID でポストを1件検索する。 編集・削除前のオーナーチェックや、INSERT 後のタイムスタンプ取得に使う。
     *
     * @param id 検索するポスト ID
     * @return 見つかった場合 Optional<Post>、見つからなければ Optional.empty()
     */
    Optional<Post> findById(Long id);

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
