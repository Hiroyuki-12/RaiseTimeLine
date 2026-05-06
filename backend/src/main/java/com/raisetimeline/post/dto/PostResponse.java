package com.raisetimeline.post.dto;

import java.time.LocalDateTime;

/**
 * ポスト取得レスポンスの DTO。 タイムライン一覧・作成・更新のレスポンスで使用する。 投稿者情報（displayName, username）は posts JOIN users で取得する。
 *
 * @param id 投稿の ID
 * @param content 投稿本文
 * @param authorId 投稿者のユーザー ID
 * @param authorUsername 投稿者の @handle
 * @param authorDisplayName 投稿者の表示名
 * @param createdAt 投稿日時
 * @param updatedAt 最終更新日時
 */
public record PostResponse(
        Long id,
        String content,
        Long authorId,
        String authorUsername,
        String authorDisplayName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
