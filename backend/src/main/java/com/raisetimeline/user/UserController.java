package com.raisetimeline.user;

import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.user.dto.UpdateProfileRequest;
import com.raisetimeline.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ユーザープロフィールに関する HTTP リクエストを受け付けるコントローラー。 SecurityConfig の anyRequest().authenticated() により、
 * すべてのエンドポイントで JWT 認証が必須になる。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    /** コンストラクタ。Spring が UserService を自動的に注入する。 */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * プロフィール取得エンドポイント。 GET /api/users/{username} フォロー数・フォロワー数・isFollowing・isOwnProfile を含む。
     *
     * @param username パスパラメータ（@handle 形式のユーザー名）
     * @return 200 OK + UserProfileResponse
     */
    @GetMapping("/{username}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable String username) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.getProfile(username, email));
    }

    /**
     * 自分のプロフィール更新エンドポイント。 PUT /api/users/me 自分のユーザー名・表示名・自己紹介を更新する。
     *
     * @param request 更新内容（@Valid でバリデーション実行）
     * @return 200 OK + 更新後の UserProfileResponse
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.updateProfile(request, email));
    }

    /**
     * ユーザーの投稿一覧取得エンドポイント。 GET /api/users/{username}/posts プロフィールページに表示する投稿を新しい順で返す。
     *
     * @param username パスパラメータ（@handle 形式のユーザー名）
     * @return 200 OK + PostResponse のリスト
     */
    @GetMapping("/{username}/posts")
    public ResponseEntity<List<PostResponse>> getUserPosts(@PathVariable String username) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.getUserPosts(username, email));
    }
}
