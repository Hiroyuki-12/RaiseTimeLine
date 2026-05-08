// バックエンドのビルド設定ファイル（Gradle Kotlin DSL）
// Spring Boot 4.0.0 + Java 25 の構成

plugins {
    java
    id("org.springframework.boot") version "4.0.0"
    // Spring Boot の依存バージョン管理を自動で行うプラグイン
    // これにより各ライブラリのバージョンを個別に指定しなくてよくなる
    id("io.spring.dependency-management") version "1.1.7"
    // Spotless: コードフォーマッターの自動適用・チェックプラグイン
    // ./gradlew spotlessCheck でフォーマット違反を検出
    // ./gradlew spotlessApply でフォーマットを自動修正
    id("com.diffplug.spotless") version "7.0.4"
}

group = "com.raisetimeline"
version = "0.0.1-SNAPSHOT"

java {
    // Java 25 (LTS) を使用する
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    // Maven Central から依存ライブラリを取得する
    mavenCentral()
}

dependencies {
    // === Spring Boot コア ===
    // HTTP リクエストを受け付けるための Web フレームワーク（REST API 用）
    implementation("org.springframework.boot:spring-boot-starter-web")

    // 認証・認可を担当する Spring Security
    // ログイン必須ページの保護や JWT 検証フィルターの組み込みに使う
    implementation("org.springframework.boot:spring-boot-starter-security")

    // リクエストのバリデーション（入力チェック）ライブラリ
    // @NotBlank, @Email, @Size などのアノテーションが使えるようになる
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // === MyBatis（ORM） ===
    // SQL を XML に書いて Java のメソッドに対応させる O/R マッパー
    // Spring Data JPA（自動SQL生成）ではなく、手書き SQL が明示的で学習しやすい MyBatis を採用
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0")

    // === DB マイグレーション（Flyway） ===
    // DB スキーマの変更履歴を SQL ファイルで管理するツール
    // アプリ起動時に未適用のマイグレーションを自動実行する
    // spring-boot-starter-flyway: Spring Boot の自動設定を含む Flyway スターター
    // flyway-core 単体より確実に起動時に FlywayMigrationInitializer が動く
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    // PostgreSQL 専用の Flyway サポートモジュール（PostgreSQL 固有の方言に対応）
    implementation("org.flywaydb:flyway-database-postgresql")

    // === DB ドライバー ===
    // PostgreSQL への接続に必要な JDBC ドライバー（実行時のみ必要）
    runtimeOnly("org.postgresql:postgresql")

    // === JWT（JSON Web Token）ライブラリ JJWT 0.12.x ===
    // JWT の生成・検証に使う。3モジュールに分かれている:
    //   jjwt-api     : JWT 操作の API インターフェース定義（コンパイル時に必要）
    //   jjwt-impl    : API の実装（実行時のみ必要）
    //   jjwt-jackson : JSON のシリアライズ/デシリアライズに Jackson を使う（実行時のみ必要）
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // === AWS S3（画像アップロード） ===
    // AWS SDK v2 の S3 クライアント。プロフィール画像・投稿画像を S3 バケットに保存するために使う。
    // v1（com.amazonaws）は Deprecated のため v2（software.amazon.awssdk）を使用する。
    implementation("software.amazon.awssdk:s3:2.25.23")

    // === テスト ===
    // Spring Boot のテストサポート（JUnit 5, MockMvc など）
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Security のテストサポート（@WithMockUser など）
    testImplementation("org.springframework.security:spring-security-test")
    // JUnit 5 のテストランナー（テスト実行エンジン）
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    // JUnit 5 (JUnit Platform) でテストを実行する設定
    useJUnitPlatform()
}

/*
 * Spotless コードフォーマット設定
 * google-java-format を使って Java コードのスタイルを統一する。
 * Google Java Style Guide に沿ったインデント・改行・インポート順が強制される。
 *
 * 使い方:
 *   ./gradlew spotlessCheck  → フォーマット違反の検出（CI でのチェックに使う）
 *   ./gradlew spotlessApply  → フォーマットの自動修正（コミット前に実行）
 */
spotless {
    java {
        // google-java-format でフォーマット（AOSP スタイル = インデント4スペース）
        googleJavaFormat().aosp()
        // 未使用 import の自動削除
        removeUnusedImports()
        // ファイル末尾の空白を削除
        trimTrailingWhitespace()
        // ファイル末尾の改行を統一
        endWithNewline()
    }
}
