package com.raisetimeline.exception;

import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * アプリケーション全体の例外を一元的に処理するクラス。 @RestControllerAdvice: すべてのコントローラーで発生した例外をここでキャッチして 統一した形式の JSON
 * レスポンスに変換する。 これがないと、バリデーションエラーが Spring のデフォルト形式（見づらい HTML 等）で返される。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 想定外の例外を ERROR レベルで 1 度だけログ出力するためのロガー。 */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * バリデーションエラーを JSON 形式で返す。 @Valid アノテーションによる入力チェックが失敗したときに MethodArgumentNotValidException
     * が発生する。 このメソッドがそれをキャッチして、フロントエンドが扱いやすい形式に変換する。
     *
     * <p>レスポンス例: { "errors": { "email": "有効なメールアドレスを入力してください", "password": "パスワードは8文字以上で入力してください" }
     * }
     *
     * @param ex バリデーション失敗時の例外オブジェクト（どのフィールドがどう違反したか含む）
     * @return 400 Bad Request + エラー内容の JSON
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        /*
         * FieldError のリストから「フィールド名 → エラーメッセージ」のマップを作る。
         * 同じフィールドに複数エラーがある場合は最初のメッセージを使う（(a, b) -> a の部分）。
         */
        Map<String, String> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .collect(
                                Collectors.toMap(
                                        FieldError::getField,
                                        fe ->
                                                fe.getDefaultMessage() != null
                                                        ? fe.getDefaultMessage()
                                                        : "invalid",
                                        (a, b) -> a // 同じフィールドに複数エラーがある場合は先勝ち
                                        ));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errors", errors));
    }

    /**
     * ビジネスロジック例外（ResponseStatusException）を統一 JSON 形式で返す。
     *
     * <p>AuthService では重複チェックや認証失敗時に ResponseStatusException を throw する。 このハンドラーがなければ Spring
     * のデフォルト形式（timestamp, status, error, path 等が混在） で返るため、フロントエンドがエラーメッセージを取り出しにくくなる。 ここで {
     * "message": "..." } の形式に統一することで、フロントエンドの処理を簡潔にする。
     *
     * <p>レスポンス例: { "message": "このメールアドレスは既に使用されています" }
     *
     * @param ex ResponseStatusException（HTTP ステータスとメッセージを持つ業務例外）
     * @return 例外が指定したステータスコード + { "message": "..." } の JSON
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }

    /**
     * ルーティングに該当しないリクエスト（存在しないパス）は 404 を返す。
     *
     * <p>下の汎用 Exception ハンドラーがこれを巻き込んで 500 にしてしまうのを防ぐため、 より具体的なハンドラーとして明示的に定義する。 Spring
     * の標準動作（404）に揃えつつ、レスポンスボディだけ既存のエラー JSON 形式に揃える。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Not Found"));
    }

    /**
     * 上記でハンドリングしていない、想定外の例外を最後に拾う。
     *
     * <p>各 Service / Controller で独自に try-catch して握りつぶすと、エラーログが分散したり 出ないケースが発生して運用上追えなくなる。ここで ERROR
     * ログとして 1 度だけ出すことで、 構造化ログ（trace.id 付き）から後追いできるようにする。
     *
     * <p>レスポンスはスタックトレース等を含めない最小限の JSON に留める（情報漏洩防止）。 詳細はログ側で確認する運用とする。
     *
     * @param ex 想定外の例外
     * @return 500 Internal Server Error + 汎用メッセージ
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        // ロガーが第2引数に Throwable を取ると、ECS フォーマットでは error.type / error.stack_trace に展開される
        log.error("未捕捉例外を検出しました", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "サーバー内部エラーが発生しました"));
    }
}
