package com.raisetimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.comment.dto.CommentResponse;
import com.raisetimeline.post.PostMapper;
import com.raisetimeline.support.AbstractMapperIntegrationTest;
import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CommentMapper の DB 統合テスト。
 *
 * <pre>
 * 対象: CommentMapper (insert / findByPostId / findById / update / deleteById)
 * 技法: 件数の境界値 (0/1/多) + JOIN 検証 (ユーザー情報が CommentResponse に入ること) + ORDER BY ASC
 * </pre>
 */
@DisplayName("CommentMapper")
class CommentMapperTest extends AbstractMapperIntegrationTest {

    @Autowired private CommentMapper sut;
    @Autowired private UserMapper userMapper;
    @Autowired private PostMapper postMapper;

    private User insertUser(String email, String username) {
        User u = TestFixtures.user(null, email, username);
        u.setId(null);
        userMapper.insert(u);
        return u;
    }

    private long insertPost(User user, String content) {
        return postMapper.insertAndReturn(user.getId(), content, null, user.getEmail()).id();
    }

    @Test
    @DisplayName("insert で id が自動採番される")
    void insertで自動採番() {
        User user = insertUser("a@example.com", "alice");
        long postId = insertPost(user, "post");
        Comment comment = TestFixtures.comment(null, postId, user.getId(), "hi");
        comment.setId(null);

        sut.insert(comment);

        assertThat(comment.getId()).isNotNull();
    }

    @Test
    @DisplayName("findByPostId は対象投稿のコメントのみ古い順で返す (件数 0/1/多 の境界値)")
    void findByPostId件数() {
        User alice = insertUser("a@example.com", "alice");
        User bob = insertUser("b@example.com", "bob");
        long post1 = insertPost(alice, "p1");
        long post2 = insertPost(alice, "p2");

        // post1 に 2 件、post2 に 1 件、別投稿は除外される
        Comment c1 = TestFixtures.comment(null, post1, alice.getId(), "first");
        c1.setId(null);
        sut.insert(c1);
        Comment c2 = TestFixtures.comment(null, post1, bob.getId(), "second");
        c2.setId(null);
        sut.insert(c2);
        Comment c3 = TestFixtures.comment(null, post2, alice.getId(), "other-post");
        c3.setId(null);
        sut.insert(c3);

        List<CommentResponse> result = sut.findByPostId(post1);

        assertThat(result).hasSize(2);
        // 古い順 (created_at ASC) で取得されること
        assertThat(result.get(0).content()).isEqualTo("first");
        assertThat(result.get(1).content()).isEqualTo("second");
        // JOIN したユーザー情報が含まれること
        assertThat(result.get(0).authorUsername()).isEqualTo("alice");
        assertThat(result.get(1).authorUsername()).isEqualTo("bob");
    }

    @Test
    @DisplayName("findByPostId: 0 件のとき空リスト")
    void 件数0() {
        assertThat(sut.findByPostId(99999L)).isEmpty();
    }

    @Test
    @DisplayName("findById: 存在するコメントを取得 / 不在は Optional.empty")
    void findById() {
        User user = insertUser("a@example.com", "alice");
        long postId = insertPost(user, "p");
        Comment c = TestFixtures.comment(null, postId, user.getId(), "x");
        c.setId(null);
        sut.insert(c);

        Optional<Comment> found = sut.findById(c.getId());

        assertThat(found).isPresent();
        assertThat(sut.findById(99999L)).isEmpty();
    }

    @Test
    @DisplayName("update で content が更新される")
    void update() {
        User user = insertUser("a@example.com", "alice");
        long postId = insertPost(user, "p");
        Comment c = TestFixtures.comment(null, postId, user.getId(), "old");
        c.setId(null);
        sut.insert(c);

        sut.update(c.getId(), "new");

        assertThat(sut.findById(c.getId()).get().getContent()).isEqualTo("new");
    }

    @Test
    @DisplayName("deleteById でコメントが削除される")
    void deleteById() {
        User user = insertUser("a@example.com", "alice");
        long postId = insertPost(user, "p");
        Comment c = TestFixtures.comment(null, postId, user.getId(), "x");
        c.setId(null);
        sut.insert(c);

        sut.deleteById(c.getId());

        assertThat(sut.findById(c.getId())).isEmpty();
    }

    @Test
    @DisplayName("FK CASCADE: 投稿削除でコメントも消える")
    void cascade削除() {
        User user = insertUser("a@example.com", "alice");
        long postId = insertPost(user, "p");
        Comment c = TestFixtures.comment(null, postId, user.getId(), "x");
        c.setId(null);
        sut.insert(c);

        postMapper.deleteById(postId);

        // V5 マイグレーションの ON DELETE CASCADE により自動削除される
        assertThat(sut.findById(c.getId())).isEmpty();
    }
}
