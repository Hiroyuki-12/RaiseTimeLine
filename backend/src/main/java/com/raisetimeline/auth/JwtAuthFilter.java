package com.raisetimeline.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
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
 * SecurityContextHolder に認証情報をセット（認証済み状態にする） 4. 無効・なしの場合は何もしない（SecurityConfig の
 * authorizeHttpRequests が 401 を返す）
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

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
            }
            // トークンが無効な場合は SecurityContextHolder に何もセットしない
            // → SecurityConfig の .anyRequest().authenticated() が 401 を返す
        }

        // 次のフィルター（または最終的にコントローラー）に処理を渡す
        chain.doFilter(request, response);
    }
}
