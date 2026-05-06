package com.raisetimeline.config;

import com.raisetimeline.auth.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security の設定クラス。
 *
 * <p>Spring Security は Web アプリのセキュリティ機能（認証・認可・CORS・CSRF 等）を提供するフレームワーク。
 * ここで「どのエンドポイントに認証が必要か」「どの認証方式を使うか」などを設定する。
 *
 * <p>注意: Spring Boot 4.x（Spring Security 7.x）では WebSecurityConfigurerAdapter クラスが削除された。 代わりに
 * SecurityFilterChain を @Bean として定義する方式が標準になっている。
 */
@Configuration
@EnableWebSecurity // Spring Security の Web セキュリティ機能を有効にする
public class SecurityConfig {

    /** JWT 認証フィルター（各リクエストの Authorization ヘッダーを検証するフィルター） */
    private final JwtAuthFilter jwtAuthFilter;

    /** CORS 設定（フロントエンドからのリクエストを許可するルール） */
    private final CorsConfigurationSource corsConfigurationSource;

    /** コンストラクタ。Spring が JwtAuthFilter と CorsConfigurationSource を自動的に注入する。 */
    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter, CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /**
     * セキュリティのルールを定義するメインメソッド。 HTTP リクエストに対するセキュリティポリシーを設定する。
     *
     * @param http Spring Security が提供するセキュリティ設定ビルダー
     * @return 設定済みの SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                /*
                 * CORS 設定を適用する。
                 * CorsConfig で定義した corsConfigurationSource（localhost:5173 からのリクエストを許可）を使う。
                 * これがないとフロントエンドからのリクエストがブラウザにブロックされる。
                 */
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                /*
                 * CSRF（Cross-Site Request Forgery）保護を無効にする。
                 *
                 * CSRF 攻撃は「別のサイトからブラウザの Cookie を使って不正リクエストを送る」攻撃。
                 * セッション Cookie を使う場合は対策が必要だが、今回は JWT を Authorization ヘッダーで送る。
                 * Authorization ヘッダーはブラウザが自動送信しないため、CSRF の脅威が存在しない。
                 * → CSRF 保護を無効にしても安全。有効にすると REST クライアントでのテストが不便になる。
                 */
                .csrf(csrf -> csrf.disable())

                /*
                 * セッション管理をステートレスに設定する。
                 * STATELESS: サーバー側にセッション情報を保持しない。
                 * JWT 認証はトークン自体に認証情報が含まれるため、サーバーにセッションは不要。
                 * スケールアウト（サーバー台数を増やすこと）しやすくなる利点もある。
                 */
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                /*
                 * エンドポイントごとのアクセス制御を設定する。
                 */
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        /*
                                         * 認証不要エンドポイントを個別に列挙する。
                                         * Spring Security 7.x では requestMatchers(String) はデフォルトで
                                         * PathPatternRequestMatcher を使う。ワイルドカード(**) の動作が
                                         * Spring MVC の有無によって変わる場合があるため、
                                         * 認証不要パスは明示的に列挙して確実に permitAll を適用する。
                                         */
                                        // 登録・ログイン・リフレッシュ・ログアウトは認証不要
                                        .requestMatchers(
                                                "/api/auth/register",
                                                "/api/auth/login",
                                                "/api/auth/refresh",
                                                "/api/auth/logout")
                                        .permitAll()
                                        /*
                                         * /error も認証不要にする。
                                         * Spring Boot はリクエストが 404 や 500 等でエラーになると、
                                         * 内部で /error に転送する（サーブレットの ERROR ディスパッチ）。
                                         * この転送は元のリクエストの JWT を引き継がないため、
                                         * /error が認証必須だと 403 になってしまう。
                                         * /error 自体はエラー情報を返すだけで機密データは含まないため permitAll が適切。
                                         */
                                        .requestMatchers("/error")
                                        .permitAll()
                                        // 上記以外のすべてのエンドポイントは JWT 認証が必要
                                        .anyRequest()
                                        .authenticated())

                /*
                 * JWT 認証フィルターをフィルターチェーンに追加する。
                 * UsernamePasswordAuthenticationFilter の前に実行されるように配置する。
                 * これにより各リクエストで JWT が検証され、有効なら認証済み状態になる。
                 */
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * パスワードエンコーダーを Bean として登録する。
     *
     * <p>BCryptPasswordEncoder を使う。BCrypt の特徴: - 一方向ハッシュ（元のパスワードに戻せない） -
     * ソルト（ランダムデータ）を自動生成してハッシュに組み込む → 同じパスワードでもハッシュ値が毎回異なる（レインボーテーブル攻撃を防ぐ） - コスト係数（デフォルト:
     * 10）でハッシュ計算の遅さを調整できる → 遅いほどブルートフォース攻撃に強い @Bean として登録することで AuthService に DI（依存性注入）できる。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
