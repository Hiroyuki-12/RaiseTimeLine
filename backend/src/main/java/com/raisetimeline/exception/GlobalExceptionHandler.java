package com.raisetimeline.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * アプリケーション全体の例外を一元的に処理するクラス。
 *
 * @RestControllerAdvice: すべてのコントローラーで発生した例外をここでキャッチして
 * 統一した形式の JSON レスポンスに変換する。
 * これがないと、バリデーションエラーが Spring のデフォルト形式（見づらい HTML 等）で返される。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * バリデーションエラーを JSON 形式で返す。
     *
     * @Valid アノテーションによる入力チェックが失敗したときに MethodArgumentNotValidException が発生する。
     * このメソッドがそれをキャッチして、フロントエンドが扱いやすい形式に変換する。
     *
     * レスポンス例:
     * {
     *   "errors": {
     *     "email": "有効なメールアドレスを入力してください",
     *     "password": "パスワードは8文字以上で入力してください"
     *   }
     * }
     *
     * @param ex バリデーション失敗時の例外オブジェクト（どのフィールドがどう違反したか含む）
     * @return 400 Bad Request + エラー内容の JSON
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        /*
         * FieldError のリストから「フィールド名 → エラーメッセージ」のマップを作る。
         * 同じフィールドに複数エラーがある場合は最初のメッセージを使う（(a, b) -> a の部分）。
         */
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (a, b) -> a  // 同じフィールドに複数エラーがある場合は先勝ち
                ));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("errors", errors));
    }
}
