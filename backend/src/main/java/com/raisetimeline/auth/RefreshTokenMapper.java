package com.raisetimeline.auth;

import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

/**
 * refresh_tokens テーブルに対する MyBatis Mapper インターフェース。
 * SQL は RefreshTokenMapper.xml で定義している。
 */
@Mapper
public interface RefreshTokenMapper {

    /**
     * リフレッシュトークンを1件挿入する。
     * ログイン・新規登録のたびに呼ばれる。
     */
    void insert(RefreshToken refreshToken);

    /**
     * トークン文字列でレコードを検索する。
     * /api/auth/refresh でリフレッシュ要求が来たときに呼ぶ。
     * 結果が存在しない（無効・削除済）場合は空の Optional を返す。
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * トークン文字列でレコードを削除する。
     * /api/auth/logout でログアウト要求が来たときに呼ぶ。
     */
    void deleteByToken(String token);

    /**
     * ユーザー ID に紐づくすべてのリフレッシュトークンを削除する。
     * 全デバイスからログアウトしたい場合に使う。
     */
    void deleteByUserId(Long userId);
}
