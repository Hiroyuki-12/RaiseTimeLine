package com.raisetimeline.like;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
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
 * LikeService の単体テスト。
 *
 * <pre>
 * 対象: LikeService (addLike / removeLike)
 * 技法: 状態遷移 (未いいね ↔ いいね済) + 分岐網羅
 *
 * 状態遷移テーブル:
 *   現在状態 | 操作       | 期待結果
 *   未いいね | addLike    | INSERT 実行
 *   いいね済 | addLike    | 409 Conflict (重複防止)
 *   いいね済 | removeLike | DELETE 実行
 *   未いいね 　| removeLike | 404 Not Found
 *
 * いずれもユーザー不在は 401。
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LikeService")
class LikeServiceTest {

    @Mock private LikeMapper likeMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private LikeService sut;

    @Nested
    @DisplayName("addLike")
    class AddLike {

        @Test
        @DisplayName("ユーザー不在で 401")
        void ユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.addLike(1L, "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(likeMapper, never()).insert(any());
        }

        @Test
        @DisplayName("既にいいね済みのとき 409 を投げる")
        void 重複いいねで409() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(likeMapper.exists(1L, alice.getId())).thenReturn(true);

            assertThatThrownBy(() -> sut.addLike(1L, "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
            verify(likeMapper, never()).insert(any());
        }

        @Test
        @DisplayName("未いいね状態のときは INSERT が呼ばれる")
        void 正常いいね追加() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(likeMapper.exists(1L, alice.getId())).thenReturn(false);

            sut.addLike(1L, "alice@example.com");

            verify(likeMapper).insert(any(Like.class));
        }
    }

    @Nested
    @DisplayName("removeLike")
    class RemoveLike {

        @Test
        @DisplayName("ユーザー不在で 401")
        void ユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.removeLike(1L, "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("いいねが存在しないとき 404 を投げる")
        void いいね不在で404() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(likeMapper.exists(1L, alice.getId())).thenReturn(false);

            assertThatThrownBy(() -> sut.removeLike(1L, "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            verify(likeMapper, never()).delete(anyLong(), anyLong());
        }

        @Test
        @DisplayName("いいね済みのときは DELETE が呼ばれる")
        void 正常いいね取消() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(likeMapper.exists(1L, alice.getId())).thenReturn(true);

            sut.removeLike(1L, "alice@example.com");

            verify(likeMapper).delete(1L, alice.getId());
        }
    }
}
