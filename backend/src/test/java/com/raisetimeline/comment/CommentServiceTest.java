package com.raisetimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.comment.dto.CommentResponse;
import com.raisetimeline.comment.dto.CreateCommentRequest;
import com.raisetimeline.comment.dto.UpdateCommentRequest;
import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
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
 * CommentService の単体テスト。
 *
 * <pre>
 * 対象: CommentService (getComments / createComment / updateComment / deleteComment)
 * 技法: 分岐網羅 + デシジョンテーブル (PostService と同じ 4 パターン)
 *
 * getComments():
 *   [WB-1] Mapper 結果をそのまま返す (postId 不在でも空 List)
 *
 * createComment():
 *   [WB-1] ユーザー不在 → 401
 *   [WB-2] INSERT 直後の findById が空 → 500 (整合性異常)
 *   [WB-3] 正常 → CommentResponse を返す
 *
 * updateComment / deleteComment (デシジョンテーブル):
 *   ユーザー × コメント存在 × 所有者 → 401 / 404 / 403 / 成功
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService")
class CommentServiceTest {

    @Mock private CommentMapper commentMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private CommentService sut;

    @Nested
    @DisplayName("getComments")
    class GetComments {

        @Test
        @DisplayName("Mapper の戻り値をそのまま返す")
        void 委譲() {
            List<CommentResponse> expected = List.of();
            when(commentMapper.findByPostId(99L)).thenReturn(expected);

            assertThat(sut.getComments(99L)).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("createComment")
    class CreateComment {

        @Test
        @DisplayName("ユーザー不在で 401")
        void ユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    sut.createComment(
                                            1L,
                                            new CreateCommentRequest("hi"),
                                            "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(commentMapper, never()).insert(any());
        }

        @Test
        @DisplayName("INSERT 直後の findById が空のとき 500 を投げる (整合性異常)")
        void 整合性異常で500() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            // insert 後、commentMapper#findById が空を返すケース
            org.mockito.Mockito.doAnswer(
                            invocation -> {
                                Comment c = invocation.getArgument(0);
                                c.setId(10L);
                                return null;
                            })
                    .when(commentMapper)
                    .insert(any(Comment.class));
            when(commentMapper.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    sut.createComment(
                                            1L,
                                            new CreateCommentRequest("hi"),
                                            "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("正常時は CommentResponse を返す")
        void 正常作成() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            org.mockito.Mockito.doAnswer(
                            invocation -> {
                                Comment c = invocation.getArgument(0);
                                c.setId(10L);
                                return null;
                            })
                    .when(commentMapper)
                    .insert(any(Comment.class));
            Comment saved = TestFixtures.comment(10L, 1L, alice.getId(), "hi");
            when(commentMapper.findById(10L)).thenReturn(Optional.of(saved));

            CommentResponse result =
                    sut.createComment(1L, new CreateCommentRequest("hi"), "alice@example.com");

            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.postId()).isEqualTo(1L);
            assertThat(result.authorId()).isEqualTo(alice.getId());
            assertThat(result.content()).isEqualTo("hi");
        }
    }

    @Nested
    @DisplayName("updateComment")
    class UpdateComment {

        @Test
        @DisplayName("ユーザー不在で 401")
        void ユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    sut.updateComment(
                                            1L, new UpdateCommentRequest("x"), "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("コメント不在で 404")
        void コメント不在で404() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(commentMapper.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    sut.updateComment(
                                            1L, new UpdateCommentRequest("x"), "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("他人のコメントを編集しようとすると 403")
        void 他人のコメントで403() {
            User alice = TestFixtures.aliceUser();
            Comment bobsComment = TestFixtures.comment(1L, 5L, 2L, "bob");
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(commentMapper.findById(1L)).thenReturn(Optional.of(bobsComment));

            assertThatThrownBy(
                            () ->
                                    sut.updateComment(
                                            1L, new UpdateCommentRequest("x"), "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            verify(commentMapper, never()).update(anyLong(), anyString());
        }

        @Test
        @DisplayName("自分のコメントは update されて結果が返る")
        void 正常更新() {
            User alice = TestFixtures.aliceUser();
            Comment alicesComment = TestFixtures.comment(1L, 5L, alice.getId(), "old");
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(commentMapper.findById(1L)).thenReturn(Optional.of(alicesComment));

            CommentResponse result =
                    sut.updateComment(1L, new UpdateCommentRequest("new"), "alice@example.com");

            assertThat(result.content()).isEqualTo("new");
            verify(commentMapper).update(1L, "new");
        }
    }

    @Nested
    @DisplayName("deleteComment")
    class DeleteComment {

        @Test
        @DisplayName("ユーザー不在で 401")
        void ユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.deleteComment(1L, "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("コメント不在で 404")
        void コメント不在で404() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(commentMapper.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.deleteComment(1L, "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("他人のコメントを削除しようとすると 403")
        void 他人のコメントで403() {
            User alice = TestFixtures.aliceUser();
            Comment bobsComment = TestFixtures.comment(1L, 5L, 2L, "bob");
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(commentMapper.findById(1L)).thenReturn(Optional.of(bobsComment));

            assertThatThrownBy(() -> sut.deleteComment(1L, "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            verify(commentMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("自分のコメントは deleteById が呼ばれる")
        void 正常削除() {
            User alice = TestFixtures.aliceUser();
            Comment alicesComment = TestFixtures.comment(1L, 5L, alice.getId(), "x");
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(commentMapper.findById(1L)).thenReturn(Optional.of(alicesComment));

            sut.deleteComment(1L, "alice@example.com");

            verify(commentMapper).deleteById(1L);
        }
    }
}
