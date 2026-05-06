package com.raisetimeline.post;

import java.time.LocalDateTime;

/**
 * posts テーブルの1行に対応するドメインオブジェクト（POJO）。 MyBatis の map-underscore-to-camel-case=true により user_id →
 * userId、created_at → createdAt が自動マッピングされる。
 */
public class Post {

    /** posts.id: 主キー。DB が自動採番する */
    private Long id;

    /** posts.user_id: 投稿者の users.id */
    private Long userId;

    /** posts.content: 投稿本文（最大 280 文字） */
    private String content;

    /** posts.created_at: 投稿日時 */
    private LocalDateTime createdAt;

    /** posts.updated_at: 最終更新日時 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
