package com.raisetimeline.user;

import java.time.LocalDateTime;

/**
 * users テーブルの1行に対応するドメインオブジェクト（POJO）。
 *
 * MyBatis を使っているため JPA のアノテーションは不要。
 * mybatis.configuration.map-underscore-to-camel-case=true の設定により、
 * DB のスネークケース列名（例: password_hash）が Java のキャメルケース（passwordHash）に自動マッピングされる。
 */
public class User {

    /** users.id: 主キー。DB が自動採番する */
    private Long id;

    /** users.email: ログインに使うメールアドレス */
    private String email;

    /**
     * users.password_hash: BCrypt でハッシュ化されたパスワード。
     * 平文のパスワードは絶対にこのフィールドに入れてはいけない。
     */
    private String passwordHash;

    /** users.username: 画面に表示されるユーザー名 */
    private String username;

    /** users.avatar_url: プロフィール画像の URL。null 可 */
    private String avatarUrl;

    /** users.bio: プロフィール文。null 可 */
    private String bio;

    /** users.created_at: レコード作成日時 */
    private LocalDateTime createdAt;

    /** users.updated_at: レコード更新日時 */
    private LocalDateTime updatedAt;

    // --- Getters / Setters ---
    // MyBatis がオブジェクトを生成する際に setter を通じてフィールドをセットする

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
