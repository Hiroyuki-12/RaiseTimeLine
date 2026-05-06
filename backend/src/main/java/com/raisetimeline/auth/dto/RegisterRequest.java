package com.raisetimeline.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ユーザー登録リクエストの入力データを表す DTO（Data Transfer Object）。
 *
 * <p>Java の Record クラスを使用している。Record は不変（immutable）なデータ入れ物で、
 * フィールド宣言と同時にコンストラクタ・getter・equals・hashCode・toString が自動生成される。
 *
 * <p>jakarta.validation アノテーションによりバリデーション（入力値の検証）を宣言的に定義する。 コントローラーで @Valid を付けると自動でチェックが実行され、
 * 違反があれば GlobalExceptionHandler が 400 Bad Request を返す。
 */
public record RegisterRequest(

        /** ユーザー名: 1〜50文字、英数字・アンダースコア・ハイフンのみ使用可。 画面に表示される名前で、重複不可。 */
        @NotBlank(message = "ユーザー名は必須です")
                @Size(min = 1, max = 50, message = "ユーザー名は1〜50文字で入力してください")
                @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "ユーザー名は英数字・_・-のみ使用できます")
                String username,

        /** メールアドレス: 有効なメール形式であること。重複不可。 */
        @NotBlank(message = "メールアドレスは必須です") @Email(message = "有効なメールアドレスを入力してください") String email,

        /**
         * パスワード: 8文字以上、英字と数字を両方含むこと。 正規表現の意味: (?=.*[a-zA-Z]) : 英字を1文字以上含む（先読みアサーション） (?=.*\d) :
         * 数字を1文字以上含む（先読みアサーション） .+ : 任意の文字が1文字以上続く
         */
        @NotBlank(message = "パスワードは必須です")
                @Size(min = 8, message = "パスワードは8文字以上で入力してください")
                @Pattern(
                        regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$",
                        message = "パスワードは英字と数字を両方含む必要があります")
                String password,

        /**
         * パスワード確認用: フロントで入力したパスワードと一致することを AuthService で確認する。 バリデーションアノテーションでは一致チェックができないため、 サービス層で
         * password と比較する。
         */
        @NotBlank(message = "パスワード（確認）は必須です") String passwordConfirm) {}
