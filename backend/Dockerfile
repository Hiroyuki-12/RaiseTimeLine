# ==========================================================================
# backend(Spring Boot) コンテナイメージのビルド定義
# ==========================================================================
# マルチステージビルドを採用する。
# - 第1段(build): JDK + Gradle で app.jar を作る（重いビルド道具が必要）
# - 第2段(runtime): JRE だけの軽いイメージに app.jar をコピーして実行する
# こうすると最終イメージにビルド道具を含めず、小さく・攻撃面の少ないイメージにできる。

# --------------------------------------------------------------------------
# 第1段: ビルドステージ
# --------------------------------------------------------------------------
# Java 25 の JDK 入りイメージ。ここで Gradle ビルドを行う。
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 依存解決を Docker レイヤキャッシュに乗せるため、先に Gradle 関連ファイルだけコピーする。
# （ソースだけが変わったときに依存ダウンロードを再実行せず、ビルドを速くする）
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x ./gradlew

# 続いてソースをコピーする。
COPY src ./src

# bootJar（実行可能 fat jar）だけを作る。テストはCIで実行済みのためここでは省略して高速化する。
# --no-daemon: コンテナ内では Gradle デーモンを常駐させない（1回ビルドして終わるため）。
RUN ./gradlew bootJar -x test --no-daemon

# --------------------------------------------------------------------------
# 第2段: 実行ステージ
# --------------------------------------------------------------------------
# Java 25 の JRE のみ。実行に必要な最小限だけを含む軽量イメージ。
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# セキュリティのため root ではなく非特権ユーザーで実行する。
# 万一コンテナ内で侵害されても権限を最小化できる。
RUN useradd --create-home --shell /usr/sbin/nologin appuser
USER appuser

# 第1段で作った jar（plain jar は build.gradle.kts で無効化済みなので 1 つだけ）をコピーする。
COPY --from=build /workspace/build/libs/*.jar app.jar

# アプリは 8080 で待ち受ける（ポート運用ルール: バックエンドは必ず 8080）。
EXPOSE 8080

# コンテナ起動時に Spring Boot アプリを起動する。
# 環境変数(SPRING_DATASOURCE_URL 等)や SPRING_PROFILES_ACTIVE は ECS タスク定義から注入される。
ENTRYPOINT ["java", "-jar", "app.jar"]
