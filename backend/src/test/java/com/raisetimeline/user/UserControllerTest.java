package com.raisetimeline.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisetimeline.exception.GlobalExceptionHandler;
import com.raisetimeline.support.MockMvcSecurity;
import com.raisetimeline.user.dto.UpdateProfileRequest;
import com.raisetimeline.user.dto.UserProfileResponse;
import com.raisetimeline.user.dto.UserSummary;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/**
 * UserController のスライステスト。
 *
 * <pre>
 * 対象: UserController (search / avatar / getProfile / updateProfile / userPosts)
 * 技法: 同値分割 (バリデーション) + デシジョンテーブル (認可・存在チェック)
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserController")
class UserControllerTest {

    private static final String CURRENT_EMAIL = "alice@example.com";

    @Mock private UserService userService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(userService);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        // 各テストで「認証済み」状態を再現する
        MockMvcSecurity.setAuthenticatedUser(CURRENT_EMAIL);
    }

    @AfterEach
    void tearDown() {
        MockMvcSecurity.clear();
    }

    @Nested
    @DisplayName("GET /api/users/search")
    class Search {

        @Test
        @DisplayName("Service の戻り値を JSON 配列として返す")
        void 正常検索() throws Exception {
            when(userService.searchUsers("a", CURRENT_EMAIL))
                    .thenReturn(List.of(new UserSummary(2L, "bob", "ボブ", null, false)));

            mockMvc.perform(get("/api/users/search").param("q", "a"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].username").value("bob"));
        }
    }

    @Nested
    @DisplayName("POST /api/users/me/avatar")
    class UploadAvatar {

        @Test
        @DisplayName("multipart で file を送ると Service が呼ばれ 200 を返す")
        void 正常アップロード() throws Exception {
            MockMultipartFile file =
                    new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});
            when(userService.uploadAvatar(any(), eq(CURRENT_EMAIL))).thenReturn(profile("alice"));

            mockMvc.perform(multipart("/api/users/me/avatar").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("alice"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/{username}")
    class GetProfile {

        @Test
        @DisplayName("正常時はプロフィールを返す")
        void 正常() throws Exception {
            when(userService.getProfile("alice", CURRENT_EMAIL)).thenReturn(profile("alice"));

            mockMvc.perform(get("/api/users/alice"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("alice"));
        }

        @Test
        @DisplayName("Service が 404 を投げると 404 + message")
        void 不在で404() throws Exception {
            when(userService.getProfile("ghost", CURRENT_EMAIL))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));

            mockMvc.perform(get("/api/users/ghost"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me")
    class UpdateProfile {

        @Test
        @DisplayName("username 形式違反のとき 400 + errors.username")
        void バリデーション違反で400() throws Exception {
            // 日本語 (許可されない) を含む username
            UpdateProfileRequest req = new UpdateProfileRequest("アリス", "アリス", "");

            mockMvc.perform(
                            put("/api/users/me")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.username").exists());
            verify(userService, never()).updateProfile(any(), any());
        }

        @Test
        @DisplayName("ユーザー名重複で 409")
        void 重複で409() throws Exception {
            UpdateProfileRequest req = new UpdateProfileRequest("bob", "ボブ", "");
            when(userService.updateProfile(any(), eq(CURRENT_EMAIL)))
                    .thenThrow(
                            new ResponseStatusException(HttpStatus.CONFLICT, "このユーザー名は既に使用されています"));

            mockMvc.perform(
                            put("/api/users/me")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("正常時は 200 + 更新後プロフィール")
        void 正常更新() throws Exception {
            UpdateProfileRequest req = new UpdateProfileRequest("alice2", "アリス改", "Hello");
            when(userService.updateProfile(any(), eq(CURRENT_EMAIL))).thenReturn(profile("alice2"));

            mockMvc.perform(
                            put("/api/users/me")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("alice2"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/{username}/posts")
    class UserPosts {

        @Test
        @DisplayName("正常時は配列を返す")
        void 正常() throws Exception {
            when(userService.getUserPosts("alice", CURRENT_EMAIL)).thenReturn(List.of());

            mockMvc.perform(get("/api/users/alice/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    private static UserProfileResponse profile(String username) {
        return new UserProfileResponse(1L, username, "表示名", null, null, 0L, 0L, false, true);
    }
}
