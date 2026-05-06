package com.raisetimeline.like;

import java.time.LocalDateTime;

/**
 * likes テーブルの1行に対応するエンティティクラス。 MyBatis が SQL の結果をこのクラスにマッピングする。 map-underscore-to-camel-case=true
 * の設定により、DB のスネークケース（post_id, user_id, created_at）が 自動的にキャメルケース（postId, userId, createdAt）に変換される。
 */
public class Like {

    /** いいねの主キー（DB が AUTO_INCREMENT で自動採番する） */
    private Long id;

    /** いいね対象の投稿 ID（posts テーブルの外部キー） */
    private Long postId;

    /** いいねしたユーザーの ID（users テーブルの外部キー） */
    private Long userId;

    /** いいねの作成日時（DB の DEFAULT CURRENT_TIMESTAMP で自動セットされる） */
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
