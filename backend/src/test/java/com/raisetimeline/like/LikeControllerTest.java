package com.raisetimeline.like;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.exception.GlobalExceptionHandler;
import com.raisetimeline.support.MockMvcSecurity;
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
 * LikeController のスライステスト。
 *
 * <pre>
 * 対象: LikeController (POST /api/likes / DELETE /api/likes/{postId})
 * 技法: 同値分割 (postId 必須) + デシジョンテーブル (Service 戻り値)
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LikeController")
class LikeControllerTest {

    private static final String CURRENT_EMAIL = "alice@example.com";

    @Mock private LikeService likeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new LikeController(likeService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        MockMvcSecurity.setAuthenticatedUser(CURRENT_EMAIL);
    }

    @AfterEach
    void tearDown() {
        MockMvcSecurity.clear();
    }

    @Test
    @DisplayName("POST /api/likes: postId なしで 400 (@Valid)")
    void postIdなしで400() throws Exception {
        mockMvc.perform(post("/api/likes").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.postId").exists());
        verify(likeService, never()).addLike(any(), any());
    }

    @Test
    @DisplayName("POST /api/likes: 正常時 204")
    void いいね追加成功() throws Exception {
        mockMvc.perform(
                        post("/api/likes")
                                .contentType("application/json")
                                .content("{\"postId\":1}"))
                .andExpect(status().isNoContent());
        verify(likeService).addLike(1L, CURRENT_EMAIL);
    }

    @Test
    @DisplayName("POST /api/likes: 既にいいね済み (Service 409) → 409")
    void 重複で409() throws Exception {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "既にいいね済みです"))
                .when(likeService)
                .addLike(1L, CURRENT_EMAIL);

        mockMvc.perform(
                        post("/api/likes")
                                .contentType("application/json")
                                .content("{\"postId\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /api/likes/{postId}: 正常時 204")
    void いいね取消成功() throws Exception {
        mockMvc.perform(delete("/api/likes/1")).andExpect(status().isNoContent());
        verify(likeService).removeLike(1L, CURRENT_EMAIL);
    }

    @Test
    @DisplayName("DELETE /api/likes/{postId}: いいね不在で 404")
    void いいね不在で404() throws Exception {
        org.mockito.Mockito.doThrow(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "いいねが見つかりません"))
                .when(likeService)
                .removeLike(1L, CURRENT_EMAIL);

        mockMvc.perform(delete("/api/likes/1")).andExpect(status().isNotFound());
    }
}
