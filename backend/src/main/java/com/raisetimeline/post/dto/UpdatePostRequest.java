package com.raisetimeline.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ポスト編集リクエストの入力データを表す DTO。
 *
 * @param content 更新後の投稿本文（1〜280 文字）
 */
public record UpdatePostRequest(
        /** 更新後の投稿本文: 1〜280 文字。空白のみは不可 */
        @NotBlank(message = "投稿内容は必須です")
                @Size(min = 1, max = 280, message = "投稿内容は1〜280文字で入力してください")
                String content) {}
