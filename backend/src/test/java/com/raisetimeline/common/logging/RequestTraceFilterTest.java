package com.raisetimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * RequestTraceFilter の単体テスト。
 *
 * <pre>
 * 対象: RequestTraceFilter.doFilterInternal
 * 技法: 同値分割 + 状態確認
 *
 * 確認観点:
 *   1. X-Trace-Id ヘッダ無し → UUID を採番してレスポンスにセット
 *   2. X-Trace-Id ヘッダ有り → 受信値をそのまま流用
 *   3. リクエスト終了後に MDC が確実にクリアされる
 * </pre>
 */
class RequestTraceFilterTest {

    @Test
    @DisplayName("ヘッダ無し: UUID を採番してレスポンスヘッダと MDC に格納し、終了後にクリアする")
    void doFilter_noIncomingHeader_generatesUuid() throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // FilterChain 実行中に MDC に trace.id が入っているかキャプチャする
        String[] capturedDuringChain = new String[1];
        FilterChain chain =
                (req, res) -> capturedDuringChain[0] = MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY);

        filter.doFilter(request, response, chain);

        assertThat(capturedDuringChain[0]).isNotNull().hasSizeGreaterThanOrEqualTo(8);
        assertThat(response.getHeader(RequestTraceFilter.TRACE_ID_HEADER))
                .isEqualTo(capturedDuringChain[0]);
        // 終了後は MDC からクリアされている
        assertThat(MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("ヘッダ有り: 受信値をそのまま MDC とレスポンスに引き継ぐ")
    void doFilter_incomingHeader_reusesValue() throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceFilter.TRACE_ID_HEADER, "external-trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] capturedDuringChain = new String[1];
        FilterChain chain =
                (req, res) -> capturedDuringChain[0] = MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY);

        filter.doFilter(request, response, chain);

        assertThat(capturedDuringChain[0]).isEqualTo("external-trace-123");
        assertThat(response.getHeader(RequestTraceFilter.TRACE_ID_HEADER))
                .isEqualTo("external-trace-123");
        assertThat(MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("FilterChain で例外が発生しても finally で MDC がクリアされる")
    void doFilter_chainThrows_clearsMdc() {
        RequestTraceFilter filter = new RequestTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain =
                (req, res) -> {
                    throw new RuntimeException("boom");
                };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {
            // 例外は意図的に発生させているため握りつぶす
        }

        assertThat(MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}
