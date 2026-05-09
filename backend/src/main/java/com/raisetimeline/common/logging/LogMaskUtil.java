package com.raisetimeline.common.logging;

/**
 * ログ出力時に機密情報をマスクするためのユーティリティ。
 *
 * <p>個人情報（メールアドレス等）を平文のままログに残すと、ログが漏洩した際にそのまま悪用される可能性があるため、 ログに出す前にこのクラスのメソッドを通してマスクする。
 *
 * <p>パスワード・JWT トークン本体はそもそもログに出力しない方針のため、このクラスでは扱わない。
 */
public final class LogMaskUtil {

    /** ユーティリティクラスのため、外部からインスタンス化できないようにする。 */
    private LogMaskUtil() {}

    /**
     * メールアドレスをマスクして返す。
     *
     * <p>ローカル部の先頭2文字（2文字未満なら先頭1文字）だけを残し、それ以降を "***" で置換する。 ドメイン部はそのまま残す。
     *
     * <p>例: "otsuka.hiroyuki@gmail.com" → "ot***@gmail.com"
     *
     * <p>ローカル部だけでユーザーをほぼ特定できる「otsuka.hiroyuki」のような形でログに残ると、 流出時に個人を即座に紐づけられてしまうため、先頭数文字のみに削る。
     *
     * @param email マスク対象のメールアドレス（null / 空文字 / @ なし も許容）
     * @return マスク済みの文字列。null / 空文字なら空文字、@ が無ければ "***" を返す
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        // @ が先頭にある or 含まれない場合は安全側に倒して全マスク
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        // ローカル部が 1 文字なら先頭 1 文字、それ以上なら先頭 2 文字を残す
        String visible = local.length() <= 1 ? local : local.substring(0, 2);
        return visible + "***" + domain;
    }
}
