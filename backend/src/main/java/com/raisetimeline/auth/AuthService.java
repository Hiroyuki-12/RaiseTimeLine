package com.raisetimeline.auth;

import com.raisetimeline.auth.dto.AuthResponse;
import com.raisetimeline.auth.dto.LoginRequest;
import com.raisetimeline.auth.dto.RegisterRequest;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * ユーザー登録・ログイン・リフレッシュ・ログアウトのビジネスロジックを担当するサービスクラス。 @Service アノテーションにより Spring が管理する Bean として登録される。
 * コントローラーからこのクラスを呼び出すことで、HTTP の詳細とビジネスロジックを分離できる。
 */
@Service
public class AuthService {

    // SLF4J ロガー。クラス名をカテゴリとして使用することでログの出所が分かりやすくなる
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /** リフレッシュトークンの有効期限（ミリ秒）。application.properties から注入される */
    private final long refreshExpirationMs;

    /** コンストラクタ。Spring が依存オブジェクトを自動的に注入する（依存性注入）。 */
    public AuthService(
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * ユーザー登録を行い、アクセストークンを返す。 リフレッシュトークンは呼び出し元（AuthController）が Cookie にセットする。
     *
     * <p>処理の流れ: 1. パスワードと確認用パスワードが一致するか確認 2. メールアドレス・ユーザー名の重複チェック 3. パスワードを BCrypt でハッシュ化して DB に保存
     * 4. アクセストークンと リフレッシュトークンを生成 5. リフレッシュトークンを DB に保存
     *
     * @param req フロントエンドから送られてきた登録情報
     * @return 認証レスポンス（アクセストークン・ユーザーID・ユーザー名）
     */
    @Transactional
    public AuthResult register(RegisterRequest req) {
        // パスワードと確認用が一致しているかチェック
        if (!req.password().equals(req.passwordConfirm())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードが一致しません");
        }

        // 同じメールアドレスで複数アカウントを作れないようにチェック
        if (userMapper.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "このメールアドレスは既に使用されています");
        }

        // 同じユーザー名で複数アカウントを作れないようにチェック
        if (userMapper.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "このユーザー名は既に使用されています");
        }

        // User オブジェクトを作成し DB に保存する
        User user = new User();
        user.setEmail(req.email());
        user.setUsername(req.username());
        // 表示名（日本語対応）を設定する
        user.setDisplayName(req.displayName());
        /*
         * BCrypt でパスワードをハッシュ化して保存する。
         * BCrypt は一方向ハッシュ（元のパスワードに戻せない）なので、
         * DB が漏洩しても平文パスワードは分からない。
         */
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userMapper.insert(user);

        // アクセストークン（15分）とリフレッシュトークン（7日間）を生成してDBに保存
        String accessToken = jwtUtil.generateToken(user.getEmail());
        String refreshToken = createAndSaveRefreshToken(user.getId());

        log.info("ユーザー登録完了: userId={}, username={}", user.getId(), user.getUsername());
        return new AuthResult(
                new AuthResponse(
                        accessToken, user.getId(), user.getUsername(), user.getDisplayName()),
                refreshToken);
    }

    /**
     * ログインを行い、アクセストークンとリフレッシュトークンを返す。
     *
     * <p>セキュリティ上の注意: 「メールアドレスが存在しない」と「パスワードが違う」でエラーメッセージを分けると、 攻撃者にメールアドレスの存在有無を知らせることになる（列挙攻撃）。
     * そのため、どちらの場合も同じエラーメッセージを返す。
     *
     * @param req ログイン情報（メールアドレス・パスワード）
     * @return 認証レスポンス（アクセストークン・ユーザーID・ユーザー名）とリフレッシュトークン
     */
    @Transactional
    public AuthResult login(LoginRequest req) {
        // メールアドレスでユーザーを検索する
        User user =
                userMapper
                        .findByEmail(req.email())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED,
                                                "メールアドレスまたはパスワードが正しくありません"));

        /*
         * passwordEncoder.matches(rawPassword, encodedPassword) で照合する。
         * BCrypt は内部でソルトを使うため毎回同じ計算結果にはならないが、
         * matches メソッドが正確に照合してくれる。
         */
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません");
        }

        // 認証成功: アクセストークンとリフレッシュトークンを生成
        String accessToken = jwtUtil.generateToken(user.getEmail());
        String refreshToken = createAndSaveRefreshToken(user.getId());

        log.info("ログイン成功: userId={}, username={}", user.getId(), user.getUsername());
        return new AuthResult(
                new AuthResponse(
                        accessToken, user.getId(), user.getUsername(), user.getDisplayName()),
                refreshToken);
    }

    /**
     * リフレッシュトークンを使ってアクセストークンを再発行する。 アクセストークン（15分）が期限切れになったとき、ページ遷移のたびにログインを求めないために使う。
     *
     * <p>処理の流れ: 1. Cookie に含まれるリフレッシュトークンを DB で検索 2. 存在しない or 期限切れなら 401 を返す 3.
     * 有効なら新しいアクセストークンを発行する 4. リフレッシュトークンをローテーション（古いトークンを削除して新しいものに差し替え）
     *
     * @param rawRefreshToken Cookie から取り出したリフレッシュトークン文字列
     * @return 新しいアクセストークンを含む AuthResult
     */
    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        // DB でリフレッシュトークンを検索する
        RefreshToken stored =
                refreshTokenMapper
                        .findByToken(rawRefreshToken)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "リフレッシュトークンが無効です"));

        // 有効期限が切れていないか確認する
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            // 期限切れのトークンは削除してから 401 を返す
            refreshTokenMapper.deleteByToken(rawRefreshToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "リフレッシュトークンの有効期限が切れています");
        }

        // リフレッシュトークンに紐づく userId でユーザーを取得して新しいアクセストークンを発行する
        User user =
                userMapper
                        .findById(stored.getUserId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        String accessToken = jwtUtil.generateToken(user.getEmail());

        // リフレッシュトークンをローテーション: 古いトークンを削除して新しいものを発行
        refreshTokenMapper.deleteByToken(rawRefreshToken);
        String newRefreshToken = createAndSaveRefreshToken(user.getId());

        return new AuthResult(
                new AuthResponse(
                        accessToken, user.getId(), user.getUsername(), user.getDisplayName()),
                newRefreshToken);
    }

    /**
     * ログアウト処理: リフレッシュトークンを DB から削除する。 Cookie のクリアはコントローラー（AuthController）で行う。
     *
     * @param rawRefreshToken Cookie から取り出したリフレッシュトークン文字列
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenMapper.deleteByToken(rawRefreshToken);
            log.info("ログアウト: リフレッシュトークンを削除しました");
        }
    }

    /**
     * リフレッシュトークンを生成して DB に保存する内部メソッド。 ログイン・登録・リフレッシュローテーション時に共通で使う。
     *
     * @param userId トークンを紐付けるユーザーの ID
     * @return 生成したリフレッシュトークン文字列
     */
    private String createAndSaveRefreshToken(Long userId) {
        String token = jwtUtil.generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setToken(token);
        // 現在時刻 + refreshExpirationMs ミリ秒 を有効期限にセットする
        refreshToken.setExpiresAt(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000L));
        refreshTokenMapper.insert(refreshToken);
        return token;
    }

    /**
     * register() と login() の戻り値をまとめる内部 record。 AuthResponse（レスポンスボディ用）とリフレッシュトークン（Cookie 用）の両方を持つ。
     */
    public record AuthResult(AuthResponse response, String refreshToken) {}
}
