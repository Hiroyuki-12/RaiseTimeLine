package com.raisetimeline.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** プロフィール編集リクエスト DTO。バリデーションは機能仕様書 F-06 に準拠する。 */
public record UpdateProfileRequest(
        /** 変更後のユーザー名。英数字・アンダースコア・ハイフンのみ許可。 URL の一部になるため日本語は不可。 */
        @Size(min = 1, max = 50, message = "ユーザー名は1〜50文字で入力してください")
                @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "ユーザー名は英数字・アンダースコア・ハイフンのみ使用できます")
                String username,

        /** 変更後の表示名。日本語可。 */
        @Size(min = 1, max = 50, message = "表示名は1〜50文字で入力してください") String displayName,

        /** 変更後の自己紹介文。未設定（空文字）も許可する。 */
        @Size(max = 160, message = "自己紹介は160文字以内で入力してください") String bio) {}
