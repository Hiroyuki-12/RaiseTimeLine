package com.raisetimeline.like;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * likes テーブルへの SQL 操作を定義する MyBatis Mapper インターフェース。 具体的な SQL は LikeMapper.xml に書く。
 * BackendApplication の @MapperScan に "com.raisetimeline.like" を追加する必要がある。
 */
@Mapper
public interface LikeMapper {

    /**
     * likes テーブルに1件 INSERT する。 ON CONFLICT DO NOTHING により、重複いいねは DB レベルで無視される（競合状態への防衛）。 サービス層でも
     * exists チェックするが、同時リクエスト時の二重登録を防ぐ多段防御の仕組み。
     *
     * @param like 登録するいいね情報
     */
    void insert(Like like);

    /**
     * 指定した投稿・ユーザーのいいねを削除する。
     *
     * @param postId いいね対象の投稿 ID
     * @param userId いいねを取り消すユーザーの ID
     */
    void delete(@Param("postId") Long postId, @Param("userId") Long userId);

    /**
     * 指定した投稿・ユーザーのいいねが存在するか確認する。 サービス層で重複追加・存在しない削除を弾くために使う。
     *
     * @param postId いいね対象の投稿 ID
     * @param userId 確認するユーザーの ID
     * @return いいねが存在すれば true、なければ false
     */
    boolean exists(@Param("postId") Long postId, @Param("userId") Long userId);
}
