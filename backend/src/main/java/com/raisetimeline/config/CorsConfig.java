package com.raisetimeline.config;

import java.util.List;
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
     * CORS の許可ルールを定義した CorsConfigurationSource を Bean として登録する。 SecurityConfig でこの Bean を参照して Spring
     * Security の CORS 設定に組み込む。
     *
     * @return CORS 設定ソース
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 許可するオリジン（フロントエンドの URL）
        // ポートルール: フロントエンドは必ず 5173 を使用する
        config.setAllowedOrigins(List.of("http://localhost:5173"));

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
