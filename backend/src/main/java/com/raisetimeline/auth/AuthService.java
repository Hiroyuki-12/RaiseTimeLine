package com.raisetimeline.auth;

import com.raisetimeline.auth.dto.AuthResponse;
import com.raisetimeline.auth.dto.LoginRequest;
import com.raisetimeline.auth.dto.RegisterRequest;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * ユーザー登録・ログインのビジネスロジックを担当するサービスクラス。
 *
 * @Service アノテーションにより Spring が管理する Bean として登録される。
 * コントローラーからこのクラスを呼び出すことで、
 * HTTP の詳細（リクエスト・レスポンス）とビジネスロジックを分離できる。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * コンストラクタ。Spring が依存オブジェクトを自動的に注入する（依存性注入）。
     * @Autowired なしでもコンストラクタが1つの場合は自動でDIが機能する。
     */
    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * ユーザー登録を行い、JWT トークンを返す。
     *
     * 処理の流れ:
     *   1. パスワードと確認用パスワードが一致するか確認
     *   2. メールアドレス・ユーザー名の重複チェック
     *   3. パスワードを BCrypt でハッシュ化して DB に保存
     *   4. JWT を生成してレスポンスに含める
     *
     * @param req フロントエンドから送られてきた登録情報
     * @return 認証レスポンス（JWT トークン・ユーザーID・ユーザー名）
     */
    public AuthResponse register(RegisterRequest req) {
        // パスワードと確認用が一致しているかチェック
        // バリデーションアノテーションでは2フィールドの比較ができないため、ここで確認する
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
        /*
         * BCrypt でパスワードをハッシュ化して保存する。
         * BCrypt は一方向ハッシュ（元のパスワードに戻せない）なので、
         * DB が漏洩しても平文パスワードは分からない。
         * ソルト（ランダムな追加データ）が自動で付与されるため、
         * 同じパスワードでもハッシュ値が毎回異なる。
         */
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userMapper.insert(user);
        // insert 後、user.getId() に DB が発行した id がセットされている

        // JWT を生成してクライアントに返す
        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getId(), user.getUsername());
    }

    /**
     * ログインを行い、JWT トークンを返す。
     *
     * 処理の流れ:
     *   1. メールアドレスでユーザーを検索（存在しなければ 401）
     *   2. 入力パスワードと DB のハッシュを BCrypt で照合（不一致なら 401）
     *   3. JWT を生成してレスポンスに含める
     *
     * セキュリティ上の注意:
     *   「メールアドレスが存在しない」と「パスワードが違う」でエラーメッセージを分けると、
     *   攻撃者にメールアドレスの存在有無を知らせることになる（列挙攻撃）。
     *   そのため、どちらの場合も同じエラーメッセージを返す。
     *
     * @param req ログイン情報（メールアドレス・パスワード）
     * @return 認証レスポンス（JWT トークン・ユーザーID・ユーザー名）
     */
    public AuthResponse login(LoginRequest req) {
        // メールアドレスでユーザーを検索する
        User user = userMapper.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません"));

        /*
         * passwordEncoder.matches(rawPassword, encodedPassword) で照合する。
         * rawPassword: クライアントから送られてきた平文パスワード
         * encodedPassword: DB に保存されている BCrypt ハッシュ
         * BCrypt は内部でソルトを使うため、毎回同じ計算結果にはならないが、
         * matches メソッドが正確に照合してくれる。
         */
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません");
        }

        // 認証成功: JWT を生成してクライアントに返す
        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getId(), user.getUsername());
    }
}
