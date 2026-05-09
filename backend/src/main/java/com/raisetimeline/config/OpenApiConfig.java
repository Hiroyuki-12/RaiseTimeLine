package com.raisetimeline.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi の追加設定クラス。
 *
 * <p>このアプリの API は JWT (Bearer トークン) 認証を採用しているため、Swagger UI 上でも "Authorize"
 * ボタンからアクセストークンを入力すれば認証付きエンドポイントを試行できるようにする。 そのために OpenAPI の SecurityScheme として "bearerAuth"
 * を定義し、グローバルに適用する。
 *
 * <p>認証不要なエンドポイント（/api/auth/login など）は、各 Controller のメソッドに {@code @SecurityRequirements({})}
 * を付けることでこのグローバル要件を打ち消し、UI 上で「鍵マークなし」として表示される。
 */
@Configuration
public class OpenApiConfig {

    /** SecurityScheme の名前。Controller 側で参照する文字列と一致させる必要がある。 */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * カスタム OpenAPI 定義を Bean として公開する。 springdoc-openapi はこの Bean を検出すると、自動生成した仕様にこの設定をマージする。
     *
     * @return タイトル・バージョン・JWT セキュリティスキームを含む OpenAPI 定義
     */
    @Bean
    public OpenAPI raiseTimeLineOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("RaiseTimeLine API")
                                .version("v1")
                                .description(
                                        "RaiseTimeLine の REST API 仕様書。"
                                                + "認証付きエンドポイントを試す場合は、右上の Authorize から"
                                                + " アクセストークン (Bearer JWT) を入力してください。"))
                /*
                 * SecurityScheme: "bearerAuth" を HTTP Bearer (JWT) として定義。
                 * Swagger UI に "Authorize" ボタンが出るようになり、
                 * 入力したトークンが各リクエストの Authorization ヘッダーに付与される。
                 */
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_NAME,
                                        new SecurityScheme()
                                                .name(SECURITY_SCHEME_NAME)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                /*
                 * グローバルにこのスキームを必須として適用する。
                 * 認証不要のエンドポイントは Controller 側で @SecurityRequirements({}) により打ち消す。
                 */
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
