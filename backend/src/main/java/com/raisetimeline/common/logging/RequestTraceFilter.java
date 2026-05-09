package com.raisetimeline.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエスト相関 ID（trace.id）を採番し、MDC とレスポンスヘッダに載せるフィルター。
 *
 * <p>1 件のリクエストから派生するすべてのログに同じ trace.id を含めることで、 後からログを横断検索したときに「このリクエストに関連するログ群」をまとめて追える。
 *
 * <p>処理フロー: 1. クライアントが X-Trace-Id ヘッダを送ってきた場合はそれを優先（マイクロサービス間連携を想定） 2. 無ければ UUID v4 を採番 3. MDC に
 * "trace.id" キーで格納（ECS フォーマットで JSON フィールドに自動展開される） 4. レスポンスヘッダ X-Trace-Id
 * にも載せる（フロントから問い合わせを受けた際に追跡しやすい） 5. リクエスト終了時に MDC から trace.id を削除する（スレッドプール再利用時の漏れを防ぐ）
 *
 * <p>Spring Security のフィルターチェーンより前に動かすため、@Component ではなく LoggingConfig の FilterRegistrationBean
 * 経由で登録する。
 */
public class RequestTraceFilter extends OncePerRequestFilter {

    /** リクエスト / レスポンスの両方で使うトレース ID 用ヘッダ名。 */
    static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC に格納するキー。ECS では trace.id フィールドにマップされる。 */
    static final String TRACE_ID_MDC_KEY = "trace.id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId =
                (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        // レスポンスヘッダにも載せておくことで、フロントエンド / クライアント側でも同じ ID を参照できる
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            // 自分が入れたキーだけ削除する（他フィルターが入れた MDC キーは触らない）
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
