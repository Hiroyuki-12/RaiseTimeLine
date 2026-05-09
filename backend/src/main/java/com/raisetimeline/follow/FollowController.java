package com.raisetimeline.follow;

import com.raisetimeline.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * フォロー/アンフォローに関する HTTP リクエストを受け付けるコントローラー。 SecurityConfig の anyRequest().authenticated() により、
 * すべてのエンドポイントで JWT 認証が必須になる。
 */
@Tag(name = "Follow", description = "ユーザーのフォロー・アンフォローとフォロー/フォロワー一覧")
@RestController
@RequestMapping("/api/users")
public class FollowController {

    private final FollowService followService;

    /** コンストラクタ。Spring が FollowService を自動的に注入する。 */
    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /**
     * フォローエンドポイント。 POST /api/users/{userId}/follow 指定ユーザーをフォローする。自分自身・重複フォローは 400/409 を返す。
     *
     * @param userId フォローするユーザーの ID
     * @return 204 No Content
     */
    @Operation(summary = "フォロー", description = "指定ユーザーをフォローする。自分自身は 400、重複フォローは 409 を返す。")
    @ApiResponse(responseCode = "204", description = "フォロー成功")
    @ApiResponse(responseCode = "400", description = "自分自身をフォローしようとした")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない")
    @ApiResponse(responseCode = "409", description = "既にフォロー済み")
    @PostMapping("/{userId}/follow")
    public ResponseEntity<Void> follow(@PathVariable Long userId) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        followService.follow(userId, email);
        return ResponseEntity.noContent().build();
    }

    /**
     * アンフォローエンドポイント。 DELETE /api/users/{userId}/follow 指定ユーザーのフォローを解除する。
     *
     * @param userId アンフォローするユーザーの ID
     * @return 204 No Content
     */
    @Operation(summary = "アンフォロー", description = "指定ユーザーのフォローを解除する。フォローしていない場合でも冪等に 204 を返す。")
    @ApiResponse(responseCode = "204", description = "アンフォロー成功")
    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<Void> unfollow(@PathVariable Long userId) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        followService.unfollow(userId, email);
        return ResponseEntity.noContent().build();
    }

    /**
     * フォロー中一覧取得エンドポイント。 GET /api/users/{username}/following 指定ユーザーがフォローしているユーザー一覧を返す。
     *
     * @param username パスパラメータ（@handle 形式のユーザー名）
     * @return 200 OK + UserSummary のリスト
     */
    @Operation(
            summary = "フォロー中一覧取得",
            description = "指定ユーザーがフォローしているユーザー一覧。各要素には自分視点の isFollowing が付く。")
    @ApiResponse(responseCode = "200", description = "取得成功")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない")
    @GetMapping("/{username}/following")
    public ResponseEntity<List<UserSummary>> getFollowing(@PathVariable String username) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(followService.getFollowing(username, email));
    }

    /**
     * フォロワー一覧取得エンドポイント。 GET /api/users/{username}/followers 指定ユーザーをフォローしているユーザー一覧を返す。
     *
     * @param username パスパラメータ（@handle 形式のユーザー名）
     * @return 200 OK + UserSummary のリスト
     */
    @Operation(
            summary = "フォロワー一覧取得",
            description = "指定ユーザーをフォローしているユーザー一覧。各要素には自分視点の isFollowing が付く。")
    @ApiResponse(responseCode = "200", description = "取得成功")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない")
    @GetMapping("/{username}/followers")
    public ResponseEntity<List<UserSummary>> getFollowers(@PathVariable String username) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(followService.getFollowers(username, email));
    }
}
