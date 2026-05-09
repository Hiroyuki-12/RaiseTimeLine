package com.raisetimeline.user;

import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.user.dto.UpdateProfileRequest;
import com.raisetimeline.user.dto.UserProfileResponse;
import com.raisetimeline.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "User", description = "ユーザープロフィール・検索・アバター画像")
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
    @Operation(summary = "ユーザー検索", description = "ユーザー名の部分一致で検索する。最大 20 件、isFollowing フラグ付き。")
    @ApiResponse(responseCode = "200", description = "検索成功")
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
    @Operation(
            summary = "アバター画像アップロード",
            description = "JPEG/PNG・2MB 以下の画像を S3 に保存し、avatarUrl を更新したプロフィールを返す。")
    @ApiResponse(responseCode = "200", description = "更新成功")
    @ApiResponse(responseCode = "400", description = "ファイル形式・サイズが不正")
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
    @Operation(
            summary = "プロフィール取得",
            description = "指定ユーザー名 (@handle) のプロフィールを返す。フォロー数・フォロワー数・isFollowing・isOwnProfile を含む。")
    @ApiResponse(responseCode = "200", description = "取得成功")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない")
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
    @Operation(summary = "自分のプロフィール更新", description = "ユーザー名・表示名・自己紹介を更新する。ユーザー名重複時は 409 を返す。")
    @ApiResponse(responseCode = "200", description = "更新成功")
    @ApiResponse(responseCode = "400", description = "バリデーションエラー")
    @ApiResponse(responseCode = "409", description = "ユーザー名が既に使われている")
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
    @Operation(summary = "ユーザーの投稿一覧取得", description = "プロフィールページに表示する投稿を新しい順で返す。")
    @ApiResponse(responseCode = "200", description = "取得成功")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない")
    @GetMapping("/{username}/posts")
    public ResponseEntity<List<PostResponse>> getUserPosts(@PathVariable String username) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.getUserPosts(username, email));
    }
}
