package com.raisetimeline.comment;

import java.time.LocalDateTime;

/**
 * comments テーブルの1行に対応するエンティティクラス。 MyBatis が SQL の結果をこのクラスにマッピングする。
 * map-underscore-to-camel-case=true の設定により、DBのスネークケース（post_id, user_id, created_at）が
 * 自動的にキャメルケース（postId, userId, createdAt）に変換される。
 */
public class Comment {

    /** コメントの主キー（DB が AUTO_INCREMENT で自動採番する） */
    private Long id;

    /** コメント対象の投稿 ID（posts テーブルの外部キー） */
    private Long postId;

    /** コメント投稿者の ID（users テーブルの外部キー） */
    private Long userId;

    /** コメント本文（最大140文字） */
    private String content;

    /** コメントの作成日時（DB の DEFAULT CURRENT_TIMESTAMP で自動セットされる） */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
