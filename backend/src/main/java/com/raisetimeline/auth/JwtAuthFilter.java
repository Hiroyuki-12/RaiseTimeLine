package com.raisetimeline.auth;

import com.raisetimeline.common.logging.LogMaskUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 認証フィルター。
 *
 * <p>HTTP リクエストが届くたびにこのフィルターが実行され、Authorization ヘッダーの JWT を検証する。 Spring Security
 * のフィルターチェーンに組み込まれており、コントローラーに到達する前に実行される。
 *
 * <p>OncePerRequestFilter を継承することで、1リクエストにつき1回だけ実行されることが保証される。
 *
 * <p>処理フロー: 1. リクエストヘッダーから "Authorization: Bearer <token>" を取得 2. JWT を検証（署名が正しい かつ 有効期限内か） 3. 有効なら
 * SecurityContextHolder に認証情報をセット（認証済み状態にする） 4. 同時に MDC に user.email_masked を載せ、以降のログに「誰のリクエストか」が
 * 紐づくようにする（PII を平文で残さないためマスク済みのみ） 5. 無効・なしの場合は何もしない（SecurityConfig の authorizeHttpRequests が 401
 * を返す）
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** MDC に格納するキー。ECS では user.email_masked フィールドにマップされる。 */
    public static final String USER_EMAIL_MASKED_MDC_KEY = "user.email_masked";

    /**
     * リクエスト属性として保存するキー。
     *
     * <p>filter の finally は LIFO で unwind するため、外側にいる AccessLogFilter が log を出す時点では JwtAuthFilter が
     * 既に MDC を clear 済み。AccessLogFilter から再参照できるよう、リクエスト属性にも値を格納しておく。
     */
    public static final String USER_EMAIL_MASKED_REQUEST_ATTR =
            "com.raisetimeline.user.email_masked";

    private final JwtUtil jwtUtil;

    /** コンストラクタ。Spring が JwtUtil を自動的に注入する（依存性注入）。 */
    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * フィルターのメイン処理。リクエストごとに呼ばれる。
     *
     * @param request クライアントからの HTTP リクエスト
     * @param response サーバーからの HTTP レスポンス
     * @param chain 次のフィルターに処理を渡すためのオブジェクト
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        boolean mdcSet = false;

        // Authorization ヘッダーを取得する（例: "Bearer eyJhbGci..."）
        String authHeader = request.getHeader("Authorization");

        // ヘッダーが存在し "Bearer " で始まる場合のみ JWT 処理を行う
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // "Bearer " の7文字を除いた部分がトークン本体
            String token = authHeader.substring(7);

            // JWT の署名と有効期限を検証する
            if (jwtUtil.isTokenValid(token)) {
                // トークンからメールアドレス（ユーザー識別子）を取り出す
                String email = jwtUtil.extractEmail(token);

                /*
                 * 認証情報オブジェクトを作成して SecurityContextHolder にセットする。
                 * これにより Spring Security が「このリクエストは認証済み」と判断する。
                 *
                 * 引数の意味:
                 *   第1引数 (principal)   : ユーザーを示す情報。ここではメールアドレス文字列
                 *   第2引数 (credentials) : パスワード等。JWT 認証では不要なので null
                 *   第3引数 (authorities) : 権限リスト。今は権限管理なしなので空リスト
                 */
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                /*
                 * 構造化ログ用に user.email_masked を MDC に入れる。
                 * email を平文で残すと PII 漏洩リスクが高いため、必ず LogMaskUtil でマスクしてから格納する。
                 * user.id は JWT に未格納のため本フィルターでは扱わない（DB ルックアップを毎リクエスト走らせない方針）。
                 * 必要になったら JWT に id クレームを追加する別 Issue で対応する。
                 */
                String masked = LogMaskUtil.maskEmail(email);
                MDC.put(USER_EMAIL_MASKED_MDC_KEY, masked);
                // AccessLogFilter から再参照できるように request 属性にも保存しておく
                request.setAttribute(USER_EMAIL_MASKED_REQUEST_ATTR, masked);
                mdcSet = true;
            }
            // トークンが無効な場合は SecurityContextHolder に何もセットしない
            // → SecurityConfig の .anyRequest().authenticated() が 401 を返す
        }

        try {
            // 次のフィルター（または最終的にコントローラー）に処理を渡す
            chain.doFilter(request, response);
        } finally {
            // スレッドプール再利用時に他リクエストへ漏れないよう、自分が入れた MDC キーだけを削除する
            if (mdcSet) {
                MDC.remove(USER_EMAIL_MASKED_MDC_KEY);
            }
        }
    }
}
