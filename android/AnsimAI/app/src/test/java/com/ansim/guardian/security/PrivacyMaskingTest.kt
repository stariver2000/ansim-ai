package com.ansim.guardian.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 개인정보 마스킹 테스트
 *
 * 이 앱은 사용자의 민감 정보를 다루므로,
 * 분석 로그·서버 전송 시 개인정보가 노출되지 않아야 한다.
 */
class PrivacyMaskingTest {

    // ── 전화번호 마스킹 ─────────────────────────────────────

    @Test
    fun `전화번호가 마스킹된다`() {
        val masked = PrivacyMasker.mask("010-1234-5678로 전화하세요")
        assertThat(masked).doesNotContain("010-1234-5678")
        assertThat(masked).contains("[전화번호]")
    }

    @Test
    fun `하이픈 없는 전화번호도 마스킹된다`() {
        val masked = PrivacyMasker.mask("01012345678 이 번호로 연락주세요")
        assertThat(masked).doesNotContain("01012345678")
    }

    @Test
    fun `국제 전화번호도 마스킹된다`() {
        val masked = PrivacyMasker.mask("+82 10-1234-5678에 연락")
        assertThat(masked).doesNotContain("10-1234-5678")
    }

    @Test
    fun `070 인터넷 전화도 마스킹된다`() {
        val masked = PrivacyMasker.mask("070-1234-5678 발신자")
        assertThat(masked).doesNotContain("070-1234-5678")
    }

    // ── 계좌번호 마스킹 ─────────────────────────────────────

    @Test
    fun `계좌번호 형식이 마스킹된다`() {
        val masked = PrivacyMasker.mask("계좌 123-456-789012로 입금하세요")
        assertThat(masked).doesNotContain("123-456-789012")
    }

    // ── 이메일 마스킹 ───────────────────────────────────────

    @Test
    fun `이메일 주소가 마스킹된다`() {
        val masked = PrivacyMasker.mask("user@example.com으로 연락주세요")
        assertThat(masked).doesNotContain("user@example.com")
        assertThat(masked).contains("[이메일]")
    }

    // ── 주민등록번호 마스킹 ──────────────────────────────────

    @Test
    fun `주민등록번호가 마스킹된다`() {
        // 공백으로 명확히 분리된 형태
        val masked = PrivacyMasker.mask("주민번호: 900101-1234567 입력")
        assertThat(masked).doesNotContain("900101-1234567")
        assertThat(masked).contains("[주민등록번호]")
    }

    // ── 마스킹 후 분석 가능성 유지 ──────────────────────────

    @Test
    fun `마스킹 후에도 위험 키워드는 유지된다`() {
        val text = "010-1234-5678로 전화했더니 안전계좌로 이체하라고 했어요"
        val masked = PrivacyMasker.mask(text)
        assertThat(masked).contains("안전계좌")
        assertThat(masked).doesNotContain("010-1234-5678")
    }

    // ── 민감정보 감지 ───────────────────────────────────────

    @Test
    fun `민감정보 포함 여부를 감지한다`() {
        assertThat(PrivacyMasker.containsSensitiveInfo("010-1234-5678 전화")).isTrue()
        assertThat(PrivacyMasker.containsSensitiveInfo("오늘 날씨 좋네요")).isFalse()
    }

    @Test
    fun `민감정보 없는 텍스트는 마스킹 후 원문과 동일하다`() {
        val text = "오늘 안전계좌로 이체해야 합니다"
        val masked = PrivacyMasker.mask(text)
        // 위험 키워드는 있지만 개인정보는 없으므로 원문과 거의 동일
        assertThat(masked).contains("안전계좌")
    }

    // ── 복합 마스킹 ─────────────────────────────────────────

    @Test
    fun `여러 민감정보가 함께 있으면 모두 마스킹된다`() {
        val text = "010-1234-5678로 연락하고 user@test.com으로 이메일 보내주세요"
        val masked = PrivacyMasker.mask(text)
        assertThat(masked).doesNotContain("010-1234-5678")
        assertThat(masked).doesNotContain("user@test.com")
    }

    @Test
    fun `빈 문자열도 마스킹 처리된다`() {
        val masked = PrivacyMasker.mask("")
        assertThat(masked).isEmpty()
    }
}
