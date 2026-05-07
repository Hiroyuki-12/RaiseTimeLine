package com.raisetimeline.follow;

import java.time.LocalDateTime;

/**
 * follows テーブルの1行に対応するエンティティクラス。 フォロー関係を表す。follower_id がフォローする側、followee_id がフォローされる側。
 * map-underscore-to-camel-case=true の設定により、DB のスネークケースが自動的にキャメルケースに変換される。
 */
public class Follow {

    /** フォロー関係の主キー（DB が自動採番） */
    private Long id;

    /** フォローする側のユーザー ID（能動側） */
    private Long followerId;

    /** フォローされる側のユーザー ID（受動側） */
    private Long followeeId;

    /** フォローした日時（DB の DEFAULT CURRENT_TIMESTAMP で自動セットされる） */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFollowerId() {
        return followerId;
    }

    public void setFollowerId(Long followerId) {
        this.followerId = followerId;
    }

    public Long getFolloweeId() {
        return followeeId;
    }

    public void setFolloweeId(Long followeeId) {
        this.followeeId = followeeId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
