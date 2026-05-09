package com.raisetimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * LogMaskUtil の単体テスト。
 *
 * <pre>
 * 対象: LogMaskUtil.maskEmail
 * 技法: 同値分割 + 境界値
 *
 * 同値クラス:
 *   - 通常メール（ローカル部 2 文字以上 + @ + ドメイン）
 *   - ローカル部 1 文字（境界値: 先頭 1 文字のみ残す）
 *   - 不正な形式（@ なし / @ 先頭 / null / 空文字）
 * </pre>
 */
class LogMaskUtilTest {

    @Test
    @DisplayName("通常メール: ローカル部の先頭 2 文字 + *** + @ドメイン になる")
    void maskEmail_normalEmail_keepsFirstTwoChars() {
        assertThat(LogMaskUtil.maskEmail("otsuka.hiroyuki@gmail.com")).isEqualTo("ot***@gmail.com");
    }

    @Test
    @DisplayName("ローカル部が 2 文字ちょうど: 先頭 2 文字をそのまま残す（境界値）")
    void maskEmail_twoCharLocal_keepsBothChars() {
        assertThat(LogMaskUtil.maskEmail("ab@example.com")).isEqualTo("ab***@example.com");
    }

    @Test
    @DisplayName("ローカル部が 1 文字: 先頭 1 文字のみ残す（境界値）")
    void maskEmail_singleCharLocal_keepsFirstChar() {
        assertThat(LogMaskUtil.maskEmail("a@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    @DisplayName("@ が含まれない場合は全マスク")
    void maskEmail_noAtSign_returnsAllMasked() {
        assertThat(LogMaskUtil.maskEmail("invalid-email")).isEqualTo("***");
    }

    @Test
    @DisplayName("@ が先頭にある場合は全マスク（ローカル部が空）")
    void maskEmail_atSignAtStart_returnsAllMasked() {
        assertThat(LogMaskUtil.maskEmail("@example.com")).isEqualTo("***");
    }

    @Test
    @DisplayName("null は空文字を返す")
    void maskEmail_null_returnsEmpty() {
        assertThat(LogMaskUtil.maskEmail(null)).isEmpty();
    }

    @Test
    @DisplayName("空文字 / 空白のみは空文字を返す")
    void maskEmail_blank_returnsEmpty() {
        assertThat(LogMaskUtil.maskEmail("")).isEmpty();
        assertThat(LogMaskUtil.maskEmail("   ")).isEmpty();
    }
}
