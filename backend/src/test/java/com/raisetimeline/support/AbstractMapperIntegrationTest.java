package com.raisetimeline.support;

import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Mapper DB 統合テストの共通基底クラス。
 *
 * <p>本番が PostgreSQL であり、Mapper には PostgreSQL 固有の SQL (INSERT/UPDATE ... RETURNING、ON CONFLICT DO
 * NOTHING、ILIKE 等) が含まれる。H2 では一部 SQL が解釈できないため、Testcontainers で本物の PostgreSQL コンテナを起動し、本番と同じ DB
 * エンジンで Mapper の SQL を検証する。
 *
 * <p>本番 DB に直接書き込まないという要件はテスト終了時にコンテナを破棄することで維持される (Docker が状態を一切残さない)。
 *
 * <p>{@code static} フィールドで {@link PostgreSQLContainer} を保持することで、 JVM 内の全テストクラスでコンテナを 1 つだけ起動する
 * Singleton Container パターンを採る。 これによりテストクラスごとにコンテナを立ち上げるオーバーヘッド (~5 秒) を抑える。
 *
 * <p>{@code @ServiceConnection} は Spring Boot 3.1+ で導入された機構で、Testcontainers の接続情報を Spring の
 * DataSource に自動配線する。手動で {@code @DynamicPropertySource} を書く必要がない。
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractMapperIntegrationTest {

    /**
     * テスト用 PostgreSQL コンテナ。
     *
     * <ul>
     *   <li>postgres:16-alpine — 本番と同じメジャーバージョン (16) の軽量イメージ
     *   <li>static — JVM 全体で 1 つだけ起動する Singleton パターン
     *   <li>{@code @ServiceConnection} — Spring の DataSource に自動配線
     * </ul>
     */
    @SuppressWarnings("resource")
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("raisetimeline_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        // static イニシャライザで JVM 起動時に 1 度だけコンテナを立ち上げる。
        // @Testcontainers + static フィールドの組み合わせでも自動起動するが、
        // 明示的に start() しておくことで起動タイミングが分かりやすくなる。
        POSTGRES.start();
    }
}
