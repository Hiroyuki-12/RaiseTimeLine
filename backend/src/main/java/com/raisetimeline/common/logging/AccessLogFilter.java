package com.raisetimeline.common.logging;

import com.raisetimeline.auth.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * アクセスログを 1 リクエスト 1 行で出力するフィルター。
 *
 * <p>Controller 個別に log.info を書かずに済むよう、共通フィルターでメソッド・パス・ステータス・処理時間をまとめて出す。 ECS
 * フォーマットの該当フィールドに自動展開されるよう、MDC に下記キーで一時的に値を入れる:
 *
 * <ul>
 *   <li>http.request.method
 *   <li>url.path
 *   <li>http.response.status_code
 *   <li>event.duration（ミリ秒）
 * </ul>
 *
 * <p>RequestTraceFilter の後ろ（= trace.id がもう MDC に入った状態）で動かすため、 LoggingConfig の order を
 * RequestTraceFilter よりわずかに後ろに設定する。
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    private static final String MDC_METHOD = "http.request.method";
    private static final String MDC_PATH = "url.path";
    private static final String MDC_STATUS = "http.response.status_code";
    private static final String MDC_DURATION = "event.duration";
    private static final String MDC_USER_EMAIL_MASKED = JwtAuthFilter.USER_EMAIL_MASKED_MDC_KEY;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();
        boolean userEmailRestored = false;
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            try {
                MDC.put(MDC_METHOD, request.getMethod());
                MDC.put(MDC_PATH, request.getRequestURI());
                MDC.put(MDC_STATUS, String.valueOf(response.getStatus()));
                MDC.put(MDC_DURATION, String.valueOf(durationMs));

                // 認証フィルターは内側にあり、ここに来る前に MDC を clear している。
                // フィルターチェーンを抜けた後でも user.email_masked をアクセスログに載せるため、
                // request 属性経由で再注入する（JwtAuthFilter が認証成功時に保存している）。
                Object emailMasked =
                        request.getAttribute(JwtAuthFilter.USER_EMAIL_MASKED_REQUEST_ATTR);
                if (emailMasked instanceof String s && !s.isEmpty()) {
                    MDC.put(MDC_USER_EMAIL_MASKED, s);
                    userEmailRestored = true;
                }

                // メッセージ自体は人間可読用。機械処理用の値は MDC 経由で構造化フィールドに載る
                log.info(
                        "HTTP {} {} -> {} ({}ms)",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        durationMs);
            } finally {
                // 自分が入れたキーだけクリアする
                MDC.remove(MDC_METHOD);
                MDC.remove(MDC_PATH);
                MDC.remove(MDC_STATUS);
                MDC.remove(MDC_DURATION);
                if (userEmailRestored) {
                    MDC.remove(MDC_USER_EMAIL_MASKED);
                }
            }
        }
    }
}
