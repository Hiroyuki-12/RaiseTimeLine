package com.raisetimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.post.dto.CreatePostRequest;
import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.post.dto.UpdatePostRequest;
import com.raisetimeline.storage.FileStorageService;
import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import java.time.LocalDateTime;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * PostService の単体テスト。
 *
 * <pre>
 * 対象: PostService (getPage / getById / countNewerThan / createPost / updatePost / deletePost)
 * 技法: 分岐網羅 + デシジョンテーブル (ユーザー存在 × 投稿存在 × 所有者) + 同値分割 (画像有無)
 *
 * getPage():
 *   [WB-1] timeline="all": 全員タイムライン Mapper を呼ぶ
 *   [WB-2] timeline="following" + ユーザー存在: フォロー中タイムライン Mapper を呼ぶ
 *   [WB-3] timeline="following" + ユーザー不在: 401
 *
 * getById():
 *   [WB-1] 投稿不在 → 404
 *   [WB-2] 投稿あり → そのまま返す
 *
 * createPost():
 *   [WB-1] ユーザー不在 → 401
 *   [BB-1] 画像なし (null) → fileStorage 呼ばず imageUrl=null で INSERT
 *   [BB-2] 画像なし (isEmpty) → 同上
 *   [BB-3] 画像あり → savePostImage を呼んで URL を渡す
 *
 * update/deletePost (デシジョンテーブル):
 *   ユーザー存在 × 投稿存在 × 所有者 → 期待結果
 *   × | -- | -- | 401
 *   ○ | ×  | -- | 404
 *   ○ | ○  | ×  | 403
 *   ○ | ○  | ○  | 成功
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostService")
class PostServiceTest {

    @Mock private PostMapper postMapper;
    @Mock private UserMapper userMapper;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private PostService sut;

    private static PostResponse samplePostResponse(Long id) {
        return new PostResponse(
                id,
                "content",
                1L,
                "alice",
                "アリス",
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                0L,
                0L,
                false);
    }

    @Nested
    @DisplayName("getPage")
    class GetPage {

        @Test
        @DisplayName("timeline=all のときは全員タイムライン Mapper を呼ぶ")
        void allタイムライン() {
            List<PostResponse> expected = List.of(samplePostResponse(1L));
            when(postMapper.findPageOrderByCreatedAtDesc(0, 20, "alice@example.com"))
                    .thenReturn(expected);

            List<PostResponse> result = sut.getPage(0, 20, "alice@example.com", "all");

            assertThat(result).isSameAs(expected);
            verify(userMapper, never()).findByEmail(anyString());
        }

        @Test
        @DisplayName("timeline=following のときはフォロー中タイムライン Mapper を呼ぶ")
        void followingタイムライン() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            List<PostResponse> expected = List.of(samplePostResponse(2L));
            when(postMapper.findFollowingPageOrderByCreatedAtDesc(
                            alice.getId(), 0, 20, "alice@example.com"))
                    .thenReturn(expected);

            List<PostResponse> result = sut.getPage(0, 20, "alice@example.com", "following");

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("timeline=following + ユーザー不在のとき 401 を投げる")
        void following時のユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getPage(0, 20, "ghost@example.com", "following"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("投稿が見つからないとき 404 を投げる")
        void 投稿不在で404() {
            when(postMapper.findByIdAsResponse(1L, "alice@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getById(1L, "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("投稿があるときは Mapper の戻り値をそのまま返す")
        void 正常取得() {
            PostResponse expected = samplePostResponse(1L);
            when(postMapper.findByIdAsResponse(1L, "alice@example.com"))
                    .thenReturn(Optional.of(expected));

            PostResponse result = sut.getById(1L, "alice@example.com");

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("countNewerThan")
    class CountNewerThan {

        @Test
        @DisplayName("Mapper の戻り値をそのまま返す")
        void 委譲() {
            LocalDateTime since = LocalDateTime.now().minusMinutes(5);
            when(postMapper.countNewerThan(since)).thenReturn(42L);

            assertThat(sut.countNewerThan(since)).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("createPost")
    class CreatePost {

        @Test
        @DisplayName("ユーザーが見つからないとき 401 を投げる")
        void ユーザー不在で401() {
            CreatePostRequest req = new CreatePostRequest("hello");
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.createPost(req, null, "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(postMapper, never()).insertAndReturn(anyLong(), anyString(), any(), anyString());
        }

        @Test
        @DisplayName("画像が null のときは fileStorage を呼ばず imageUrl=null で INSERT する")
        void 画像なし_null() {
            CreatePostRequest req = new CreatePostRequest("hello");
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            PostResponse expected = samplePostResponse(1L);
            when(postMapper.insertAndReturn(alice.getId(), "hello", null, "alice@example.com"))
                    .thenReturn(expected);

            PostResponse result = sut.createPost(req, null, "alice@example.com");

            assertThat(result).isSameAs(expected);
            verify(fileStorageService, never()).savePostImage(any());
        }

        @Test
        @DisplayName("画像が空 (isEmpty) のときは fileStorage を呼ばず imageUrl=null で INSERT する")
        void 画像なし_空() {
            CreatePostRequest req = new CreatePostRequest("hello");
            User alice = TestFixtures.aliceUser();
            MultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(postMapper.insertAndReturn(alice.getId(), "hello", null, "alice@example.com"))
                    .thenReturn(samplePostResponse(1L));

            sut.createPost(req, empty, "alice@example.com");

            verify(fileStorageService, never()).savePostImage(any());
        }

        @Test
        @DisplayName("画像があるときは savePostImage の URL を imageUrl として INSERT する")
        void 画像あり() {
            CreatePostRequest req = new CreatePostRequest("hello");
            User alice = TestFixtures.aliceUser();
            MultipartFile file =
                    new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(fileStorageService.savePostImage(file)).thenReturn("https://s3/p.png");
            when(postMapper.insertAndReturn(
                            alice.getId(), "hello", "https://s3/p.png", "alice@example.com"))
                    .thenReturn(samplePostResponse(1L));

            sut.createPost(req, file, "alice@example.com");

            verify(fileStorageService).savePostImage(file);
            verify(postMapper)
                    .insertAndReturn(
                            alice.getId(), "hello", "https://s3/p.png", "alice@example.com");
        }
    }

    @Nested
    @DisplayName("updatePost")
    class UpdatePost {

        @Test
        @DisplayName("ユーザー不在で 401")
        void ユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    sut.updatePost(
                                            1L, new UpdatePostRequest("x"), "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("投稿不在で 404")
        void 投稿不在で404() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(postMapper.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    sut.updatePost(
                                            1L, new UpdatePostRequest("x"), "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("他人の投稿を更新しようとすると 403")
        void 他人の投稿で403() {
            User alice = TestFixtures.aliceUser();
            // bob (id=2) の投稿を alice が更新しようとしている
            Post bobPost = TestFixtures.post(1L, 2L, "bobの投稿");
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(postMapper.findById(1L)).thenReturn(Optional.of(bobPost));

            assertThatThrownBy(
                            () ->
                                    sut.updatePost(
                                            1L, new UpdatePostRequest("x"), "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            verify(postMapper, never()).updateAndReturn(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("自分の投稿は updateAndReturn が呼ばれて結果を返す")
        void 自分の投稿は更新成功() {
            User alice = TestFixtures.aliceUser();
            Post alicePost = TestFixtures.post(1L, alice.getId(), "old");
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(postMapper.findById(1L)).thenReturn(Optional.of(alicePost));
            PostResponse expected = samplePostResponse(1L);
            when(postMapper.updateAndReturn(1L, "new", "alice@example.com")).thenReturn(expected);

            PostResponse result =
                    sut.updatePost(1L, new UpdatePostRequest("new"), "alice@example.com");

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("deletePost")
    class DeletePost {

        @Test
        @DisplayName("ユーザー不在で 401")
        void ユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.deletePost(1L, "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("投稿不在で 404")
        void 投稿不在で404() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(postMapper.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.deletePost(1L, "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("他人の投稿を削除しようとすると 403")
        void 他人の投稿で403() {
            User alice = TestFixtures.aliceUser();
            Post bobPost = TestFixtures.post(1L, 2L, "bobの投稿");
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(postMapper.findById(1L)).thenReturn(Optional.of(bobPost));

            assertThatThrownBy(() -> sut.deletePost(1L, "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            verify(postMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("自分の投稿は deleteById が呼ばれる")
        void 自分の投稿は削除成功() {
            User alice = TestFixtures.aliceUser();
            Post alicePost = TestFixtures.post(1L, alice.getId(), "old");
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(postMapper.findById(1L)).thenReturn(Optional.of(alicePost));

            sut.deletePost(1L, "alice@example.com");

            verify(postMapper).deleteById(1L);
        }
    }
}
