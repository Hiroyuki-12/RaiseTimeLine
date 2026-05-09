package com.raisetimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.follow.FollowMapper;
import com.raisetimeline.post.PostMapper;
import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.storage.FileStorageService;
import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.dto.UpdateProfileRequest;
import com.raisetimeline.user.dto.UserProfileResponse;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * UserService の単体テスト。
 *
 * <pre>
 * 対象: UserService (getProfile / updateProfile / searchUsers / uploadAvatar / getUserPosts)
 * 技法: 分岐網羅 + デシジョンテーブル (対象ユーザー存在 × 現在ユーザー存在)
 *
 * getProfile():
 *   [WB-1] 対象ユーザー不在 → 404
 *   [WB-2] 現在ユーザー不在 → 401
 *   [WB-3] 自分のプロフィール → isOwnProfile=true
 *   [WB-4] 他人のプロフィール (フォロー中) → isOwnProfile=false, isFollowing=true
 *
 * updateProfile():
 *   [WB-1] 現在ユーザー不在 → 401
 *   [WB-2] ユーザー名重複 (自分以外) → 409
 *   [WB-3] bio が空白文字のみ → null として保存される (実装の三項演算子分岐)
 *   [WB-4] bio が通常値 → そのまま保存
 *
 * searchUsers():
 *   [WB-1] 現在ユーザー不在 → 401
 *   [WB-2] 正常: Mapper の戻り値がそのまま返る (自分除外は Mapper 側で実施)
 *
 * uploadAvatar():
 *   [WB-1] 現在ユーザー不在 → 401
 *   [WB-2] 正常: S3 アップロード → DB 更新 → プロフィール再取得 の順
 *
 * getUserPosts():
 *   [WB-1] 対象ユーザー不在 → 404
 *   [WB-2] 正常: Mapper の戻り値をそのまま返す
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private FollowMapper followMapper;
    @Mock private PostMapper postMapper;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private UserService sut;

    @Nested
    @DisplayName("getProfile")
    class GetProfile {

        @Test
        @DisplayName("対象ユーザーが見つからないとき 404 を投げる")
        void 対象ユーザー不在で404() {
            when(userMapper.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getProfile("ghost", "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("現在ユーザーが見つからないとき 401 を投げる")
        void 現在ユーザー不在で401() {
            User target = TestFixtures.aliceUser();
            when(userMapper.findByUsername("alice")).thenReturn(Optional.of(target));
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getProfile("alice", "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("自分のプロフィールのとき isOwnProfile=true / isFollowing=false")
        void 自分のプロフィール() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(followMapper.exists(alice.getId(), alice.getId())).thenReturn(false);
            when(followMapper.countFollowing(alice.getId())).thenReturn(3L);
            when(followMapper.countFollowers(alice.getId())).thenReturn(7L);

            UserProfileResponse res = sut.getProfile("alice", "alice@example.com");

            assertThat(res.isOwnProfile()).isTrue();
            assertThat(res.isFollowing()).isFalse();
            assertThat(res.followingCount()).isEqualTo(3L);
            assertThat(res.followerCount()).isEqualTo(7L);
        }

        @Test
        @DisplayName("他人のプロフィール (フォロー中) のとき isOwnProfile=false / isFollowing=true")
        void 他人のプロフィール_フォロー中() {
            User alice = TestFixtures.aliceUser();
            User bob = TestFixtures.bobUser();
            when(userMapper.findByUsername("bob")).thenReturn(Optional.of(bob));
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(followMapper.exists(alice.getId(), bob.getId())).thenReturn(true);

            UserProfileResponse res = sut.getProfile("bob", "alice@example.com");

            assertThat(res.isOwnProfile()).isFalse();
            assertThat(res.isFollowing()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {

        @Test
        @DisplayName("現在ユーザーが見つからないとき 401 を投げる")
        void 現在ユーザー不在で401() {
            UpdateProfileRequest req = new UpdateProfileRequest("alice", "アリス", "");
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.updateProfile(req, "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("自分以外で同じユーザー名が既に存在するとき 409 を投げる")
        void ユーザー名重複で409() {
            UpdateProfileRequest req = new UpdateProfileRequest("bob", "アリス", "");
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(userMapper.existsByUsernameExcludingSelf("bob", alice.getId())).thenReturn(true);

            assertThatThrownBy(() -> sut.updateProfile(req, "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
            verify(userMapper, never()).update(alice);
        }

        @Test
        @DisplayName("bio が空白のみのとき null に正規化して保存する")
        void bio空白はnullに変換() {
            UpdateProfileRequest req = new UpdateProfileRequest("alice", "アリス", "   ");
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(userMapper.existsByUsernameExcludingSelf("alice", alice.getId()))
                    .thenReturn(false);
            // updateProfile は最後に getProfile を呼ぶため、必要なスタブを用意する
            when(userMapper.findByUsername("alice")).thenReturn(Optional.of(alice));

            sut.updateProfile(req, "alice@example.com");

            // bio が null に変換されて User オブジェクトに反映されること
            assertThat(alice.getBio()).isNull();
            verify(userMapper).update(alice);
        }

        @Test
        @DisplayName("bio が通常値のときはそのまま保存する")
        void bio通常値はそのまま保存() {
            UpdateProfileRequest req = new UpdateProfileRequest("alice", "アリス", "Hello!");
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(userMapper.existsByUsernameExcludingSelf("alice", alice.getId()))
                    .thenReturn(false);
            when(userMapper.findByUsername("alice")).thenReturn(Optional.of(alice));

            sut.updateProfile(req, "alice@example.com");

            assertThat(alice.getBio()).isEqualTo("Hello!");
            verify(userMapper).update(alice);
        }
    }

    @Nested
    @DisplayName("searchUsers")
    class SearchUsers {

        @Test
        @DisplayName("現在ユーザーが見つからないとき 401 を投げる")
        void 現在ユーザー不在で401() {
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.searchUsers("a", "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正常時は Mapper の戻り値をそのまま返す")
        void 正常検索() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            List<UserSummary> expected = List.of(new UserSummary(2L, "bob", "ボブ", null, false));
            when(userMapper.findByUsernameContaining("b", alice.getId())).thenReturn(expected);

            List<UserSummary> result = sut.searchUsers("b", "alice@example.com");

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("uploadAvatar")
    class UploadAvatar {

        @Test
        @DisplayName("現在ユーザーが見つからないとき 401 を投げる")
        void 現在ユーザー不在で401() {
            MockMultipartFile file =
                    new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
            when(userMapper.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.uploadAvatar(file, "ghost@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            // 認証失敗時は S3 にアップロードしないこと
            verify(fileStorageService, never()).saveAvatar(file);
        }

        @Test
        @DisplayName("正常時は S3 アップロード → DB 更新 → プロフィール返却 の順で実行される")
        void 正常アップロード() {
            MockMultipartFile file =
                    new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
            when(fileStorageService.saveAvatar(file)).thenReturn("https://s3/alice.png");
            when(userMapper.findByUsername("alice")).thenReturn(Optional.of(alice));

            sut.uploadAvatar(file, "alice@example.com");

            verify(fileStorageService).saveAvatar(file);
            verify(userMapper).updateAvatarUrl(alice.getId(), "https://s3/alice.png");
        }
    }

    @Nested
    @DisplayName("getUserPosts")
    class GetUserPosts {

        @Test
        @DisplayName("対象ユーザーが見つからないとき 404 を投げる")
        void 対象ユーザー不在で404() {
            when(userMapper.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getUserPosts("ghost", "alice@example.com"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("正常時は Mapper から取得した一覧をそのまま返す")
        void 正常取得() {
            User alice = TestFixtures.aliceUser();
            when(userMapper.findByUsername("alice")).thenReturn(Optional.of(alice));
            List<PostResponse> expected = List.of();
            when(postMapper.findByUserIdOrderByCreatedAtDesc(alice.getId(), "viewer@example.com"))
                    .thenReturn(expected);

            List<PostResponse> result = sut.getUserPosts("alice", "viewer@example.com");

            assertThat(result).isSameAs(expected);
        }
    }
}
