package com.raisetimeline.auth;

import com.raisetimeline.auth.dto.AuthResponse;
import com.raisetimeline.auth.dto.LoginRequest;
import com.raisetimeline.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証に関する HTTP リクエストを受け付けるコントローラー。 @RestController: このクラスが REST API のコントローラーであることを示す。 メソッドの戻り値が自動的に
 * JSON に変換されてレスポンスボディに書き込まれる。 @RequestMapping("/api/auth"): このコントローラーのすべてのエンドポイントは /api/auth
 * で始まるパスを持つ。
 *
 * <p>SecurityConfig で /api/auth/** は認証不要（permitAll）に設定している。 そのため、未ログインのユーザーもこれらのエンドポイントにアクセスできる。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /** コンストラクタ。Spring が AuthService を自動的に注入する。 */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * ユーザー登録エンドポイント。 POST /api/auth/register @Valid: リクエストボディを RegisterRequest
     * のバリデーションアノテーションで検証する。 検証に失敗した場合は GlobalExceptionHandler が 400 Bad Request を返す。
     *
     * @param request フロントエンドから送られてくる登録情報（JSON が自動でオブジェクトに変換される）
     * @return 201 Created + AuthResponse（JWT トークン・ユーザーID・ユーザー名） 新規リソース作成なので 200 OK ではなく 201 Created
     *     を返す（REST の慣例）
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * ログインエンドポイント。 POST /api/auth/login
     *
     * @param request フロントエンドから送られてくるログイン情報（JSON が自動でオブジェクトに変換される）
     * @return 200 OK + AuthResponse（JWT トークン・ユーザーID・ユーザー名） 認証失敗時は AuthService が 401 Unauthorized
     *     を投げる
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
