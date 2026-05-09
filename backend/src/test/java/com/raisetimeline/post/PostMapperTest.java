package com.raisetimeline.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.follow.FollowMapper;
import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.support.AbstractMapperIntegrationTest;
import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostMapper の DB 統合テスト。
 *
 * <pre>
 * 対象: PostMapper の全メソッド (insert / insertAndReturn / updateAndReturn /
 *      findPageOrderByCreatedAtDesc / countNewerThan / findById / findByIdAsResponse /
 *      findByUserIdOrderByCreatedAtDesc / findFollowingPageOrderByCreatedAtDesc /
 *      update / deleteById)
 * 技法: 件数の境界値 (0/1/多) + ORDER BY 検証 + 集計サブクエリ + INSERT/UPDATE...RETURNING の確認
 *
 * 注意: insertAndReturn / updateAndReturn は PostgreSQL 固有の RETURNING 句を使うため
 *       H2 互換性に懸念があったが、H2 2.4 + MODE=PostgreSQL ではサポートされていることを実測で確認する。
 * </pre>
 */
@DisplayName("PostMapper")
class PostMapperTest extends AbstractMapperIntegrationTest {

    @Autowired private PostMapper sut;
    @Autowired private UserMapper userMapper;
    @Autowired private FollowMapper followMapper;
    // ORDER BY 検証で異なる created_at を明示的にセットしたいときに使う。
    // 本番 SQL は NOW() を使うが、PostgreSQL の NOW() はトランザクション開始時刻に固定されるため、
    // 同一トランザクション内の連続 INSERT では created_at が同値になり ORDER BY が不定になる。
    @Autowired private JdbcTemplate jdbcTemplate;

    private User insertUser(String email, String username) {
        User u = TestFixtures.user(null, email, username);
        u.setId(null);
        userMapper.insert(u);
        return u;
    }

    @Test
    @DisplayName("insert で id が自動採番される")
    void insertで自動採番() {
        User user = insertUser("a@example.com", "alice");

        com.raisetimeline.post.Post post = TestFixtures.post(null, user.getId(), "hello");
        post.setId(null);
        sut.insert(post);

        assertThat(post.getId()).isNotNull();
    }

    @Test
    @DisplayName("insertAndReturn は INSERT RETURNING で PostResponse を返す")
    void insertAndReturn() {
        User user = insertUser("a@example.com", "alice");

        PostResponse result = sut.insertAndReturn(user.getId(), "本文", null, user.getEmail());

        assertThat(result.id()).isNotNull();
        assertThat(result.content()).isEqualTo("本文");
        assertThat(result.authorId()).isEqualTo(user.getId());
        assertThat(result.authorUsername()).isEqualTo("alice");
        assertThat(result.likeCount()).isZero();
        assertThat(result.commentCount()).isZero();
        assertThat(result.liked()).isFalse();
    }

    @Test
    @DisplayName("updateAndReturn は UPDATE RETURNING で更新後の PostResponse を返す")
    void updateAndReturn() {
        User user = insertUser("a@example.com", "alice");
        PostResponse created = sut.insertAndReturn(user.getId(), "old", null, user.getEmail());

        PostResponse updated = sut.updateAndReturn(created.id(), "new", user.getEmail());

        assertThat(updated.content()).isEqualTo("new");
    }

    @Test
    @DisplayName("findById は Post エンティティを返す / 不在時は Optional.empty")
    void findById() {
        User user = insertUser("a@example.com", "alice");
        PostResponse created = sut.insertAndReturn(user.getId(), "x", null, user.getEmail());

        Optional<com.raisetimeline.post.Post> found = sut.findById(created.id());

        assertThat(found).isPresent();
        assertThat(sut.findById(99999L)).isEmpty();
    }

    @Test
    @DisplayName("findByIdAsResponse は集計付き PostResponse を返す")
    void findByIdAsResponse() {
        User user = insertUser("a@example.com", "alice");
        PostResponse created = sut.insertAndReturn(user.getId(), "hello", null, user.getEmail());

        Optional<PostResponse> found = sut.findByIdAsResponse(created.id(), user.getEmail());

        assertThat(found).isPresent();
        assertThat(found.get().authorUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("findPageOrderByCreatedAtDesc は新しい順 / LIMIT/OFFSET が効く")
    void タイムライン取得() {
        User user = insertUser("a@example.com", "alice");
        // PostgreSQL の NOW() はトランザクション開始時刻に固定されるため、@MybatisTest の単一
        // トランザクション内で連続 INSERT すると created_at が全て同値になり ORDER BY が不定になる。
        // ORDER BY DESC が効くことを確かめたいので、JdbcTemplate で明示的に異なる timestamp を入れる。
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        insertPostWithTimestamp(user.getId(), "first", base);
        insertPostWithTimestamp(user.getId(), "second", base.plusMinutes(1));
        insertPostWithTimestamp(user.getId(), "third", base.plusMinutes(2));

        List<PostResponse> page1 = sut.findPageOrderByCreatedAtDesc(0, 2, user.getEmail());
        List<PostResponse> page2 = sut.findPageOrderByCreatedAtDesc(2, 2, user.getEmail());

        assertThat(page1).hasSize(2);
        assertThat(page2).hasSize(1);
        // created_at DESC: 最初の 2 件は third / second の順
        assertThat(page1.get(0).content()).isEqualTo("third");
        assertThat(page1.get(1).content()).isEqualTo("second");
    }

    /** ORDER BY 検証用に created_at / updated_at を明示指定で INSERT するヘルパー。 */
    private void insertPostWithTimestamp(Long userId, String content, LocalDateTime ts) {
        jdbcTemplate.update(
                "INSERT INTO posts (user_id, content, created_at, updated_at) VALUES (?, ?, ?, ?)",
                userId,
                content,
                ts,
                ts);
    }

    @Test
    @DisplayName("findPageOrderByCreatedAtDesc: 0 件のとき空のリスト")
    void タイムライン0件() {
        assertThat(sut.findPageOrderByCreatedAtDesc(0, 20, "nobody@example.com")).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc は指定ユーザーの投稿のみ返す")
    void ユーザー投稿一覧() {
        User alice = insertUser("a@example.com", "alice");
        User bob = insertUser("b@example.com", "bob");
        sut.insertAndReturn(alice.getId(), "alice-post", null, alice.getEmail());
        sut.insertAndReturn(bob.getId(), "bob-post", null, bob.getEmail());

        List<PostResponse> result =
                sut.findByUserIdOrderByCreatedAtDesc(alice.getId(), alice.getEmail());

        assertThat(result).extracting(PostResponse::content).containsExactly("alice-post");
    }

    @Test
    @DisplayName("findFollowingPageOrderByCreatedAtDesc はフォロー中ユーザーの投稿のみ返す")
    void フォロー中タイムライン() {
        User alice = insertUser("a@example.com", "alice");
        User bob = insertUser("b@example.com", "bob");
        User carol = insertUser("c@example.com", "carol");
        // alice が bob をフォロー
        com.raisetimeline.follow.Follow follow = new com.raisetimeline.follow.Follow();
        follow.setFollowerId(alice.getId());
        follow.setFolloweeId(bob.getId());
        followMapper.insert(follow);

        sut.insertAndReturn(bob.getId(), "bob-post", null, bob.getEmail());
        sut.insertAndReturn(carol.getId(), "carol-post", null, carol.getEmail());

        List<PostResponse> result =
                sut.findFollowingPageOrderByCreatedAtDesc(alice.getId(), 0, 20, alice.getEmail());

        // bob はフォロー中、carol はフォロー外なので bob の投稿のみ取得される
        assertThat(result).extracting(PostResponse::content).containsExactly("bob-post");
    }

    @Test
    @DisplayName("countNewerThan は指定日時より後の投稿を数える")
    void countNewerThan() {
        User user = insertUser("a@example.com", "alice");
        LocalDateTime before = LocalDateTime.now().minusDays(1);
        sut.insertAndReturn(user.getId(), "p1", null, user.getEmail());
        sut.insertAndReturn(user.getId(), "p2", null, user.getEmail());

        assertThat(sut.countNewerThan(before)).isEqualTo(2L);
        assertThat(sut.countNewerThan(LocalDateTime.now().plusDays(1))).isZero();
    }

    @Test
    @DisplayName("deleteById で投稿が削除される")
    void deleteById() {
        User user = insertUser("a@example.com", "alice");
        PostResponse created = sut.insertAndReturn(user.getId(), "x", null, user.getEmail());

        sut.deleteById(created.id());

        assertThat(sut.findById(created.id())).isEmpty();
    }
}
