package com.ansim.guardian.context

import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * 대화 맥락 테스트
 *
 * 한 문장만으로는 낮은 위험이지만, 여러 문장이 쌓이면 높아지는 경우.
 * 현재 구현: 여러 메시지를 합쳐서 분석하는 방식으로 테스트.
 * (향후: 발신자별 이력 기반 누적 점수 구현 시 이 테스트 확장)
 */
class ConversationContextTest {

    private lateinit var engine: RuleBasedRiskEngine

    @Before
    fun setUp() { engine = RuleBasedRiskEngine() }

    // ── 단계별 위험 상승 ───────────────────────────────────

    @Test
    fun `송금 요구가 추가되면 위험도가 올라간다`() = runTest {
        val base = engine.analyze(RiskInput("잠깐 도와줄 수 있어?"))
        // "돈 보내줘"는 규칙에 있는 명확한 키워드 사용
        val withMoney = engine.analyze(RiskInput("잠깐 도와줄 수 있어? 일단 돈 보내줘"))

        assertThat(withMoney.totalScore).isGreaterThan(base.totalScore)
    }

    @Test
    fun `긴급성이 추가되면 점수가 올라간다`() = runTest {
        val without = engine.analyze(RiskInput("돈 보내줘"))
        val withUrgency = engine.analyze(RiskInput("급해 돈 보내줘 지금 바로"))

        assertThat(withUrgency.totalScore).isGreaterThan(without.totalScore)
    }

    @Test
    fun `비밀 요구가 추가되면 점수가 크게 오른다`() = runTest {
        val without = engine.analyze(RiskInput("돈 좀 보내줘"))
        val withSecret = engine.analyze(RiskInput("돈 좀 보내줘 아무한테도 말하지마"))

        assertThat(withSecret.totalScore).isGreaterThan(without.totalScore)
        assertThat(withSecret.riskLevel.ordinal).isAtLeast(without.riskLevel.ordinal)
    }

    // ── 복합 메시지 누적 분석 ──────────────────────────────

    @Test
    fun `여러 메시지를 합쳐 분석하면 단일 메시지보다 위험도가 높다`() = runTest {
        val messages = listOf(
            "안녕하세요. 좋은 투자 정보 드립니다.",
            "관심 있으세요?",
            "원금은 보장됩니다.",
            "오늘 안에 입금하시면 VIP방 초대해드립니다."
        )

        val single = engine.analyze(RiskInput(messages.last()))
        val combined = engine.analyze(RiskInput(messages.joinToString(" ")))

        // 합친 분석이 마지막 메시지 단독보다 점수가 높거나 같아야 함
        assertThat(combined.totalScore).isAtLeast(single.totalScore)
    }

    @Test
    fun `투자 권유 대화 흐름은 DANGER 이상이다`() = runTest {
        val fullConversation = """
            안녕하세요. 저희 VIP 투자 그룹에 초대합니다.
            세력 매집이 완료된 종목입니다.
            원금 보장되고 손실나면 저희가 보전해드립니다.
            오늘 마감이니 지금 바로 입금하세요.
        """.trimIndent()

        val result = engine.analyze(RiskInput(fullConversation))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)
    }

    @Test
    fun `보이스피싱 전체 스크립트는 CRITICAL이다`() = runTest {
        val script = """
            서울중앙지검 수사관입니다.
            고객님 계좌가 범죄에 연루되어 수사 중입니다.
            자산 보호를 위해 안전계좌로 이체하셔야 합니다.
            가족에게는 말하지 마세요.
        """.trimIndent()

        val result = engine.analyze(RiskInput(script))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
    }

    // ── 경계값: 점수 단계 전환 ─────────────────────────────

    @Test
    fun `단일 위험 신호는 CAUTION이고 복합은 DANGER 이상이다`() = runTest {
        val single = engine.analyze(RiskInput("원금보장"))
        val combined = engine.analyze(RiskInput("원금보장 수익보장 지금 바로 입금"))

        assertThat(single.riskLevel.ordinal).isAtMost(RiskLevel.CAUTION.ordinal)
        assertThat(combined.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)
    }

    // ── 분석 일관성 ────────────────────────────────────────

    @Test
    fun `동일한 텍스트는 항상 동일한 결과를 반환한다`() = runTest {
        val text = "원금보장 VIP방 세력 매집 지금 입금"
        val result1 = engine.analyze(RiskInput(text))
        val result2 = engine.analyze(RiskInput(text))
        val result3 = engine.analyze(RiskInput(text))

        assertThat(result1.totalScore).isEqualTo(result2.totalScore)
        assertThat(result2.totalScore).isEqualTo(result3.totalScore)
        assertThat(result1.riskLevel).isEqualTo(result3.riskLevel)
    }
}
