package com.ansim.guardian.engine

import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RuleBasedRiskEngineTest {

    private lateinit var engine: RuleBasedRiskEngine

    @Before
    fun setUp() {
        engine = RuleBasedRiskEngine()
    }

    // ── 안전 케이스 ──────────────────────────────────────────

    @Test
    fun `일반 안전 텍스트는 SAFE를 반환한다`() = runTest {
        val result = engine.analyze(input("오늘 날씨가 맑고 좋네요. 점심 뭐 먹을까요?"))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.SAFE)
        assertThat(result.totalScore).isEqualTo(0)
    }

    @Test
    fun `빈 문자열은 SAFE를 반환한다`() = runTest {
        val result = engine.analyze(input(""))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.SAFE)
        assertThat(result.detectedSignals).isEmpty()
    }

    @Test
    fun `공백만 있는 텍스트는 SAFE다`() = runTest {
        val result = engine.analyze(input("   \n\t  "))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.SAFE)
    }

    // ── 보이스피싱 ──────────────────────────────────────────

    @Test
    fun `검찰 사칭 텍스트는 CAUTION 이상이다`() = runTest {
        val result = engine.analyze(input("검사입니다. 고객님 계좌가 수사 중입니다."))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
        assertThat(result.primaryCategory).isEqualTo(SignalCategory.VOICE_PHISHING)
    }

    @Test
    fun `안전계좌 언급은 즉시 CRITICAL이다`() = runTest {
        val result = engine.analyze(input("안전계좌로 돈을 옮겨주세요."))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.isCriticalOverride).isTrue()
    }

    @Test
    fun `인증번호 요구는 CAUTION 이상이다`() = runTest {
        val result = engine.analyze(input("인증번호를 불러주시겠어요?"))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
    }

    @Test
    fun `금감원 사칭은 CAUTION 이상이다`() = runTest {
        val result = engine.analyze(input("금융감독원입니다. 계좌 이상 거래가 감지되었습니다."))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
    }

    // ── 가족 사칭 ──────────────────────────────────────────

    @Test
    fun `가족 사칭 + 송금 요구는 DANGER 이상이다`() = runTest {
        val result = engine.analyze(input("엄마 나야. 폰 고장났어. 돈 좀 보내줘."))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)
        assertThat(result.primaryCategory).isEqualTo(SignalCategory.FAMILY_IMPERSONATION)
    }

    @Test
    fun `새 번호 + 비밀 + 송금 조합은 CRITICAL이다`() = runTest {
        val result = engine.analyze(input("엄마 나야 새번호야. 50만원만 보내줘. 아빠한테는 말하지 마."))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
    }

    @Test
    fun `상품권 구매 요구는 DANGER 이상이다`() = runTest {
        val result = engine.analyze(input("아빠 나야. 구글 기프트카드 30만원어치만 사서 번호 찍어 보내줘."))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)
    }

    // ── 투자 사기 ──────────────────────────────────────────

    @Test
    fun `원금보장 + 고수익 조합은 CRITICAL이다`() = runTest {
        val result = engine.analyze(input("원금보장에 수익 보장해드립니다. 지금 바로 매수하세요."))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
    }

    @Test
    fun `손실보전 약속은 CAUTION 이상이다`() = runTest {
        // 손실보전(35점) 단독 → CAUTION(30~59점) 구간
        val result = engine.analyze(input("손실보전 해드립니다. 이번 종목 놓치지 마세요."))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
    }

    @Test
    fun `손실보전 + 원금보장 조합은 DANGER 이상이다`() = runTest {
        // 손실보전(35) + 원금보장(40) = 75점 → DANGER
        val result = engine.analyze(input("원금보장에 손실보전까지 해드립니다."))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)
    }

    @Test
    fun `세력 + 내부정보 언급은 CAUTION 이상이다`() = runTest {
        val result = engine.analyze(input("세력 매집 완료됐습니다. 내부정보입니다. VIP방만 공개합니다."))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
        assertThat(result.primaryCategory).isEqualTo(SignalCategory.INVESTMENT_FRAUD)
    }

    @Test
    fun `비상장주식 상장확정은 DANGER 이상이다`() = runTest {
        val result = engine.analyze(input("상장 확정입니다. 일반인은 못 사는 주식이에요. 기관 물량 드립니다."))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)
        assertThat(result.primaryCategory).isEqualTo(SignalCategory.UNLISTED_STOCK)
    }

    // ── 코인 사기 ──────────────────────────────────────────

    @Test
    fun `출금전 추가입금 요구는 즉시 CRITICAL이다`() = runTest {
        val result = engine.analyze(input("출금하려면 세금 먼저 내야 합니다."))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.isCriticalOverride).isTrue()
    }

    @Test
    fun `보증금 요구는 즉시 CRITICAL이다`() = runTest {
        val result = engine.analyze(input("계정 복구를 위해 보증금 입금이 필요합니다."))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
    }

    // ── 원격제어 사기 ──────────────────────────────────────

    @Test
    fun `AnyDesk 설치 요청은 즉시 CRITICAL이다`() = runTest {
        val result = engine.analyze(input("AnyDesk를 설치해서 코드를 알려주세요."))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.primaryCategory).isEqualTo(SignalCategory.REMOTE_CONTROL)
    }

    @Test
    fun `TeamViewer 설치 요청은 CRITICAL이다`() = runTest {
        val result = engine.analyze(input("TeamViewer 설치하고 화면 공유해주세요."))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
    }

    @Test
    fun `원격제어 단어만 있으면 DANGER 이상이다`() = runTest {
        val result = engine.analyze(input("원격제어 앱 설치해 주세요."))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)
    }

    // ── 스미싱 ─────────────────────────────────────────────

    @Test
    fun `단축URL이 포함된 택배 문자는 CAUTION 이상이다`() = runTest {
        val result = engine.analyze(input("[택배] 배송 조회 바랍니다: bit.ly/xxxxx"))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
    }

    // ── 복합 신호 ──────────────────────────────────────────

    @Test
    fun `여러 위험 신호가 있으면 점수가 누적된다`() = runTest {
        val result = engine.analyze(input(
            "검찰입니다. 수사 중입니다. 안전계좌로 인증번호 알려주시고 원격제어 앱 설치해주세요."
        ))
        assertThat(result.totalScore).isGreaterThan(100)
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        // 카테고리 dedup: VOICE_PHISHING + REMOTE_CONTROL → 최소 2개
        assertThat(result.detectedSignals.size).isAtLeast(2)
    }

    @Test
    fun `감지된 신호는 카테고리별로 중복 제거된다`() = runTest {
        val result = engine.analyze(input("검찰 검사 금감원 금융감독원"))
        // 같은 카테고리(VOICE_PHISHING) 신호가 중복 없이 반환되어야 함
        val categories = result.detectedSignals.map { it.category }
        assertThat(categories.size).isEqualTo(categories.toSet().size)
    }

    // ── 대소문자 / 인코딩 ──────────────────────────────────

    @Test
    fun `영문 대소문자 무관하게 원격제어앱이 탐지된다`() = runTest {
        val lower = engine.analyze(input("anydesk 설치해주세요"))
        val upper = engine.analyze(input("ANYDESK 설치해주세요"))
        val mixed = engine.analyze(input("AnyDesk 설치해주세요"))
        assertThat(lower.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(upper.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(mixed.riskLevel).isEqualTo(RiskLevel.CRITICAL)
    }

    // ── 입력 소스별 ────────────────────────────────────────

    @Test
    fun `SMS 소스도 동일하게 분석된다`() = runTest {
        val result = engine.analyze(RiskInput(
            text = "엄마 나야 새번호야 50만원 보내줘",
            source = InputSource.SMS
        ))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)
        assertThat(result.input.source).isEqualTo(InputSource.SMS)
    }

    @Test
    fun `카카오톡 알림 소스도 분석된다`() = runTest {
        val result = engine.analyze(RiskInput(
            text = "세력 매집 완료. 지금 바로 매수하세요.",
            source = InputSource.NOTIFICATION_KAKAO,
            senderInfo = "주식리딩방"
        ))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
    }

    // ── 헬퍼 ──────────────────────────────────────────────

    private fun input(text: String) = RiskInput(text = text)
}
