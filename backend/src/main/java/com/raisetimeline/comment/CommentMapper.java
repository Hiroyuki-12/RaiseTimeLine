package com.raisetimeline.comment;

import com.raisetimeline.comment.dto.CommentResponse;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * comments テーブルへの SQL 操作を定義する MyBatis Mapper インターフェース。 具体的な SQL は CommentMapper.xml に書く。
 * BackendApplication の @MapperScan に "com.raisetimeline.comment" を追加する必要がある。
 */
@Mapper
public interface CommentMapper {

    /**
     * comments テーブルに1件 INSERT する。 useGeneratedKeys=true により INSERT 後の id が comment.id にセットされる。
     *
     * @param comment 登録するコメント（id は null で OK。INSERT 後に自動セットされる）
     */
    void insert(Comment comment);

    /**
     * 指定した投稿のコメント一覧を作成日時の昇順で取得する（古いコメントが上に来る）。 N+1 防止: CommentMapper.xml で comments JOIN users
     * の1クエリで取得する。
     *
     * @param postId コメントを取得する投稿の ID
     * @return CommentResponse のリスト（created_at 昇順）
     */
    List<CommentResponse> findByPostId(@Param("postId") Long postId);

    /**
     * ID でコメントを1件検索する。 編集・削除前のオーナーチェック（user_id の確認）のために使う。
     *
     * @param id 検索するコメント ID
     * @return 見つかった場合 Optional<Comment>、見つからなければ Optional.empty()
     */
    Optional<Comment> findById(@Param("id") Long id);

    /**
     * コメントの content を更新する。
     *
     * @param id 更新対象のコメント ID
     * @param content 更新後の本文
     */
    void update(@Param("id") Long id, @Param("content") String content);

    /**
     * ID でコメントを1件削除する。
     *
     * @param id 削除するコメント ID
     */
    void deleteById(@Param("id") Long id);
}
