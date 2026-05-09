package com.raisetimeline.support;

import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Controller テストで SecurityContextHolder を手動操作するためのヘルパー。
 *
 * <p>Spring Boot 4.0 で @WebMvcTest / @AutoConfigureMockMvc が削除されたため、 Controller スライステストは
 * MockMvcBuilders.standaloneSetup() で構築する。 standaloneSetup では Spring Security の
 * TestExecutionListener が動かないため、 @WithMockJwtUser のようなアノテーションが効かない。
 *
 * <p>そのため各テストの @BeforeEach で {@link #setAuthenticatedUser(String)} を呼んで SecurityContext
 * を組み立て、@AfterEach で {@link #clear()} を呼んで他テストへの漏れを防ぐ。
 */
public final class MockMvcSecurity {

    private MockMvcSecurity() {}

    /**
     * 本番 JwtAuthFilter と同形式 (principal=email 文字列、authorities 空) の Authentication を
     * SecurityContextHolder にセットする。
     */
    public static void setAuthenticatedUser(String email) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        SecurityContextHolder.setContext(ctx);
    }

    /** SecurityContext をクリアして他テストに状態が漏れないようにする。 */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
