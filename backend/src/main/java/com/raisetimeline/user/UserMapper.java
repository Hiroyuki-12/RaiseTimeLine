package com.raisetimeline.user;

import com.raisetimeline.user.dto.UserSummary;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * users テーブルへの SQL 操作を定義する MyBatis Mapper インターフェース。 @Mapper アノテーションを付けると Spring Boot
 * が起動時にこのインターフェースの実装を 自動生成して Bean として登録してくれる。具体的な SQL は UserMapper.xml に書く。
 *
 * <p>Optional を使うことで、検索結果が0件のときに null の代わりに Optional.empty() が返り、 NullPointerException を防ぎやすくなる。
 */
@Mapper
public interface UserMapper {

    /**
     * users テーブルに1件 INSERT する。 useGeneratedKeys=true により、INSERT 後に DB が発行した id が user.id にセットされる。
     *
     * @param user 登録するユーザー情報（id は null でOK。INSERT 後に自動セットされる）
     */
    void insert(User user);

    /**
     * メールアドレスでユーザーを1件検索する。 ログイン時にメールアドレスからユーザーを取得するために使う。
     *
     * @param email 検索するメールアドレス
     * @return 見つかった場合は Optional<User>、見つからなければ Optional.empty()
     */
    Optional<User> findByEmail(String email);

    /**
     * 指定したメールアドレスが既に登録されているか確認する。 登録時の重複チェックに使う。
     *
     * @param email 確認するメールアドレス
     * @return 存在する場合 true、存在しない場合 false
     */
    boolean existsByEmail(String email);

    /**
     * 指定したユーザー名が既に登録されているか確認する。 登録時の重複チェックに使う。
     *
     * @param username 確認するユーザー名
     * @return 存在する場合 true、存在しない場合 false
     */
    boolean existsByUsername(String username);

    /**
     * ユーザー ID でユーザーを1件検索する。 リフレッシュトークンからユーザーを特定するために使う。
     *
     * @param id 検索するユーザー ID（users.id）
     * @return 見つかった場合は Optional<User>、見つからなければ Optional.empty()
     */
    Optional<User> findById(Long id);

    /**
     * ユーザー名（@handle）でユーザーを1件検索する。 プロフィールページの URL パラメータから取得するために使う。
     *
     * @param username 検索するユーザー名
     * @return 見つかった場合は Optional<User>、見つからなければ Optional.empty()
     */
    Optional<User> findByUsername(String username);

    /**
     * ユーザーのプロフィール情報を更新する。 username, display_name, bio を更新対象とする。
     *
     * @param user 更新するユーザー情報（id が必須）
     */
    void update(User user);

    /**
     * 指定したユーザー名が自分以外に存在するか確認する。 プロフィール編集時のユーザー名重複チェックに使う（自分が今持っているユーザー名は除外する）。
     *
     * @param username 確認するユーザー名
     * @param userId 除外する自分のユーザー ID
     * @return 自分以外に同じユーザー名が存在する場合 true
     */
    boolean existsByUsernameExcludingSelf(
            @Param("username") String username, @Param("userId") Long userId);

    /**
     * ユーザー名の部分一致でユーザーを検索する（ユーザー検索画面用）。
     *
     * <p>PostgreSQL の ILIKE を使い大文字小文字を区別せずに部分一致検索する。 自分自身は結果から除外する（自分をフォローするのは不自然なため）。 EXISTS
     * サブクエリで isFollowing フラグを1クエリで判定する（N+1 防止）。
     *
     * @param q 検索キーワード（1文字以上）
     * @param currentUserId 現在ログイン中のユーザー ID（isFollowing 判定・自分除外に使う）
     * @return マッチしたユーザーの一覧（UserSummary 形式、最大 20 件）
     */
    List<UserSummary> findByUsernameContaining(
            @Param("q") String q, @Param("currentUserId") Long currentUserId);

    /**
     * ユーザーのアバター URL のみを更新する（画像アップロード後の更新に使う）。
     *
     * @param userId 更新対象のユーザー ID
     * @param avatarUrl 新しいアバター画像 URL（S3 の公開 URL）
     */
    void updateAvatarUrl(@Param("userId") Long userId, @Param("avatarUrl") String avatarUrl);
}
