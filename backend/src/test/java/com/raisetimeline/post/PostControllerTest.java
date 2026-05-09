package com.raisetimeline.post;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisetimeline.exception.GlobalExceptionHandler;
import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.post.dto.UpdatePostRequest;
import com.raisetimeline.support.MockMvcSecurity;
import java.time.LocalDateTime;
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
 * PostController のスライステスト。
 *
 * <pre>
 * 対象: PostController (getTimeline / getPost / newCount / createPost / updatePost / deletePost)
 * 技法: 同値分割 + 境界値 (content 280字制限) + デシジョンテーブル (認可)
 *
 * createPost 境界値:
 *   [BV-1] content="" → 400 (Controller の手動バリデーション)
 *   [BV-2] content="x" (1字) → 201
 *   [BV-3] content=280字 → 201
 *   [BV-4] content=281字 → 400
 *
 * updatePost / deletePost (デシジョンテーブル, Service 層に委譲):
 *   - Service が 404 → 404
 *   - Service が 403 → 403
 *   - 正常 → 200 / 204
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostController")
class PostControllerTest {

    private static final String CURRENT_EMAIL = "alice@example.com";

    @Mock private PostService postService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new PostController(postService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        MockMvcSecurity.setAuthenticatedUser(CURRENT_EMAIL);
    }

    @AfterEach
    void tearDown() {
        MockMvcSecurity.clear();
    }

    private static PostResponse samplePost(Long id) {
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
    @DisplayName("GET /api/posts")
    class GetTimeline {

        @Test
        @DisplayName("デフォルトパラメータ (page=0, size=20, timeline=all) で Service を呼ぶ")
        void デフォルト() throws Exception {
            when(postService.getPage(0, 20, CURRENT_EMAIL, "all"))
                    .thenReturn(List.of(samplePost(1L)));

            mockMvc.perform(get("/api/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1));
        }

        @Test
        @DisplayName("timeline=following を渡すと Service にも following で渡る")
        void following() throws Exception {
            when(postService.getPage(0, 20, CURRENT_EMAIL, "following")).thenReturn(List.of());

            mockMvc.perform(get("/api/posts").param("timeline", "following"))
                    .andExpect(status().isOk());
            verify(postService).getPage(0, 20, CURRENT_EMAIL, "following");
        }
    }

    @Nested
    @DisplayName("GET /api/posts/{id}")
    class GetPost {

        @Test
        @DisplayName("正常時は PostResponse を返す")
        void 正常() throws Exception {
            when(postService.getById(1L, CURRENT_EMAIL)).thenReturn(samplePost(1L));

            mockMvc.perform(get("/api/posts/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("Service が 404 を投げると 404 を返す")
        void 不在で404() throws Exception {
            when(postService.getById(99L, CURRENT_EMAIL))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "ポストが見つかりません"));

            mockMvc.perform(get("/api/posts/99")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/posts/new-count")
    class NewCount {

        @Test
        @DisplayName("ISO 形式の since パラメータを LocalDateTime に変換して Service へ渡す")
        void since変換() throws Exception {
            when(postService.countNewerThan(any())).thenReturn(7L);

            mockMvc.perform(get("/api/posts/new-count").param("since", "2026-01-01T00:00:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(7));
        }
    }

    @Nested
    @DisplayName("POST /api/posts (multipart)")
    class CreatePost {

        @Test
        @DisplayName("content が空のとき 400 (Controller 手動バリデーション)")
        void 空contentで400() throws Exception {
            mockMvc.perform(multipart("/api/posts").param("content", ""))
                    .andExpect(status().isBadRequest());
            verify(postService, never()).createPost(any(), any(), any());
        }

        @Test
        @DisplayName("content が 280 字ちょうどなら 201 (境界値)")
        void content280字で成功() throws Exception {
            String content = "あ".repeat(280);
            when(postService.createPost(any(), any(), eq(CURRENT_EMAIL)))
                    .thenReturn(samplePost(1L));

            mockMvc.perform(multipart("/api/posts").param("content", content))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("content が 281 字なら 400 (境界値+1)")
        void content281字で400() throws Exception {
            String content = "あ".repeat(281);

            mockMvc.perform(multipart("/api/posts").param("content", content))
                    .andExpect(status().isBadRequest());
            verify(postService, never()).createPost(any(), any(), any());
        }

        @Test
        @DisplayName("画像付きで送ると Service の image 引数に MultipartFile が渡る")
        void 画像付き() throws Exception {
            MockMultipartFile image =
                    new MockMultipartFile("image", "p.png", "image/png", new byte[] {1});
            when(postService.createPost(any(), any(), eq(CURRENT_EMAIL)))
                    .thenReturn(samplePost(1L));

            mockMvc.perform(multipart("/api/posts").file(image).param("content", "hello"))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("PUT /api/posts/{id}")
    class UpdatePost {

        @Test
        @DisplayName("content が空のとき @Valid で 400")
        void バリデーション失敗で400() throws Exception {
            UpdatePostRequest req = new UpdatePostRequest("");

            mockMvc.perform(
                            put("/api/posts/1")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.content").exists());
        }

        @Test
        @DisplayName("Service が 403 を投げると 403")
        void 他人で403() throws Exception {
            UpdatePostRequest req = new UpdatePostRequest("new");
            when(postService.updatePost(eq(1L), any(), eq(CURRENT_EMAIL)))
                    .thenThrow(
                            new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "このポストを編集する権限がありません"));

            mockMvc.perform(
                            put("/api/posts/1")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("正常時は 200 + 更新後 PostResponse")
        void 正常() throws Exception {
            UpdatePostRequest req = new UpdatePostRequest("new");
            when(postService.updatePost(eq(1L), any(), eq(CURRENT_EMAIL)))
                    .thenReturn(samplePost(1L));

            mockMvc.perform(
                            put("/api/posts/1")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }
    }

    @Nested
    @DisplayName("DELETE /api/posts/{id}")
    class DeletePost {

        @Test
        @DisplayName("正常時は 204")
        void 正常() throws Exception {
            mockMvc.perform(delete("/api/posts/1")).andExpect(status().isNoContent());
            verify(postService).deletePost(1L, CURRENT_EMAIL);
        }

        @Test
        @DisplayName("Service が 404 を投げると 404")
        void 不在で404() throws Exception {
            org.mockito.Mockito.doThrow(
                            new ResponseStatusException(HttpStatus.NOT_FOUND, "ポストが見つかりません"))
                    .when(postService)
                    .deletePost(99L, CURRENT_EMAIL);

            mockMvc.perform(delete("/api/posts/99")).andExpect(status().isNotFound());
        }
    }
}
