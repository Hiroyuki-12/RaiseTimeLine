package com.raisetimeline.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * CorsConfig の単体テスト。
 *
 * <pre>
 * 対象: CorsConfig#corsConfigurationSource（許可オリジンの反映）
 * 技法: 同値分割（設定したオリジンが許可される / 設定外は含まれない）
 *
 * [EP-1] app.cors.allowed-origins に設定した本番ドメインが許可オリジンに反映される
 * [EP-2] 複数オリジン（ローカル + 本番）を設定でき、両方反映される
 * </pre>
 */
@DisplayName("CorsConfig の単体テスト")
class CorsConfigTest {

    /**
     * @Value はテストでは注入されないため allowedOrigins を直接埋めて検証する。
     */
    private CorsConfigurationSource buildSourceWith(List<String> origins) {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        return config.corsConfigurationSource();
    }

    private CorsConfiguration resolve(CorsConfigurationSource source) {
        HttpServletRequest req = new MockHttpServletRequest("GET", "/api/posts");
        return source.getCorsConfiguration(req);
    }

    @Test
    @DisplayName("[EP-1] 設定した本番 CloudFront ドメインが許可オリジンに反映される")
    void allowsConfiguredProdOrigin() {
        CorsConfiguration cfg = resolve(buildSourceWith(List.of("https://example.cloudfront.net")));

        assertThat(cfg).isNotNull();
        assertThat(cfg.getAllowedOrigins()).containsExactly("https://example.cloudfront.net");
        // 認証情報（Cookie）を伴うリクエストを許可している
        assertThat(cfg.getAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("[EP-2] ローカルと本番の複数オリジンを許可できる")
    void allowsMultipleOrigins() {
        CorsConfiguration cfg =
                resolve(
                        buildSourceWith(
                                List.of(
                                        "http://localhost:5173",
                                        "https://example.cloudfront.net")));

        assertThat(cfg.getAllowedOrigins())
                .containsExactlyInAnyOrder(
                        "http://localhost:5173", "https://example.cloudfront.net");
    }
}
