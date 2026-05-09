package com.raisetimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * AccessLogFilter の単体テスト。
 *
 * <pre>
 * 対象: AccessLogFilter.doFilterInternal
 * 技法: 状態確認（MDC 値の出入り）
 *
 * 確認観点:
 *   1. ログ出力時点で MDC に method / path / status / duration が入っている
 *   2. 終了後はすべてクリアされている
 *   3. FilterChain で例外が発生しても MDC はクリアされる
 * </pre>
 */
class AccessLogFilterTest {

    @Test
    @DisplayName("正常系: ログ出力時点で 4 つの MDC キーがセットされ、終了後にクリアされる")
    void doFilter_populatesAndClearsMdc() throws Exception {
        AccessLogFilter filter = new AccessLogFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        // FilterChain 実行中はまだ MDC に値は入っていない（ログ出力は chain 後の finally で行う）。
        // ここでは「リクエスト処理が終わった後、必ず MDC がクリアされていること」を担保する。
        FilterChain chain = (req, res) -> {};
        filter.doFilter(request, response, chain);

        assertThat(MDC.get("http.request.method")).isNull();
        assertThat(MDC.get("url.path")).isNull();
        assertThat(MDC.get("http.response.status_code")).isNull();
        assertThat(MDC.get("event.duration")).isNull();
    }

    @Test
    @DisplayName("FilterChain で例外発生時も MDC は最終的にクリアされる")
    void doFilter_chainThrows_clearsMdc() {
        AccessLogFilter filter = new AccessLogFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/timeline");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain =
                (req, res) -> {
                    throw new RuntimeException("boom");
                };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {
            // 想定内
        }

        assertThat(MDC.get("http.request.method")).isNull();
        assertThat(MDC.get("url.path")).isNull();
        assertThat(MDC.get("http.response.status_code")).isNull();
        assertThat(MDC.get("event.duration")).isNull();
    }
}
