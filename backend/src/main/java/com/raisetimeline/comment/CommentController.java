package com.raisetimeline.comment;

import com.raisetimeline.comment.dto.CommentResponse;
import com.raisetimeline.comment.dto.CreateCommentRequest;
import com.raisetimeline.comment.dto.UpdateCommentRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * コメントの CRUD に関する HTTP リクエストを受け付けるコントローラー。 SecurityConfig の anyRequest().authenticated() により、
 * すべてのエンドポイントで JWT 認証が必須になる。
 */
@RestController
public class CommentController {

    private final CommentService commentService;

    /** コンストラクタ。Spring が CommentService を自動的に注入する。 */
    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * コメント一覧取得エンドポイント。 GET /api/posts/{postId}/comments 指定した投稿のコメントを古い順で返す。
     *
     * @param postId パスパラメータ（コメントを取得する投稿の ID）
     * @return 200 OK + CommentResponse のリスト
     */
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getComments(postId));
    }

    /**
     * コメント作成エンドポイント。 POST /api/posts/{postId}/comments JWT から取得したメールアドレスでコメント投稿者を特定し、コメントを作成する。
     *
     * @param postId パスパラメータ（コメント対象の投稿 ID）
     * @param request コメント本文を含むリクエスト DTO
     * @return 201 Created + 作成した CommentResponse
     */
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long postId, @Valid @RequestBody CreateCommentRequest request) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        CommentResponse response = commentService.createComment(postId, request, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * コメント更新エンドポイント。 PUT /api/comments/{id} コメント投稿者本人のみ編集できる。他ユーザーが試みた場合は 403 を返す。
     *
     * @param id パスパラメータ（更新するコメントの ID）
     * @param request 更新内容を含むリクエスト DTO
     * @return 200 OK + 更新後の CommentResponse
     */
    @PutMapping("/api/comments/{id}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long id, @Valid @RequestBody UpdateCommentRequest request) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        CommentResponse response = commentService.updateComment(id, request, email);
        return ResponseEntity.ok(response);
    }

    /**
     * コメント削除エンドポイント。 DELETE /api/comments/{id} コメント投稿者本人のみ削除できる。他ユーザーが試みた場合は 403 を返す。
     *
     * @param id パスパラメータ（削除するコメントの ID）
     * @return 204 No Content
     */
    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        commentService.deleteComment(id, email);
        return ResponseEntity.noContent().build();
    }
}
