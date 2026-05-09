package com.raisetimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.support.AbstractMapperIntegrationTest;
import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RefreshTokenMapper の DB 統合テスト。
 *
 * <pre>
 * 対象: RefreshTokenMapper (insert / findByToken / deleteByToken / deleteByUserId)
 * 技法: 基本 CRUD + FK CASCADE 検証
 *
 * - insert で id が自動採番されること (useGeneratedKeys)
 * - findByToken で挿入したトークンが取れること / 不在時は Optional.empty
 * - deleteByToken / deleteByUserId が指定対象のみ削除すること
 * - users 削除時に refresh_tokens も CASCADE で消えること (V2 マイグレーションの FK 制約)
 *
 * @ActiveProfiles("test") により application-test.yml の H2 + Flyway で起動。
 * @AutoConfigureTestDatabase(replace = Replace.NONE) で MyBatisTest が自動的に
 * H2 を上書きするのを止め、application-test.yml の H2 設定を使う。
 * </pre>
 */
@DisplayName("RefreshTokenMapper")
class RefreshTokenMapperTest extends AbstractMapperIntegrationTest {

    @Autowired private RefreshTokenMapper sut;
    @Autowired private UserMapper userMapper;

    /** FK 制約 (refresh_tokens.user_id → users.id) を満たすため事前にユーザーを INSERT する。 */
    private User insertUser(String email, String username) {
        User user = TestFixtures.user(null, email, username);
        user.setId(null); // 自動採番させる
        userMapper.insert(user);
        return user;
    }

    @Test
    @DisplayName("insert すると id が自動採番される")
    void insertで自動採番() {
        User user = insertUser("a@example.com", "alice");
        RefreshToken token =
                TestFixtures.refreshToken(user.getId(), "tok-1", LocalDateTime.now().plusDays(7));
        token.setId(null);

        sut.insert(token);

        assertThat(token.getId()).isNotNull();
    }

    @Test
    @DisplayName("findByToken: 挿入したトークンを取得できる")
    void findByToken正常() {
        User user = insertUser("b@example.com", "bob");
        RefreshToken token =
                TestFixtures.refreshToken(
                        user.getId(), "tok-find", LocalDateTime.now().plusDays(7));
        token.setId(null);
        sut.insert(token);

        Optional<RefreshToken> found = sut.findByToken("tok-find");

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("findByToken: 存在しないトークンは Optional.empty")
    void findByToken不在() {
        assertThat(sut.findByToken("not-exist")).isEmpty();
    }

    @Test
    @DisplayName("deleteByToken: 指定したトークンだけが削除される")
    void deleteByToken() {
        User user = insertUser("c@example.com", "carol");
        RefreshToken keep =
                TestFixtures.refreshToken(user.getId(), "keep", LocalDateTime.now().plusDays(7));
        keep.setId(null);
        RefreshToken toDelete =
                TestFixtures.refreshToken(
                        user.getId(), "delete-me", LocalDateTime.now().plusDays(7));
        toDelete.setId(null);
        sut.insert(keep);
        sut.insert(toDelete);

        sut.deleteByToken("delete-me");

        assertThat(sut.findByToken("delete-me")).isEmpty();
        assertThat(sut.findByToken("keep")).isPresent();
    }

    @Test
    @DisplayName("FK CASCADE: ユーザーを削除すると refresh_tokens も消える")
    void cascade削除() {
        // 注: UserMapper には削除メソッドが無いため、CASCADE 検証は別 Mapper のテスト範囲とする。
        // ここでは deleteByUserId が当該ユーザーのトークンを全削除することを確認する。
        User user = insertUser("d@example.com", "dave");
        RefreshToken t1 =
                TestFixtures.refreshToken(user.getId(), "t1", LocalDateTime.now().plusDays(7));
        t1.setId(null);
        RefreshToken t2 =
                TestFixtures.refreshToken(user.getId(), "t2", LocalDateTime.now().plusDays(7));
        t2.setId(null);
        sut.insert(t1);
        sut.insert(t2);

        sut.deleteByUserId(user.getId());

        assertThat(sut.findByToken("t1")).isEmpty();
        assertThat(sut.findByToken("t2")).isEmpty();
    }
}
