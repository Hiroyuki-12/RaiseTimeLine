package com.raisetimeline.auth;

import com.raisetimeline.auth.dto.AuthResponse;
import com.raisetimeline.auth.dto.LoginRequest;
import com.raisetimeline.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証に関する HTTP リクエストを受け付けるコントローラー。
 *
 * <p>SecurityConfig で /api/auth/** は認証不要（permitAll）に設定しているため、 未ログインのユーザーもこれらのエンドポイントにアクセスできる。
 *
 * <p>リフレッシュトークンのセキュリティ設計: - HttpOnly Cookie に保存 → JavaScript からアクセス不可（XSS 対策） - SameSite=Lax →
 * 他サイトからの POST リクエストでは Cookie が送られない（CSRF 対策） - Path=/api/auth → リフレッシュ・ログアウト以外のリクエストでは Cookie
 * が送られない（スコープを最小化）
 */
@Tag(name = "Auth", description = "ユーザー登録・ログイン・トークン再発行・ログアウト")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /** リフレッシュトークンの有効期限（秒単位に変換して Cookie の Max-Age に使う） */
    private final long refreshExpirationSeconds;

    /** コンストラクタ。Spring が AuthService を自動的に注入する。 */
    public AuthController(
            AuthService authService,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.authService = authService;
        // ミリ秒 → 秒に変換（Cookie の Max-Age は秒単位）
        this.refreshExpirationSeconds = refreshExpirationMs / 1000;
    }

    /**
     * ユーザー登録エンドポイント。 POST /api/auth/register
     *
     * @param request フロントエンドから送られてくる登録情報（JSON が自動でオブジェクトに変換される）
     * @return 201 Created + AuthResponse（アクセストークン・ユーザーID・ユーザー名） リフレッシュトークンは Set-Cookie ヘッダーで
     *     HttpOnly Cookie として送る
     */
    @Operation(
            summary = "ユーザー登録",
            description =
                    "メール・ユーザー名・パスワードを受け取って新規ユーザーを作成し、"
                            + "アクセストークン (JSON) とリフレッシュトークン (HttpOnly Cookie) を返す。"
                            + "認証不要エンドポイント。")
    @ApiResponse(responseCode = "201", description = "登録成功")
    @ApiResponse(responseCode = "400", description = "バリデーションエラー / メール or ユーザー名が既に使われている")
    @SecurityRequirements({})
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.AuthResult result = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(
                        HttpHeaders.SET_COOKIE,
                        buildRefreshTokenCookie(result.refreshToken()).toString())
                .body(result.response());
    }

    /**
     * ログインエンドポイント。 POST /api/auth/login
     *
     * @param request フロントエンドから送られてくるログイン情報（JSON が自動でオブジェクトに変換される）
     * @return 200 OK + AuthResponse（アクセストークン・ユーザーID・ユーザー名） リフレッシュトークンは Set-Cookie ヘッダーで HttpOnly
     *     Cookie として送る
     */
    @Operation(
            summary = "ログイン",
            description = "メールアドレス + パスワードで認証し、アクセストークンとリフレッシュトークン Cookie を返す。" + "認証不要エンドポイント。")
    @ApiResponse(responseCode = "200", description = "ログイン成功")
    @ApiResponse(responseCode = "401", description = "メールまたはパスワードが正しくない")
    @SecurityRequirements({})
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        buildRefreshTokenCookie(result.refreshToken()).toString())
                .body(result.response());
    }

    /**
     * アクセストークン再発行エンドポイント。 POST /api/auth/refresh
     *
     * <p>フロントエンドのアクセストークン（15分）が期限切れになったとき、このエンドポイントを呼ぶ。 Cookie に含まれるリフレッシュトークンを DB
     * で検証し、有効なら新しいアクセストークンを返す。 リフレッシュトークンもローテーション（更新）される。
     *
     * @param refreshToken Cookie "refresh_token" の値（@CookieValue で自動取得）
     * @return 200 OK + 新しい AuthResponse。Cookie も新しいリフレッシュトークンに更新される
     */
    @Operation(
            summary = "アクセストークン再発行",
            description =
                    "Cookie の refresh_token を検証し、新しいアクセストークンとローテーション後のリフレッシュトークンを返す。"
                            + "アクセストークンは 15 分で失効するため、フロントは 401 を受け取ったらこのエンドポイントを呼ぶ。")
    @ApiResponse(responseCode = "200", description = "再発行成功")
    @ApiResponse(responseCode = "401", description = "リフレッシュトークンが無効・期限切れ・存在しない")
    @SecurityRequirements({})
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthService.AuthResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        buildRefreshTokenCookie(result.refreshToken()).toString())
                .body(result.response());
    }

    /**
     * ログアウトエンドポイント。 POST /api/auth/logout
     *
     * <p>DB からリフレッシュトークンを削除し、Cookie を無効化（Max-Age=0）する。 アクセストークンはクライアント側のメモリから削除する（サーバーでは管理しない）。
     *
     * @param refreshToken Cookie "refresh_token" の値（@CookieValue で自動取得）
     * @return 204 No Content
     */
    @Operation(
            summary = "ログアウト",
            description =
                    "DB から refresh_token を削除し、ブラウザの Cookie も Max-Age=0 で無効化する。"
                            + "アクセストークンはステートレスなのでサーバー側からは失効させられない（クライアント側で破棄する）。")
    @ApiResponse(responseCode = "204", description = "ログアウト成功")
    @SecurityRequirements({})
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        authService.logout(refreshToken);
        // Max-Age=0 にすることでブラウザが Cookie を即座に削除する
        ResponseCookie clearCookie =
                ResponseCookie.from("refresh_token", "")
                        .httpOnly(true)
                        .path("/api/auth")
                        .maxAge(0)
                        .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    /**
     * リフレッシュトークン用の HttpOnly Cookie を生成する内部メソッド。 HttpOnly: JavaScript からアクセス不可（XSS 対策） SameSite=Lax:
     * 別サイトの POST リクエストでは送られない（CSRF 対策） Path=/api/auth: このパス配下のリクエストにのみ Cookie が付く（スコープ最小化）
     *
     * @param token Cookie にセットするリフレッシュトークン文字列
     * @return 設定済みの ResponseCookie
     */
    private ResponseCookie buildRefreshTokenCookie(String token) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(refreshExpirationSeconds)
                .build();
    }
}
