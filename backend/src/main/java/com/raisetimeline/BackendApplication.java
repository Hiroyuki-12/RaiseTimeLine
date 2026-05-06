package com.raisetimeline;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot アプリケーションのエントリーポイント（起動クラス）。 @SpringBootApplication:
 * コンポーネントスキャン・自動設定・設定クラス読み込みを一括で有効にする。 @MapperScan: MyBatis の Mapper インターフェースが置かれているパッケージを指定する。
 * このアノテーションがないと @Mapper アノテーションが付いていても Spring に認識されない。
 */
@SpringBootApplication
// Mapper インターフェースが置かれているパッケージをすべて列挙する
// user: UserMapper、auth: RefreshTokenMapper、post: PostMapper を含む
@MapperScan({"com.raisetimeline.user", "com.raisetimeline.auth", "com.raisetimeline.post"})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
