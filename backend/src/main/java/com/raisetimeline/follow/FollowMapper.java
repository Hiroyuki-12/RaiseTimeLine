package com.raisetimeline.follow;

import com.raisetimeline.user.dto.UserSummary;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** follows テーブルへの SQL 操作を定義する MyBatis Mapper インターフェース。 具体的な SQL は FollowMapper.xml に書く。 */
@Mapper
public interface FollowMapper {

    /**
     * follows テーブルに1件 INSERT する。
     *
     * @param follow 登録するフォロー情報
     */
    void insert(Follow follow);

    /**
     * フォロー関係を削除する（アンフォロー）。
     *
     * @param followerId フォローする側のユーザー ID
     * @param followeeId フォローされる側のユーザー ID
     */
    void delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /**
     * 指定したフォロー関係が存在するか確認する。 重複フォロー防止のためにサービス層から呼ばれる。
     *
     * @param followerId フォローする側のユーザー ID
     * @param followeeId フォローされる側のユーザー ID
     * @return フォロー関係が存在すれば true
     */
    boolean exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /**
     * 指定ユーザーがフォローしているユーザー一覧を取得する（フォロー中一覧）。 N+1 を防ぐため follows JOIN users で1クエリで取得する。
     *
     * @param userId フォロー中一覧を取得したいユーザーの ID
     * @param currentUserId 現在ログイン中のユーザー ID（isFollowing フラグに使う）
     * @return フォロー中ユーザーの一覧
     */
    List<UserSummary> findFollowing(
            @Param("userId") Long userId, @Param("currentUserId") Long currentUserId);

    /**
     * 指定ユーザーをフォローしているユーザー一覧を取得する（フォロワー一覧）。
     *
     * @param userId フォロワー一覧を取得したいユーザーの ID
     * @param currentUserId 現在ログイン中のユーザー ID（isFollowing フラグに使う）
     * @return フォロワーユーザーの一覧
     */
    List<UserSummary> findFollowers(
            @Param("userId") Long userId, @Param("currentUserId") Long currentUserId);

    /**
     * 指定ユーザーがフォローしているユーザー数を取得する。
     *
     * @param userId カウント対象のユーザー ID
     * @return フォロー中のユーザー数
     */
    long countFollowing(@Param("userId") Long userId);

    /**
     * 指定ユーザーをフォローしているユーザー数を取得する。
     *
     * @param userId カウント対象のユーザー ID
     * @return フォロワー数
     */
    long countFollowers(@Param("userId") Long userId);
}
