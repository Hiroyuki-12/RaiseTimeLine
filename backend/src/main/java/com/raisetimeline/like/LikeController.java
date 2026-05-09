package com.raisetimeline.like;

import com.raisetimeline.like.dto.AddLikeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * いいねの追加・削除に関する HTTP リクエストを受け付けるコントローラー。 レスポンスは 204 No Content を返す。いいね数・liked
 * フラグはタイムライン取得時に一緒に返るため、 いいねエンドポイント自体はボディなしで成功を通知するだけにする。
 */
@Tag(name = "Like", description = "投稿へのいいね追加・削除")
@RestController
@RequestMapping("/api/likes")
public class LikeController {

    private final LikeService likeService;

    /** コンストラクタ。Spring が LikeService を自動的に注入する。 */
    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    /**
     * いいね追加エンドポイント。 POST /api/likes 既にいいね済みの場合は 409 Conflict を返す。
     *
     * @param request いいね対象の投稿 ID を含むリクエスト DTO
     * @return 204 No Content
     */
    @Operation(summary = "いいね追加", description = "指定投稿にいいねを付ける。既にいいね済みの場合は 409 を返す。")
    @ApiResponse(responseCode = "204", description = "追加成功")
    @ApiResponse(responseCode = "404", description = "投稿が存在しない")
    @ApiResponse(responseCode = "409", description = "既にいいね済み")
    @PostMapping
    public ResponseEntity<Void> addLike(@Valid @RequestBody AddLikeRequest request) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        likeService.addLike(request.postId(), email);
        return ResponseEntity.noContent().build();
    }

    /**
     * いいね削除エンドポイント。 DELETE /api/likes/{postId} いいねが存在しない場合は 404 Not Found を返す。
     *
     * @param postId パスパラメータ（いいねを取り消す投稿の ID）
     * @return 204 No Content
     */
    @Operation(summary = "いいね削除", description = "指定投稿のいいねを取り消す。いいねが存在しない場合は 404 を返す。")
    @ApiResponse(responseCode = "204", description = "削除成功")
    @ApiResponse(responseCode = "404", description = "いいねが存在しない")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> removeLike(@PathVariable Long postId) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        likeService.removeLike(postId, email);
        return ResponseEntity.noContent().build();
    }
}
