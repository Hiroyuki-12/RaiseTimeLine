package com.raisetimeline.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import com.raisetimeline.user.dto.UserSummary;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * FollowService の単体テスト。
 *
 * <pre>
 * 対象: FollowService (follow / unfollow / getFollowing / getFollowers)
 * 技法: デシジョンテーブル + エラー推測 (自己フォロー)
 *
 * follow():
 *   現在ユーザー不在 → 401
 *   フォロー対象不在 → 404
 *   自分自身 → 400 (エラー推測: 仕様で禁止)
 *   既にフォロー済 → 409
 *   それ以外 → INSERT
 *
 * unfollow():
 *   現在ユーザー不在 → 401
 *   フォロー関係なし → 404
 *   それ以外 → DELETE
 *
 * getFollowing / getFollowers:
 *   対象ユーザー不在 → 404
 *   現在ユーザー不在 → 401
 *   それ以外 → Mapper の戻り値を返す
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FollowService")
class FollowServiceTest {

    @Mock private FollowMapper followMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private FollowService sut;

    @Nested
    @DisplayName("follow")
    class Follow_ {

        @Test
        @DisplayName("現在ユーザー不在で 401")
        void 現在ユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.follow(2L, "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(followMapper, never()).insert(any());
        }

        @Test
        @DisplayName("フォロー対象が存在しないとき 404")
        void 対象不在で404() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(userMapper.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.follow(99L, "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("自分自身をフォローしようとすると 400")
        void 自己フォローで400() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(userMapper.findById(alice.getId())).thenReturn(Optional.of(alice));

            assertThatThrownBy(() -> sut.follow(alice.getId(), "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            verify(followMapper, never()).insert(any());
        }

        @Test
        @DisplayName("既にフォロー済みのとき 409")
        void 重複フォローで409() {
            User alice = TestFixtures.aliceUser();
            User bob = TestFixtures.bobUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(userMapper.findById(bob.getId())).thenReturn(Optional.of(bob));
            when(followMapper.exists(alice.getId(), bob.getId())).thenReturn(true);

            assertThatThrownBy(() -> sut.follow(bob.getId(), "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
            verify(followMapper, never()).insert(any());
        }

        @Test
        @DisplayName("正常時は Follow が INSERT される")
        void 正常フォロー() {
            User alice = TestFixtures.aliceUser();
            User bob = TestFixtures.bobUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(userMapper.findById(bob.getId())).thenReturn(Optional.of(bob));
            when(followMapper.exists(alice.getId(), bob.getId())).thenReturn(false);

            sut.follow(bob.getId(), "alice@example.com");

            verify(followMapper).insert(any(Follow.class));
        }
    }

    @Nested
    @DisplayName("unfollow")
    class Unfollow {

        @Test
        @DisplayName("現在ユーザー不在で 401")
        void 現在ユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.unfollow(2L, "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("フォロー関係が存在しないとき 404")
        void フォロー関係なしで404() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(followMapper.exists(alice.getId(), 2L)).thenReturn(false);

            assertThatThrownBy(() -> sut.unfollow(2L, "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            verify(followMapper, never()).delete(anyLong(), anyLong());
        }

        @Test
        @DisplayName("正常時は DELETE が呼ばれる")
        void 正常アンフォロー() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(followMapper.exists(alice.getId(), 2L)).thenReturn(true);

            sut.unfollow(2L, "alice@example.com");

            verify(followMapper).delete(alice.getId(), 2L);
        }
    }

    @Nested
    @DisplayName("getFollowing / getFollowers")
    class FollowList {

        @Test
        @DisplayName("getFollowing: 対象ユーザー不在で 404")
        void getFollowing対象不在で404() {
            when(userMapper.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getFollowing("ghost", "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("getFollowing: 現在ユーザー不在で 401")
        void getFollowing現在ユーザー不在で401() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getFollowing("alice", "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("getFollowing: 正常時は Mapper の戻り値を返す")
        void getFollowing正常() {
            User alice = TestFixtures.aliceUser();
            User viewer = TestFixtures.user(3L, "viewer@example.com", "viewer");
            when(userMapper.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userMapper.findByEmail("viewer@example.com")).thenReturn(Optional.of(viewer));
            List<UserSummary> expected = List.of(new UserSummary(2L, "bob", "ボブ", null, false));
            when(followMapper.findFollowing(alice.getId(), viewer.getId())).thenReturn(expected);

            assertThat(sut.getFollowing("alice", "viewer@example.com")).isSameAs(expected);
        }

        @Test
        @DisplayName("getFollowers: 対象ユーザー不在で 404")
        void getFollowers対象不在で404() {
            when(userMapper.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getFollowers("ghost", "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("getFollowers: 正常時は Mapper の戻り値を返す")
        void getFollowers正常() {
            User alice = TestFixtures.aliceUser();
            User viewer = TestFixtures.user(3L, "viewer@example.com", "viewer");
            when(userMapper.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userMapper.findByEmail("viewer@example.com")).thenReturn(Optional.of(viewer));
            List<UserSummary> expected = List.of();
            when(followMapper.findFollowers(alice.getId(), viewer.getId())).thenReturn(expected);

            assertThat(sut.getFollowers("alice", "viewer@example.com")).isSameAs(expected);
        }
    }
}
