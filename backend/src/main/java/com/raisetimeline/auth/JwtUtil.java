package com.raisetimeline.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT（JSON Web Token）の生成・検証を担当するユーティリティクラス。
 *
 * <p>JWT とは「ユーザーが誰であるか」を証明するトークン。 サーバーが秘密鍵で署名して発行し、クライアントはリクエストのたびにこのトークンを送る。
 * サーバーはトークンの署名を検証することで、改ざんされていないか確認できる。 データベースを参照しなくてもユーザーを識別できるため、スケーラブルな認証に向いている（ステートレス認証）。
 */
@Component
public class JwtUtil {

    /**
     * JWT の署名に使う秘密鍵。 HS256 アルゴリズムでは 256bit（32バイト）以上のキーが必須。 application.properties の app.jwt.secret
     * から注入される。
     */
    private final SecretKey secretKey;

    /** JWT の有効期限（ミリ秒）。 application.properties の app.jwt.expiration-ms から注入される。 */
    private final long expirationMs;

    /**
     * コンストラクタ。Spring が起動時に application.properties の値を注入する。
     *
     * @param secret JWT 署名用シークレット文字列（32文字以上必須）
     * @param expirationMs JWT 有効期限（ミリ秒）
     */
    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // 文字列をバイト配列に変換して SecretKey オブジェクトを生成する
        // Keys.hmacShaKeyFor は 256bit 未満のキーだとエラーを投げる（セキュリティ保証）
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * JWT トークンを生成する。 ログイン・ユーザー登録が成功したときにこのメソッドを呼び、クライアントに返す。
     *
     * @param email JWT に埋め込むメールアドレス（ユーザーを識別する情報）
     * @return 署名済み JWT 文字列（例: "eyJhbGciOiJIUzI1NiJ9...."）
     */
    public String generateToken(String email) {
        return Jwts.builder()
                // subject: トークンが「誰のものか」を示すフィールド。メールアドレスをセット
                .subject(email)
                // issuedAt: トークンの発行日時
                .issuedAt(new Date())
                // expiration: トークンの有効期限。現在時刻 + expirationMs（デフォルト24時間後）
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                // signWith: 秘密鍵で署名する。署名がないと改ざんされても検知できない
                .signWith(secretKey)
                .compact();
    }

    /**
     * JWT トークンからメールアドレスを取り出す。 JwtAuthFilter でトークンを検証した後、どのユーザーか特定するために使う。
     *
     * @param token 検証済みの JWT 文字列
     * @return トークンに埋め込まれたメールアドレス
     */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * リフレッシュトークンを生成する。
     * JWT ではなく UUID v4 の不透明トークンを使う。
     * リフレッシュトークンは DB に保存して照合するため、JWT のように自己完結している必要がない。
     * UUID にすることで実装がシンプルになり、DB 削除だけで即時無効化できる。
     *
     * @return UUID v4 の文字列（例: "550e8400-e29b-41d4-a716-446655440000"）
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * JWT トークンが有効かどうかを確認する。 「有効」とは: 署名が正しい かつ 有効期限が切れていない、の両方を満たす状態。
     *
     * @param token 検証する JWT 文字列
     * @return 有効なら true、無効（署名不正・期限切れなど）なら false
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            // 署名不正・期限切れ・形式不正などの場合は例外が発生するため false を返す
            return false;
        }
    }

    /**
     * JWT トークンを解析して Claims（ペイロード）を取り出す内部メソッド。 トークンが不正な場合は JwtException のサブクラスの例外が投げられる。
     *
     * <p>JJWT 0.12.x の新しい API を使用している。 旧 API（parseClaimsJws など）は 0.12.x で廃止されているため注意。
     *
     * @param token 解析する JWT 文字列
     * @return JWT のペイロード（Claims オブジェクト）
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                // verifyWith: 署名検証に使う秘密鍵を指定する
                .verifyWith(secretKey)
                .build()
                // parseSignedClaims: トークンを解析して署名検証 + 期限チェックを実行する
                .parseSignedClaims(token)
                .getPayload();
    }
}
