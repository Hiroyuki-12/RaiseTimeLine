package com.raisetimeline.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ポスト作成リクエストの入力データを表す DTO。 @Valid アノテーションによりコントローラーで自動バリデーションが実行される。
 *
 * @param content 投稿本文（1〜280 文字）
 */
public record CreatePostRequest(
        /** 投稿本文: 1〜280 文字。空白のみは不可 */
        @NotBlank(message = "投稿内容は必須です")
                @Size(min = 1, max = 280, message = "投稿内容は1〜280文字で入力してください")
                String content) {}
