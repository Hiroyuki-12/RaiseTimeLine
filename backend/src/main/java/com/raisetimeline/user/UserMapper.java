package com.raisetimeline.user;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

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
     * ユーザー ID でユーザーを1件検索する。
     * リフレッシュトークンからユーザーを特定するために使う。
     *
     * @param id 検索するユーザー ID（users.id）
     * @return 見つかった場合は Optional<User>、見つからなければ Optional.empty()
     */
    Optional<User> findById(Long id);
}
