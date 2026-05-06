package com.raisetimeline.auth.dto;

/**
 * 認証成功時のレスポンスデータを表す DTO。 ユーザー登録（/api/auth/register）とログイン（/api/auth/login）の両方で使用する。
 *
 * <p>リフレッシュトークンは HttpOnly Cookie でセットするため、このレスポンスボディには含めない。 HttpOnly Cookie は JavaScript
 * からアクセスできないため、XSS 攻撃でリフレッシュトークンが盗まれるリスクを防げる。
 *
 * @param accessToken JWT アクセストークン文字列（有効期限: 15分）。フロントエンドは Authorization ヘッダーに付けて API を呼ぶ
 * @param userId ログインしたユーザーの ID（DB の users.id）
 * @param username @handle として使う識別子（英数字・_・- のみ）
 * @param displayName 画面に表示する名前（日本語可）
 */
public record AuthResponse(String accessToken, Long userId, String username, String displayName) {}
