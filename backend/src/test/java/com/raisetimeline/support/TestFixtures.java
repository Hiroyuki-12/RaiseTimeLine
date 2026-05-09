package com.raisetimeline.support;

import com.raisetimeline.auth.RefreshToken;
import com.raisetimeline.comment.Comment;
import com.raisetimeline.post.Post;
import com.raisetimeline.user.User;
import java.time.LocalDateTime;

/**
 * Service 単体テスト用のフィクスチャ生成ヘルパー。
 *
 * <p>各テストでエンティティを new して setter を並べると視認性が落ちるため、 「最低限の値が埋まったエンティティ」を返すユーティリティをここに集約する。
 * テストごとに必要な項目だけ上書きすれば良いように、デフォルト値は無難な値にしてある。
 */
public final class TestFixtures {

    private TestFixtures() {}

    /** テスト用ユーザーを生成する。id と email/username を指定して識別性を出す。 */
    public static User user(Long id, String email, String username) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(username);
        user.setDisplayName(username + "さん");
        user.setPasswordHash("$2a$10$dummyHashForTest");
        user.setBio(null);
        user.setAvatarUrl(null);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    /** デフォルト値のテストユーザー（id=1, alice）。 */
    public static User aliceUser() {
        return user(1L, "alice@example.com", "alice");
    }

    /** 別ユーザー（id=2, bob）。所有者検証テストで「他人」として使う。 */
    public static User bobUser() {
        return user(2L, "bob@example.com", "bob");
    }

    /** 投稿エンティティを生成する。 */
    public static Post post(Long id, Long userId, String content) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setContent(content);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        return post;
    }

    /** コメントエンティティを生成する。 */
    public static Comment comment(Long id, Long postId, Long userId, String content) {
        Comment c = new Comment();
        c.setId(id);
        c.setPostId(postId);
        c.setUserId(userId);
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    /** リフレッシュトークンエンティティを生成する。expiresAt は未来に設定する。 */
    public static RefreshToken refreshToken(Long userId, String token, LocalDateTime expiresAt) {
        RefreshToken rt = new RefreshToken();
        rt.setId(100L);
        rt.setUserId(userId);
        rt.setToken(token);
        rt.setExpiresAt(expiresAt);
        rt.setCreatedAt(LocalDateTime.now());
        return rt;
    }
}
