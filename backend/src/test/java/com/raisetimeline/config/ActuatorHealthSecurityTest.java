package com.raisetimeline.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Actuator ヘルスチェックのセキュリティ挙動を検証する統合テスト。
 *
 * <pre>
 * 対象: SecurityConfig の permitAll 設定 + Spring Boot Actuator
 * 技法: デシジョンテーブル（認証有無 × エンドポイント種別）
 *
 *   [1] 未認証 GET /actuator/health        → 200（ALB/ECS がヘルスチェックできる）
 *   [2] 未認証 GET /api/posts(認証必須API) → 401（セキュリティが実際に効いている対比）
 *
 * 目的: AWS の ALB / ECS は JWT を持たずに /actuator/health を叩く。ここが認証必須だと
 *       常に 401 となり正常なタスクまで unhealthy 扱いになるため、permitAll であることを保証する。
 *
 * 注: Spring Boot 4.0 では @WebMvcTest / @AutoConfigureMockMvc が削除されたため、
 *     RANDOM_PORT で実サーバを起動し、JDK 標準の HttpClient で HTTP を投げて検証する。
 *     test プロファイル（H2 インメモリ DB）で起動するため本番 DB には触れない。
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Actuator ヘルスチェックのセキュリティ")
class ActuatorHealthSecurityTest {

    /** RANDOM_PORT で実際に割り当てられたポート番号が注入される。 */
    @LocalServerPort private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** 指定パスへ GET し、HTTP レスポンスを返す小さなヘルパー。 */
    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .GET()
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("未認証でも /actuator/health は 200 を返す（permitAll）")
    void ヘルスは未認証で200() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        // health のレスポンスボディには稼働状態 (UP) が含まれる
        assertThat(response.body()).contains("UP");
    }

    @Test
    @DisplayName("認証必須 API は未認証だと拒否される（401/403。セキュリティが有効である対比）")
    void 認証必須APIは未認証で拒否される() throws Exception {
        HttpResponse<String> response = get("/api/posts");

        // 未認証アクセスは Spring Security が拒否する。
        // 本アプリは AuthenticationEntryPoint を独自設定していないため、デフォルト挙動で 403 を返す
        // （トークン未提示は 403）。ここでは「health と違い保護されている」ことの確認が目的なので、
        // 401/403 のいずれか（=拒否）であることを検証する。
        assertThat(response.statusCode()).isIn(401, 403);
    }
}
