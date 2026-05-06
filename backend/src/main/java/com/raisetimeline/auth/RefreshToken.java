package com.raisetimeline.auth;

import java.time.LocalDateTime;

/** refresh_tokens テーブルの1行に対応するドメインオブジェクト。 アクセストークン再発行のリクエスト時に DB と照合するために使う。 */
public class RefreshToken {

    /** DB 自動採番の主キー */
    private Long id;

    /** このトークンを所有するユーザーの ID */
    private Long userId;

    /** UUID v4 で生成した不透明トークン文字列。JWT ではなく DB で検証する */
    private String token;

    /** トークンの有効期限。この日時を過ぎたトークンは拒否する */
    private LocalDateTime expiresAt;

    /** レコード作成日時 */
    private LocalDateTime createdAt;

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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
