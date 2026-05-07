package com.raisetimeline.user.dto;

/** プロフィールページに返すレスポンス DTO。 フォロー数・フォロワー数・フォロー状態・自分のプロフィールかどうかを含む。 */
public record UserProfileResponse(
        /** ユーザーの主キー */
        Long id,

        /**
         * @handle 形式のユーザー名（URL に使われる）
         */
        String username,

        /** 画面表示用の名前（日本語可） */
        String displayName,

        /** プロフィール画像 URL（未設定時は null） */
        String avatarUrl,

        /** 自己紹介文（最大160文字、未設定時は null） */
        String bio,

        /** このユーザーがフォローしているユーザー数 */
        long followingCount,

        /** このユーザーをフォローしているユーザー数 */
        long followerCount,

        /** 現在ログイン中のユーザーがこのユーザーをフォローしているか。 自分のプロフィールページでは常に false となる。 */
        boolean isFollowing,

        /** このプロフィールが現在ログイン中のユーザー自身のものか。フォローボタン/編集ボタンの表示切り替えに使う。 */
        boolean isOwnProfile) {}
