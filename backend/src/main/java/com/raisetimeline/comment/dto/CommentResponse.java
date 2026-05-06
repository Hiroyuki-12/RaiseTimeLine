package com.raisetimeline.comment.dto;

import java.time.LocalDateTime;

/**
 * コメント取得レスポンスの DTO。 コメント一覧取得・作成・更新のレスポンスで使用する。 投稿者情報（displayName, username）は comments JOIN users
 * で取得し、このクラスに詰める。
 *
 * @param id コメントの ID
 * @param postId コメント対象の投稿 ID
 * @param authorId コメント投稿者のユーザー ID
 * @param authorUsername コメント投稿者の @handle
 * @param authorDisplayName コメント投稿者の表示名
 * @param content コメント本文
 * @param createdAt コメントの作成日時
 */
public record CommentResponse(
        Long id,
        Long postId,
        Long authorId,
        String authorUsername,
        String authorDisplayName,
        String content,
        LocalDateTime createdAt) {}
