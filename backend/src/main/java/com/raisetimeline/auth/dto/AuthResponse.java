package com.raisetimeline.auth.dto;

/**
 * 認証成功時のレスポンスデータを表す DTO。
 * ユーザー登録（/api/auth/register）とログイン（/api/auth/login）の両方で使用する。
 *
 * クライアント（フロントエンド）はこのレスポンスを受け取り、
 * token をローカルストレージに保存して以降のリクエストに使用する。
 *
 * @param token    JWT トークン文字列。フロントエンドが Authorization ヘッダーに付けて送る
 * @param userId   ログインしたユーザーの ID（DB の users.id）
 * @param username ログインしたユーザー名（画面表示用）
 */
public record AuthResponse(
        String token,
        Long userId,
        String username
) {}
