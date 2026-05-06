package com.raisetimeline.like.dto;

import jakarta.validation.constraints.NotNull;

/**
 * いいね追加リクエストの DTO。
 *
 * @param postId いいね対象の投稿 ID（必須）
 */
public record AddLikeRequest(@NotNull(message = "投稿 ID は必須です") Long postId) {}
