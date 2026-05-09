package com.raisetimeline;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * アプリケーション全体のコンテキストロードを確認するスモークテスト。
 *
 * <p>{@code @ActiveProfiles("test")} を付けることで、本番用 application.properties ではなく
 * src/test/resources/application-test.yml が読み込まれる。これにより:
 *
 * <ul>
 *   <li>テストが本番開発用 PostgreSQL に接続して既存データを汚すリスクを排除する
 *   <li>H2 インメモリ DB に Flyway マイグレーション (V1〜) を流し、本番マイグレーションが H2 でも動くことを毎回検証する
 * </ul>
 *
 * <p>Mapper / Service / Controller の各テストでも同じ test プロファイルを使うため、 ここがグリーンであることはテスト基盤全体の前提となる。
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @Test
    void contextLoads() {}
}
