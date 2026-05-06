package com.raisetimeline.exception;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * アプリケーション全体の例外を一元的に処理するクラス。 @RestControllerAdvice: すべてのコントローラーで発生した例外をここでキャッチして 統一した形式の JSON
 * レスポンスに変換する。 これがないと、バリデーションエラーが Spring のデフォルト形式（見づらい HTML 等）で返される。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
