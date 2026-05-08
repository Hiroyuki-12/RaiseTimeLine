package com.raisetimeline.post.dto;

import java.time.LocalDateTime;

/**
 * ポスト取得レスポンスの DTO。 タイムライン一覧・作成・更新のレスポンスで使用する。 投稿者情報（displayName, username, avatarUrl）は posts JOIN
 * users で取得する。
 *
 * @param id 投稿の ID
 * @param content 投稿本文
 * @param authorId 投稿者のユーザー ID
 * @param authorUsername 投稿者の @handle
 * @param authorDisplayName 投稿者の表示名
 * @param authorAvatarUrl 投稿者のプロフィール画像 URL（未設定時は null）
 * @param imageUrl 投稿に添付された画像の URL（画像なし投稿は null）
 * @param createdAt 投稿日時
 * @param updatedAt 最終更新日時
 * @param likeCount いいね数（likes テーブルの COUNT を SQL で集計して取得）
 * @param commentCount コメント数（comments テーブルの COUNT を SQL で集計して取得）
 * @param liked 現在ログイン中のユーザーがいいね済みかどうか（EXISTS サブクエリで判定）
 */
public record PostResponse(
        Long id,
        String content,
        Long authorId,
        String authorUsername,
        String authorDisplayName,
        String authorAvatarUrl,
        String imageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long likeCount,
        long commentCount,
        boolean liked) {}
