package com.raisetimeline.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * ログインリクエストの入力データを表す DTO。
 *
 * ログインに必要なのはメールアドレスとパスワードの2項目のみ。
 */
public record LoginRequest(

        /** ログインに使うメールアドレス */
        @NotBlank(message = "メールアドレスは必須です")
        @Email(message = "有効なメールアドレスを入力してください")
        String email,

        /** ログインに使うパスワード（平文。サービス層で BCrypt の照合を行う） */
        @NotBlank(message = "パスワードは必須です")
        String password
) {}
