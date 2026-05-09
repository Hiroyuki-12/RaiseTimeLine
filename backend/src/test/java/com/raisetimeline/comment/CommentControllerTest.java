package com.raisetimeline.comment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisetimeline.comment.dto.CommentResponse;
import com.raisetimeline.comment.dto.CreateCommentRequest;
import com.raisetimeline.comment.dto.UpdateCommentRequest;
import com.raisetimeline.exception.GlobalExceptionHandler;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/**
 * CommentController のスライステスト。
 *
 * <pre>
 * 対象: CommentController (list / create / update / delete)
 * 技法: 同値分割 + 境界値 (140字制限) + デシジョンテーブル (認可)
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentController")
class CommentControllerTest {

    private static final String CURRENT_EMAIL = "alice@example.com";

    @Mock private CommentService commentService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new CommentController(commentService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        MockMvcSecurity.setAuthenticatedUser(CURRENT_EMAIL);
    }

    @AfterEach
    void tearDown() {
        MockMvcSecurity.clear();
    }

    private static CommentResponse sample(Long id) {
        return new CommentResponse(id, 1L, 1L, "alice", "アリス", "hi", LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /api/posts/{postId}/comments")
    class List_ {

        @Test
        @DisplayName("Service の戻り値を JSON 配列として返す")
        void 正常() throws Exception {
            when(commentService.getComments(1L)).thenReturn(List.of(sample(10L)));

            mockMvc.perform(get("/api/posts/1/comments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(10));
        }
    }

    @Nested
    @DisplayName("POST /api/posts/{postId}/comments")
    class Create {

        @Test
        @DisplayName("content が空のとき @Valid で 400 + errors.content")
        void バリデーション失敗で400() throws Exception {
            CreateCommentRequest req = new CreateCommentRequest("");

            mockMvc.perform(
                            post("/api/posts/1/comments")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.content").exists());
            verify(commentService, never()).createComment(any(), any(), any());
        }

        @Test
        @DisplayName("content が 141 字なら 400 (境界値+1)")
        void content141字で400() throws Exception {
            CreateCommentRequest req = new CreateCommentRequest("あ".repeat(141));

            mockMvc.perform(
                            post("/api/posts/1/comments")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("正常時は 201 + CommentResponse")
        void 正常() throws Exception {
            CreateCommentRequest req = new CreateCommentRequest("hi");
            when(commentService.createComment(eq(1L), any(), eq(CURRENT_EMAIL)))
                    .thenReturn(sample(10L));

            mockMvc.perform(
                            post("/api/posts/1/comments")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10));
        }
    }

    @Nested
    @DisplayName("PUT /api/comments/{id}")
    class Update {

        @Test
        @DisplayName("Service が 403 を投げると 403")
        void 他人のコメントで403() throws Exception {
            UpdateCommentRequest req = new UpdateCommentRequest("new");
            when(commentService.updateComment(eq(1L), any(), eq(CURRENT_EMAIL)))
                    .thenThrow(
                            new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "このコメントを編集する権限がありません"));

            mockMvc.perform(
                            put("/api/comments/1")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("正常時は 200 + 更新後 CommentResponse")
        void 正常() throws Exception {
            UpdateCommentRequest req = new UpdateCommentRequest("new");
            when(commentService.updateComment(eq(1L), any(), eq(CURRENT_EMAIL)))
                    .thenReturn(sample(1L));

            mockMvc.perform(
                            put("/api/comments/1")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }
    }

    @Nested
    @DisplayName("DELETE /api/comments/{id}")
    class Delete {

        @Test
        @DisplayName("正常時は 204")
        void 正常() throws Exception {
            mockMvc.perform(delete("/api/comments/1")).andExpect(status().isNoContent());
            verify(commentService).deleteComment(1L, CURRENT_EMAIL);
        }

        @Test
        @DisplayName("Service が 404 を投げると 404")
        void 不在で404() throws Exception {
            org.mockito.Mockito.doThrow(
                            new ResponseStatusException(HttpStatus.NOT_FOUND, "コメントが見つかりません"))
                    .when(commentService)
                    .deleteComment(99L, CURRENT_EMAIL);

            mockMvc.perform(delete("/api/comments/99")).andExpect(status().isNotFound());
        }
    }
}
