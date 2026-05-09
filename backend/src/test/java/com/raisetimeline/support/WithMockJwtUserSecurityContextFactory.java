package com.raisetimeline.support;

import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * {@link WithMockJwtUser} 用の SecurityContext 生成ファクトリー。
 *
 * <p>本番の JwtAuthFilter と同じ形 (principal = email の String、credentials = null、authorities = 空) の
 * Authentication を組み立てて SecurityContext に詰める。これにより Controller スライステストで 本番に限りなく近い認証済みリクエストを再現できる。
 */
public class WithMockJwtUserSecurityContextFactory
        implements WithSecurityContextFactory<WithMockJwtUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockJwtUser annotation) {
        // SecurityContextHolder.createEmptyContext() で空のコンテキストを作り、
        // そこに本番と同じ形の Authentication を入れる。
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(annotation.email(), null, List.of());
        context.setAuthentication(authentication);
        return context;
    }
}
