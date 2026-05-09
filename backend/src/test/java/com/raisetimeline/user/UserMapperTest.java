package com.raisetimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.raisetimeline.support.AbstractMapperIntegrationTest;
import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.dto.UserSummary;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

/**
 * UserMapper の DB 統合テスト。
 *
 * <pre>
 * 対象: UserMapper の全メソッド
 * 技法: 同値分割 + 境界値 (件数 0/1/多) + UNIQUE 制約検証 + エラー推測 (LIKE 特殊文字)
 *
 * 注意: findByUsernameContaining は ILIKE を使うので H2 互換性に懸念あり。
 *       H2 2.x の MODE=PostgreSQL では ILIKE がサポートされているはずだが、
 *       実際に動かしてみて確認する。
 * </pre>
 */
@DisplayName("UserMapper")
class UserMapperTest extends AbstractMapperIntegrationTest {

    @Autowired private UserMapper sut;

    private User insertNew(String email, String username) {
        User user = TestFixtures.user(null, email, username);
        user.setId(null);
        sut.insert(user);
        return user;
    }

    @Test
    @DisplayName("insert で id が自動採番され、findByEmail / findByUsername / findById で取得できる")
    void 基本CRUD() {
        User user = insertNew("alice@example.com", "alice");

        assertThat(user.getId()).isNotNull();
        assertThat(sut.findByEmail("alice@example.com")).isPresent();
        assertThat(sut.findByUsername("alice")).isPresent();
        assertThat(sut.findById(user.getId())).isPresent();
    }

    @Test
    @DisplayName("UNIQUE 制約: email 重複は DuplicateKeyException")
    void email重複() {
        insertNew("dup@example.com", "alice");
        User dup = TestFixtures.user(null, "dup@example.com", "bob");
        dup.setId(null);

        assertThatThrownBy(() -> sut.insert(dup)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("UNIQUE 制約: username 重複は DuplicateKeyException")
    void username重複() {
        insertNew("a@example.com", "dupname");
        User dup = TestFixtures.user(null, "b@example.com", "dupname");
        dup.setId(null);

        assertThatThrownBy(() -> sut.insert(dup)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("existsByEmail / existsByUsername が true/false を返す")
    void exists() {
        insertNew("e@example.com", "exists");

        assertThat(sut.existsByEmail("e@example.com")).isTrue();
        assertThat(sut.existsByEmail("nobody@example.com")).isFalse();
        assertThat(sut.existsByUsername("exists")).isTrue();
        assertThat(sut.existsByUsername("nobody")).isFalse();
    }

    @Test
    @DisplayName("update で username/displayName/bio が更新される")
    void update() {
        User user = insertNew("u@example.com", "original");
        user.setUsername("renamed");
        user.setDisplayName("新名前");
        user.setBio("Hello");

        sut.update(user);

        Optional<User> found = sut.findById(user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("renamed");
        assertThat(found.get().getDisplayName()).isEqualTo("新名前");
        assertThat(found.get().getBio()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("existsByUsernameExcludingSelf: 自分以外で同じ username が存在する場合 true")
    void excludingSelf() {
        User alice = insertNew("a@example.com", "alice");
        insertNew("b@example.com", "bob");

        assertThat(sut.existsByUsernameExcludingSelf("bob", alice.getId())).isTrue();
        // 自分自身の username は除外される
        assertThat(sut.existsByUsernameExcludingSelf("alice", alice.getId())).isFalse();
    }

    @Test
    @DisplayName("findByUsernameContaining: 部分一致で検索でき、自分は除外される")
    void 部分一致検索() {
        User alice = insertNew("a@example.com", "alice");
        insertNew("b@example.com", "alibaba");
        insertNew("c@example.com", "carol");

        // currentUserId=alice なので自分以外で "ali" を含む = alibaba のみ
        List<UserSummary> result = sut.findByUsernameContaining("ali", alice.getId());

        assertThat(result).extracting(UserSummary::username).containsExactly("alibaba");
    }

    @Test
    @DisplayName("findByUsernameContaining: 大文字小文字を区別しない (ILIKE)")
    void 大文字小文字無視() {
        User me = insertNew("m@example.com", "me");
        insertNew("a@example.com", "AliceSmith");

        List<UserSummary> result = sut.findByUsernameContaining("alice", me.getId());

        assertThat(result).extracting(UserSummary::username).containsExactly("AliceSmith");
    }

    @Test
    @DisplayName("findByUsernameContaining: ヒットしないとき空のリスト")
    void ヒットなし() {
        User me = insertNew("m@example.com", "me");

        assertThat(sut.findByUsernameContaining("nomatch", me.getId())).isEmpty();
    }

    @Test
    @DisplayName("updateAvatarUrl で avatar_url が更新される")
    void updateAvatarUrl() {
        User user = insertNew("u@example.com", "alice");

        sut.updateAvatarUrl(user.getId(), "https://s3/u.png");

        assertThat(sut.findById(user.getId()).get().getAvatarUrl()).isEqualTo("https://s3/u.png");
    }
}
