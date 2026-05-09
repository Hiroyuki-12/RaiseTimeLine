package com.raisetimeline.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.raisetimeline.post.PostMapper;
import com.raisetimeline.support.AbstractMapperIntegrationTest;
import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * LikeMapper の DB 統合テスト。
 *
 * <pre>
 * 対象: LikeMapper (insert / delete / exists)
 * 技法: 状態遷移 (未いいね ↔ いいね済) + ON CONFLICT DO NOTHING の冪等性検証
 *
 * 検証ポイント:
 * - insert で行が増えること
 * - 重複 insert で例外が出ないこと (ON CONFLICT DO NOTHING; アプリ層で 409 を返す前のセーフネット)
 * - exists が true/false を正しく返すこと
 * - delete で行が消えること
 * </pre>
 */
@DisplayName("LikeMapper")
class LikeMapperTest extends AbstractMapperIntegrationTest {

    @Autowired private LikeMapper sut;
    @Autowired private UserMapper userMapper;
    @Autowired private PostMapper postMapper;

    private User insertUser(String email, String username) {
        User u = TestFixtures.user(null, email, username);
        u.setId(null);
        userMapper.insert(u);
        return u;
    }

    private long insertPost(User user) {
        return postMapper.insertAndReturn(user.getId(), "post", null, user.getEmail()).id();
    }

    @Test
    @DisplayName("insert + exists: いいねが登録され存在確認できる")
    void いいね追加() {
        User user = insertUser("a@example.com", "alice");
        long postId = insertPost(user);
        Like like = new Like();
        like.setPostId(postId);
        like.setUserId(user.getId());

        sut.insert(like);

        assertThat(sut.exists(postId, user.getId())).isTrue();
    }

    @Test
    @DisplayName("ON CONFLICT DO NOTHING: 重複 insert は例外を出さず無視される")
    void 重複insert冪等() {
        User user = insertUser("a@example.com", "alice");
        long postId = insertPost(user);
        Like first = new Like();
        first.setPostId(postId);
        first.setUserId(user.getId());
        sut.insert(first);

        // 同一 (post_id, user_id) で再 insert してもエラーにならない
        Like second = new Like();
        second.setPostId(postId);
        second.setUserId(user.getId());

        assertThatCode(() -> sut.insert(second)).doesNotThrowAnyException();
        assertThat(sut.exists(postId, user.getId())).isTrue();
    }

    @Test
    @DisplayName("exists: 未いいねの組み合わせは false")
    void exists未いいね() {
        User user = insertUser("a@example.com", "alice");
        long postId = insertPost(user);

        assertThat(sut.exists(postId, user.getId())).isFalse();
    }

    @Test
    @DisplayName("delete でいいねが消える / 二重削除も例外なし")
    void いいね取消() {
        User user = insertUser("a@example.com", "alice");
        long postId = insertPost(user);
        Like like = new Like();
        like.setPostId(postId);
        like.setUserId(user.getId());
        sut.insert(like);

        sut.delete(postId, user.getId());
        assertThat(sut.exists(postId, user.getId())).isFalse();

        // 既に削除済みの状態で再度 delete してもエラーにならない (DELETE は冪等)
        assertThatCode(() -> sut.delete(postId, user.getId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FK CASCADE: 投稿削除でいいねも消える")
    void cascade削除() {
        User user = insertUser("a@example.com", "alice");
        long postId = insertPost(user);
        Like like = new Like();
        like.setPostId(postId);
        like.setUserId(user.getId());
        sut.insert(like);

        postMapper.deleteById(postId);

        // V6 マイグレーションの ON DELETE CASCADE により自動削除される
        assertThat(sut.exists(postId, user.getId())).isFalse();
    }
}
