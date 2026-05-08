package com.raisetimeline.user;

import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.user.dto.UpdateProfileRequest;
import com.raisetimeline.user.dto.UserProfileResponse;
import com.raisetimeline.user.dto.UserSummary;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
     * ユーザー検索エンドポイント。 GET /api/users/search?q=キーワード ユーザー名の部分一致で検索し、isFollowing フラグ付きで返す。
     *
     * <p>注意: このメソッドは /{username} より先に宣言することで Spring MVC がリテラルパスを優先してマッチさせる。 /api/users/search を
     * {username} として解釈してしまうパス競合を防ぐ。
     *
     * @param q 検索キーワード（1文字以上）
     * @return 200 OK + UserSummary のリスト（最大 20 件）
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserSummary>> searchUsers(@RequestParam String q) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.searchUsers(q, email));
    }

    /**
     * アバター画像アップロードエンドポイント。 POST /api/users/me/avatar multipart/form-data でファイルを受け取り、S3
     * に保存してプロフィールを返す。
     *
     * @param file リクエストの "file" フィールドに添付された画像ファイル（JPEG または PNG、2MB 以下）
     * @return 200 OK + 更新後の UserProfileResponse
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileResponse> uploadAvatar(
            @RequestParam("file") MultipartFile file) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.uploadAvatar(file, email));
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
