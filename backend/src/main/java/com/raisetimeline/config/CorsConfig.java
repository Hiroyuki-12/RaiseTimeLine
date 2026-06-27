package com.raisetimeline.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS（Cross-Origin Resource Sharing）設定クラス。
 *
 * <p>CORS とは、ブラウザが「異なるオリジン（ドメイン・ポート）へのリクエスト」を制限するセキュリティ機能。
 * フロントエンド（localhost:5173）からバックエンド（localhost:8080）へのリクエストは ポートが異なるため、デフォルトではブラウザにブロックされる。
 * この設定でフロントエンドのオリジンを明示的に許可することで、リクエストが通るようになる。 @Configuration アノテーションにより、Spring がアプリ起動時にこのクラスを読み込んで
 * 設定を適用する。
 */
@Configuration
public class CorsConfig {

    /**
     * 許可するオリジン（カンマ区切りで複数指定可）。
     *
     * <p>ローカル開発はフロントの http://localhost:5173 が既定。本番では CloudFront のドメイン （例:
     * https://xxxx.cloudfront.net）を環境変数 APP_CORS_ALLOWED_ORIGINS で注入する。
     *
     * <p>なぜ必要か: CloudFront 配下では「同一オリジン」でもブラウザは POST 等に Origin ヘッダを付ける。 Spring の CORS
     * フィルタは許可リストに無いオリジンを 403 で弾くため、本番ドメインを許可しないと ログイン等の API がすべて 403 になる（さらに CloudFront の SPA
     * フォールバックで 200/HTML に化け、 フロントが JSON を期待して壊れる）。
     */
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    /**
     * CORS の許可ルールを定義した CorsConfigurationSource を Bean として登録する。 SecurityConfig でこの Bean を参照して Spring
     * Security の CORS 設定に組み込む。
     *
     * @return CORS 設定ソース
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 許可するオリジン（環境ごとに app.cors.allowed-origins で設定）
        // ローカル: http://localhost:5173 / 本番: CloudFront ドメイン
        config.setAllowedOrigins(allowedOrigins);

        // 許可する HTTP メソッド
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 許可するリクエストヘッダー（* = すべて許可）
        // Authorization ヘッダー（JWT 送信用）も含まれる
        config.setAllowedHeaders(List.of("*"));

        // 認証情報（Cookie など）を含むリクエストを許可する
        // JWT を Authorization ヘッダーで送る場合は厳密には不要だが、将来の拡張を考慮して true にしておく
        config.setAllowCredentials(true);

        // すべてのパス（/**）に上記の CORS ルールを適用する
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
