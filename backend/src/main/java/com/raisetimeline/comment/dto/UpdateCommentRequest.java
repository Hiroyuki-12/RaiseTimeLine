package com.raisetimeline.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * コメント更新リクエストの DTO。
 *
 * @param content 更新後のコメント本文（1〜140文字必須）
 */
public record UpdateCommentRequest(
        @NotBlank(message = "コメント本文は必須です")
                @Size(min = 1, max = 140, message = "コメントは1〜140文字で入力してください")
                String content) {}
