package com.raisetimeline.common.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 構造化ログ用フィルターをサーブレットフィルターとして登録する設定クラス。
 *
 * <p>RequestTraceFilter / AccessLogFilter は Spring Security のフィルターチェーンより前に動かしたい。 そのため @Component
 * での自動検出に任せず、FilterRegistrationBean で明示的に登録し、 Spring Security の FilterChainProxy （デフォルト order =
 * -100）より高い優先度（HIGHEST_PRECEDENCE）でフックする。
 *
 * <p>狙い: - Security 側で 401/403 を返す場合もアクセスログとして記録できる - JwtAuthFilter で MDC に user.email_masked
 * を入れる時点で、すでに trace.id が入っている状態にする
 */
@Configuration
public class LoggingConfig {

    /** リクエスト相関 ID を採番するフィルターを最優先で登録する。 これにより以降のすべてのログ（Security のログを含む）に trace.id が付与される。 */
    @Bean
    public FilterRegistrationBean<RequestTraceFilter> requestTraceFilterRegistration() {
        FilterRegistrationBean<RequestTraceFilter> reg =
                new FilterRegistrationBean<>(new RequestTraceFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    /** アクセスログ用フィルターを RequestTraceFilter の直後に登録する。 trace.id を MDC に入れた状態で全リクエストの開始 / 終了時刻を計測したい。 */
    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilterRegistration() {
        FilterRegistrationBean<AccessLogFilter> reg =
                new FilterRegistrationBean<>(new AccessLogFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return reg;
    }
}
