# バックエンド構造化ログ設計仕様書

運用監視・障害解析・アクセス監査のため、バックエンド（Spring Boot）のログを構造化（1 行 1 JSON）で出力するための設計仕様書。本仕様書は **設計のみ** を扱い、実装（依存追加・設定変更・フィルタ実装）は別 Issue で対応する。

関連 Issue: [#29](https://github.com/Hiroyuki-12/RaiseTimeLine/issues/29)

## 1. 目的とスコープ

### 目的

| 観点 | 何を実現するか |
|------|----------------|
| 運用監視 | アプリの稼働状況・異常発生をログから機械的に把握できる |
| 障害解析 | エラー発生時に、当該リクエストのトレースを横断的に追える |
| アクセス監査 | 誰が・いつ・どの API を叩いたかを後追いできる |

### スコープ

- **対象**: バックエンド（Spring Boot）のサーバーログ
- **対象外**: フロントエンド（ブラウザ）のログ。ブラウザ起因のエラー収集は本仕様の範囲外
- **対象外**: アプリ外のインフラログ（Nginx / RDS / ALB 等）。それぞれの基盤側で別途扱う

## 2. 前提と基本方針

| 前提 | 内容 |
|------|------|
| 出力形式 | 1 行 1 JSON（構造化ログ）。プレーンテキストは使用しない |
| 出力先 | 標準出力 (stdout) のみ |
| ベース技術 | Spring Boot 標準の Structured Logging（Spring Boot 3.4+ で組み込み）。`logstash-logback-encoder` 等の追加ライブラリは導入しない |
| フィールド規約 | ECS (Elastic Common Schema) に準拠。Spring Boot 標準で `ecs` フォーマットがサポートされている |
| 課金 | 有料 SaaS（Datadog / Splunk 等）には依存しない |

### 出力先を stdout のみとする理由

- アプリ側でファイル出力やローテーションを抱えると、コンテナ環境（Docker / EC2 上 Docker / 将来の ECS 等）でディスクが圧迫されるリスクがある
- 12-factor app の「Logs」原則に従い、アプリは stdout に流すだけ・収集と保管は実行基盤側に任せる方針が運用上シンプル
- 現在の AWS 構成（EC2 + Docker、`docs/aws-architecture.md` 参照）でも `docker logs` 経由で参照可能

## 3. ログレベルの使い分け

| レベル | 用途 | 例 |
|--------|------|-----|
| ERROR | 運用者の対応が必要な異常。継続稼働できない / データ不整合の可能性 | DB 接続不可、未捕捉例外、外部 API（S3）の永続的失敗 |
| WARN  | 想定内だが注意すべき事象。単発で運用者を起こすほどではない | 認証失敗、入力バリデーションエラー、リトライで回復した一時障害 |
| INFO  | 業務上の重要イベント。「誰が何をしたか」を追える粒度 | ログイン成功、投稿作成、フォロー、いいね |
| DEBUG | 開発時の詳細トレース。本番では無効 | 内部メソッドの引数 / 戻り値、SQL 実行内容 |

### 判定ガイド

```
そのログを見て運用者が対応する必要があるか？
 ├─ ある（即対応）              → ERROR
 ├─ あるかもしれない（要確認） → WARN
 └─ ない（記録目的）
     ├─ 業務イベントとして残したい → INFO
     └─ 開発者向けの内部情報       → DEBUG
```

### 本番のデフォルトレベル

- ルート: `INFO`
- `com.raisetimeline`: `INFO`（必要に応じてパッケージ単位で調整）
- `org.springframework.web`: `INFO`
- DEBUG は本番で有効化しない

## 4. 共通フィールド定義

ECS（Elastic Common Schema）に準拠したフィールド名を使用する。Spring Boot の `logging.structured.format.console=ecs` を有効化すると、以下のうち基本フィールドは自動で出力される。

### 自動出力（Spring Boot 標準 / ECS）

| フィールド | 型 | 必須 | 内容 |
|------------|-----|------|------|
| `@timestamp` | string | ○ | ISO 8601 形式のタイムスタンプ |
| `log.level` | string | ○ | ERROR / WARN / INFO / DEBUG |
| `log.logger` | string | ○ | ロガー名（クラス FQCN） |
| `message` | string | ○ | 人間可読なメッセージ |
| `process.thread.name` | string | ○ | スレッド名 |
| `service.name` | string | ○ | アプリ名（`spring.application.name` から自動） |
| `error.type` | string | △ | 例外発生時の例外クラス FQCN |
| `error.message` | string | △ | 例外メッセージ |
| `error.stack_trace` | string | △ | スタックトレース |

### 業務拡張フィールド（MDC 経由で付与）

| フィールド | 型 | 必須 | 内容 | 付与箇所 |
|------------|-----|------|------|----------|
| `trace.id` | string | △ | リクエスト相関 ID（UUID） | リクエスト受付時のフィルタ |
| `user.id` | number | △ | 認証済みユーザーの `users.id`。現状の JWT には id クレームが無く、毎リクエストで DB を引くと負荷が増えるため **本仕様の初期実装では出力しない**。必要になったら JWT に id クレームを追加する別 Issue で対応する | （現状未対応） |
| `user.email_masked` | string | △ | マスク済みメール（PII） | JwtAuthFilter（認証成功時） |
| `http.request.method` | string | △ | リクエストメソッド | アクセスログフィルタ |
| `url.path` | string | △ | リクエストパス | アクセスログフィルタ |
| `http.response.status_code` | number | △ | HTTP ステータス | アクセスログフィルタ |
| `event.duration` | number | △ | 処理時間（ms） | アクセスログフィルタ |

### 出力例

INFO レベルの業務ログ:

```json
{
  "@timestamp": "2026-05-09T10:23:45.123Z",
  "log.level": "INFO",
  "log.logger": "com.raisetimeline.post.PostService",
  "message": "ポスト作成: postId=42, userId=7",
  "process.thread.name": "http-nio-8080-exec-3",
  "service.name": "raise-time-line",
  "trace.id": "8b2f6c9e-1a2b-4c3d-9e0f-112233445566",
  "user.id": 7,
  "user.email_masked": "ot***@gmail.com"
}
```

ERROR レベルの例外ログ:

```json
{
  "@timestamp": "2026-05-09T10:24:01.456Z",
  "log.level": "ERROR",
  "log.logger": "com.raisetimeline.common.GlobalExceptionHandler",
  "message": "未捕捉例外を検出しました",
  "process.thread.name": "http-nio-8080-exec-5",
  "service.name": "raise-time-line",
  "trace.id": "1c4d7e0a-2b3c-4d5e-8f9a-99aabbccddee",
  "user.id": 7,
  "error.type": "java.lang.NullPointerException",
  "error.message": "Cannot invoke \"User.getId()\" because \"user\" is null",
  "error.stack_trace": "java.lang.NullPointerException: ...\n\tat ..."
}
```

## 5. リクエスト相関（trace.id / user）

1 件のリクエストに紐づくすべてのログを横串で追えるよう、リクエスト単位の相関 ID を `trace.id` として MDC（Mapped Diagnostic Context）に格納する。MDC に入れた値は ECS フォーマットで JSON フィールドとして自動展開される。

### 仕込みの方針

| 何を | どこで |
|------|--------|
| `trace.id` の採番（UUID v4） | リクエスト受付時の共通フィルタ（[backend/src/main/java/com/raisetimeline/auth/JwtAuthFilter.java](../backend/src/main/java/com/raisetimeline/auth/JwtAuthFilter.java) の前段、または別フィルタを新設） |
| `user.id` / `user.email_masked` の付与 | JwtAuthFilter で JWT 検証成功後、SecurityContextHolder にプリンシパルを入れた直後 |
| MDC のクリア | リクエスト終了時（`finally` ブロックで `MDC.clear()`）。スレッドプール再利用時の漏れを防ぐ |
| クライアント側への返却 | レスポンスヘッダ `X-Trace-Id` に `trace.id` を載せる。フロントから問い合わせを受けた際に追跡しやすくする |

### 既存リクエストヘッダとの関係

- クライアントが `X-Trace-Id` ヘッダを送ってきた場合は、それを優先して使用する（マイクロサービス間連携を想定）
- ヘッダが無ければサーバー側で UUID を採番する

## 6. 機密情報のマスキング方針

| 種別 | 取り扱い |
|------|----------|
| パスワード | **絶対に出さない**。リクエストボディに含まれていてもログに出力しない |
| JWT トークン本体 | **絶対に出さない**。Authorization ヘッダ全文をログに残さない |
| メールアドレス | **マスクして出力**。`user.email_masked` として `先頭2文字 + *** + @ドメイン` 形式（例: `ot***@gmail.com`） |
| ユーザー入力（投稿本文・コメント等） | INFO レベルでは出力しない。ID のみで十分。トラブルシュート時に DEBUG で限定出力する |
| 画像ファイル名 / S3 キー | 出力可。ただし署名付き URL（クエリパラメータ含む）は出さない |

### 実装上の決め事

- マスキングはログを書く側の責務とし、共通ユーティリティ（例: `LogMaskUtil.maskEmail(String)`）を 1 つ用意する
- 「うっかり出さない」ためのレビュー観点として、本仕様書のチェックリストを PR テンプレートから参照する

## 7. 出力先と運用

### 出力先

| 環境 | 出力先 | 参照方法 |
|------|--------|----------|
| ローカル（`./gradlew bootRun`） | ターミナル | そのまま画面で確認 |
| ローカル（Docker Compose） | コンテナの stdout | `docker compose logs backend` |
| 本番（EC2 + Docker） | コンテナの stdout | `docker logs <container>`。必要に応じて CloudWatch Logs エージェントで集約（別途検討） |

### ローテーションと永続化

- アプリではローテーションを行わない
- ローカルで長時間動かす場合は、開発者が必要に応じて `tee` 等で手元のファイルに残す
- 本番でログを長期保管したい場合は、運用基盤側（Docker ログドライバ / CloudWatch Logs / S3 アーカイブ等）で対応する。本仕様書の対象外

### 開発時の見やすさ

- `dev` プロファイルでも JSON で出力するのを基本とする（本番との差異を減らす）
- 1 行 JSON が読みづらい場合は、ターミナル側で `| jq` を通して整形する運用とする
- どうしてもプレーンテキストが欲しい開発者は、ローカルで `logging.structured.format.console=` を空にして起動する（個人設定の範囲）

## 8. ログを書く場所のガイドライン

| レイヤ | ログを書くか | 内容 |
|--------|--------------|------|
| Controller | 書かない（共通化） | アクセスログは共通フィルタで一括出力。Controller 内で個別の `log.info` は原則書かない |
| Service | 書く | 業務イベント（INFO）と業務エラー（WARN/ERROR）。「誰が何をしたか」が分かる粒度 |
| Repository / Mapper | 書かない | SQL ログは Spring の `logging.level.org.hibernate.SQL=DEBUG` 等で必要時に有効化 |
| 共通フィルタ | 書く | アクセスログ（メソッド・パス・ステータス・処理時間）を INFO で 1 リクエスト 1 行 |
| `@ControllerAdvice` | 書く | 未捕捉例外を ERROR で 1 度だけ。各層で重複ログしない |

### 既存ログとの整合

現状の Service クラスでは以下のようなパラメータ化ログが書かれている（[backend/src/main/java/com/raisetimeline/post/PostService.java](../backend/src/main/java/com/raisetimeline/post/PostService.java) 等）。

```java
log.info("ポスト作成: postId={}, userId={}", saved.id(), user.getId());
```

このパターンは維持する。SLF4J のプレースホルダ形式 (`{}`) を使い、文字列連結 (`"... " + var`) は禁止（DEBUG 無効時の不要な文字列生成を避けるため）。

## 9. 実装計画（別 Issue で対応）

本仕様書のマージ後、以下の作業項目を別 Issue として起票し実装する。

| # | 作業項目 | 概要 |
|---|----------|------|
| 1 | application.properties で構造化ログを有効化 | `logging.structured.format.console=ecs` を追加。`spring.application.name` を確認 |
| 2 | リクエスト相関フィルタの実装 | `trace.id` を MDC に仕込む `OncePerRequestFilter`。レスポンスヘッダ `X-Trace-Id` も付与 |
| 3 | JwtAuthFilter の拡張 | 認証成功時に `user.id` / `user.email_masked` を MDC に追加。`finally` で `MDC.clear()` |
| 4 | アクセスログフィルタの実装 | メソッド / パス / ステータス / 処理時間を INFO で 1 行出力 |
| 5 | マスキングユーティリティの作成 | `LogMaskUtil.maskEmail(String)` 等。単体テスト付き |
| 6 | `@ControllerAdvice` の整理 | 未捕捉例外を ERROR ログで 1 回だけ出すよう統一 |
| 7 | サンプルログの動作確認 | 主要 API（ログイン・投稿作成・いいね等）を叩き、想定どおりの JSON が出ることを確認 |
| 8 | 非機能要件ドキュメントの更新 | [docs/non-functional-requirements.md](non-functional-requirements.md) のログ行を本仕様書へのリンクに差し替え |

## 10. 参考リンク

- [Spring Boot Reference - Structured Logging](https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured)
- [Elastic Common Schema (ECS) Field Reference](https://www.elastic.co/guide/en/ecs/current/ecs-field-reference.html)
- [The Twelve-Factor App - Logs](https://12factor.net/logs)
