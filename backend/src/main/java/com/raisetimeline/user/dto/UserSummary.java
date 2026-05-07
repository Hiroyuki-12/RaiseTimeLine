package com.raisetimeline.user.dto;

/** フォロー中・フォロワー一覧の各ユーザー行に使うシンプルな DTO。 プロフィール詳細は含まず、一覧表示に必要な最小限の情報だけを持つ。 */
public record UserSummary(
        /** ユーザーの主キー（フォロー/アンフォロー API に使う） */
        Long id,

        /**
         * @handle 形式のユーザー名（URL に使う）
         */
        String username,

        /** 画面表示用の名前（日本語可） */
        String displayName,

        /** プロフィール画像 URL（未設定時は null） */
        String avatarUrl,

        /** 現在ログイン中のユーザーがこのユーザーをフォローしているか */
        boolean isFollowing) {}
