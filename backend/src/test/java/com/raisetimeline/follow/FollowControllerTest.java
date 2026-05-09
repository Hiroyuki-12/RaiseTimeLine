package com.raisetimeline.follow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.exception.GlobalExceptionHandler;
import com.raisetimeline.support.MockMvcSecurity;
import com.raisetimeline.user.dto.UserSummary;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/**
 * FollowController のスライステスト。
 *
 * <pre>
 * 対象: FollowController (POST/DELETE /follow + GET /following / GET /followers)
 * 技法: デシジョンテーブル (Service 戻り値別)
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FollowController")
class FollowControllerTest {

    private static final String CURRENT_EMAIL = "alice@example.com";

    @Mock private FollowService followService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new FollowController(followService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        MockMvcSecurity.setAuthenticatedUser(CURRENT_EMAIL);
    }

    @AfterEach
    void tearDown() {
        MockMvcSecurity.clear();
    }

    @Test
    @DisplayName("POST /follow: 正常時 204")
    void フォロー成功() throws Exception {
        mockMvc.perform(post("/api/users/2/follow")).andExpect(status().isNoContent());
        verify(followService).follow(2L, CURRENT_EMAIL);
    }

    @Test
    @DisplayName("POST /follow: 自分自身は 400")
    void 自己フォローで400() throws Exception {
        org.mockito.Mockito.doThrow(
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "自分自身をフォローすることはできません"))
                .when(followService)
                .follow(1L, CURRENT_EMAIL);

        mockMvc.perform(post("/api/users/1/follow"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /follow: 既にフォロー済みは 409")
    void 重複フォローで409() throws Exception {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "既にフォロー済みです"))
                .when(followService)
                .follow(2L, CURRENT_EMAIL);

        mockMvc.perform(post("/api/users/2/follow")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /follow: 正常時 204")
    void アンフォロー成功() throws Exception {
        mockMvc.perform(delete("/api/users/2/follow")).andExpect(status().isNoContent());
        verify(followService).unfollow(2L, CURRENT_EMAIL);
    }

    @Test
    @DisplayName("GET /following: Service の戻り値を JSON 配列として返す")
    void following一覧() throws Exception {
        when(followService.getFollowing("alice", CURRENT_EMAIL))
                .thenReturn(List.of(new UserSummary(2L, "bob", "ボブ", null, true)));

        mockMvc.perform(get("/api/users/alice/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("bob"));
    }

    @Test
    @DisplayName("GET /followers: Service の戻り値を JSON 配列として返す")
    void followers一覧() throws Exception {
        when(followService.getFollowers("alice", CURRENT_EMAIL)).thenReturn(List.of());

        mockMvc.perform(get("/api/users/alice/followers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /following: 対象ユーザー不在で 404")
    void following対象不在で404() throws Exception {
        when(followService.getFollowing("ghost", CURRENT_EMAIL))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));

        mockMvc.perform(get("/api/users/ghost/following")).andExpect(status().isNotFound());
    }
}
