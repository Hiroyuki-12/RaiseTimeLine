package com.raisetimeline.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Controller スライステスト用のテストアノテーション。
 *
 * <p>本番の JwtAuthFilter は、JWT を検証したあと principal にユーザーのメールアドレス（String）を入れた
 * UsernamePasswordAuthenticationToken を SecurityContext にセットする。 Spring Security 標準の
 * {@code @WithMockUser} を使うと principal は org.springframework.security.core.userdetails.User
 * 型になるため、本番の挙動とズレてしまう。
 *
 * <p>このアノテーションは本番と同じ「principal = email 文字列」の SecurityContext を構築するため、 Controller がメールアドレス (String)
 * を Authentication#getPrincipal で受け取る前提のコードを 安全にテストできる。
 *
 * <p>使用例:
 *
 * <pre>
 *   &#64;Test
 *   &#64;WithMockJwtUser(email = "alice@example.com")
 *   void 認証済ユーザーで投稿が作成できる() { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockJwtUserSecurityContextFactory.class)
public @interface WithMockJwtUser {

    /** SecurityContext にセットする principal のメールアドレス。 */
    String email() default "test-user@example.com";
}
