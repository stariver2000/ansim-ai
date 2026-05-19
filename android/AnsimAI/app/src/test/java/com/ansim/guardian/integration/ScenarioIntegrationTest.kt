package com.ansim.guardian.integration

import com.ansim.guardian.ai.TemplateExplanationGenerator
import com.ansim.guardian.ai.embedding.CharNgramEmbeddingEngine
import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * 실사용 시나리오 통합 테스트
 *
 * 창업경진대회 데모 시나리오와 동일한 입력으로 전체 파이프라인 검증.
 * 규칙엔진 → 설명 생성까지 End-to-End.
 */
class ScenarioIntegrationTest {

    private lateinit var ruleEngine: RuleBasedRiskEngine
    private lateinit var explanationGenerator: TemplateExplanationGenerator
    private val embeddingEngine = CharNgramEmbeddingEngine()

    @Before
    fun setUp() {
        ruleEngine = RuleBasedRiskEngine()
        explanationGenerator = TemplateExplanationGenerator()
    }

    // ── 시나리오 1: 가족 사칭 ─────────────────────────────

    @Test
    fun `시나리오1 가족사칭 전체 파이프라인`() = runTest {
        val text = "엄마 나야 폰 고장났어 이 번호로 30만원만 보내줘 아빠한테 말하지마"

        val result = ruleEngine.analyze(RiskInput(text))

        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.primaryCategory).isEqualTo(SignalCategory.FAMILY_IMPERSONATION)
        assertThat(result.isDangerous).isTrue()
        assertThat(result.requiresImmediateAlert).isTrue()

        val explanation = explanationGenerator.generateExplanation(result.input, result, emptyList())

        assertThat(explanation.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(explanation.summary).isNotEmpty()
        assertThat(explanation.doNotDo).isNotEmpty()
        assertThat(explanation.doNow).isNotEmpty()
        assertThat(explanation.askGuardian).isNotEmpty()

        // 쉬운 말 확인
        assertThat(explanation.doNow.any { "보호자" in it || "전화" in it || "확인" in it }).isTrue()

        println("[시나리오1] 위험도: ${result.riskLevel.label}")
        println("[시나리오1] 설명: ${explanation.summary}")
        println("[시나리오1] 행동: ${explanation.doNow.firstOrNull()}")
    }

    // ── 시나리오 2: 검찰 사칭 보이스피싱 ───────────────────

    @Test
    fun `시나리오2 검찰사칭 전체 파이프라인`() = runTest {
        val text = "서울중앙지검입니다. 계좌가 범죄에 연루되어 수사 중입니다. 안전계좌로 돈을 옮겨야 합니다."

        val result = ruleEngine.analyze(RiskInput(text))

        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.primaryCategory).isEqualTo(SignalCategory.VOICE_PHISHING)

        val explanation = explanationGenerator.generateExplanation(result.input, result, emptyList())

        assertThat(explanation.doNotDo.any { "돈" in it || "이체" in it }).isTrue()
        assertThat(explanation.doNow).isNotEmpty()

        println("[시나리오2] 감지된 신호: ${result.detectedSignals.map { it.description }}")
        println("[시나리오2] 점수: ${result.totalScore}")
    }

    // ── 시나리오 3: 주식 리딩방 ───────────────────────────

    @Test
    fun `시나리오3 주식리딩방 전체 파이프라인`() = runTest {
        val text = "VIP방에서만 공개합니다. 이 종목 오늘 상한가 갑니다. 원금 보장됩니다."

        val result = ruleEngine.analyze(RiskInput(text))

        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)

        val explanation = explanationGenerator.generateExplanation(result.input, result, emptyList())

        assertThat(explanation.summary).isNotEmpty()
        assertThat(explanation.isFromLlm).isFalse()

        println("[시나리오3] 위험도: ${result.riskLevel.label}")
        println("[시나리오3] 설명: ${explanation.summary}")
    }

    // ── 시나리오 4: 가짜 거래소 ───────────────────────────

    @Test
    fun `시나리오4 가짜거래소 전체 파이프라인`() = runTest {
        val text = "수익금 출금하려면 세금 100만원을 먼저 입금하세요."

        val result = ruleEngine.analyze(RiskInput(text))

        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.isCriticalOverride).isTrue()

        val explanation = explanationGenerator.generateExplanation(result.input, result, emptyList())

        assertThat(explanation.doNotDo).isNotEmpty()

        println("[시나리오4] 위험도: ${result.riskLevel.label}")
        println("[시나리오4] 하지 말 것: ${explanation.doNotDo.firstOrNull()}")
    }

    // ── 시나리오 5: 원격제어 사기 ─────────────────────────

    @Test
    fun `시나리오5 원격제어 전체 파이프라인`() = runTest {
        val text = "AnyDesk를 설치해서 화면 공유해주세요."

        val result = ruleEngine.analyze(RiskInput(text))

        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.primaryCategory).isEqualTo(SignalCategory.REMOTE_CONTROL)

        val explanation = explanationGenerator.generateExplanation(result.input, result, emptyList())

        // 원격제어 관련 경고가 있어야 함
        val allText = (explanation.doNotDo + explanation.doNow).joinToString(" ")
        assertThat(allText).containsMatch("(앱|설치|삭제|원격)")

        println("[시나리오5] 위험도: ${result.riskLevel.label}")
        println("[시나리오5] 지금 할 것: ${explanation.doNow.firstOrNull()}")
    }

    // ── 시나리오 6: 복합 위험 ─────────────────────────────

    @Test
    fun `시나리오6 복합위험 모든_파이프라인이_동작한다`() = runTest {
        val text = "금감원 직원입니다. 계좌 보호 위해 AnyDesk 설치하고 안전계좌로 이체하세요. 가족에게 말하지 마세요."

        val result = ruleEngine.analyze(RiskInput(text))

        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.detectedSignals.size).isAtLeast(2)

        val explanation = explanationGenerator.generateExplanation(result.input, result, emptyList())

        assertThat(explanation.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(explanation.whyDangerous.size).isAtLeast(1)

        println("[시나리오6] 감지 신호 수: ${result.detectedSignals.size}")
        println("[시나리오6] 위험 이유: ${explanation.whyDangerous.take(2)}")
    }

    // ── 시나리오 7: 안전 메시지 (false positive 방지) ────────

    @Test
    fun `시나리오7 안전 메시지는 SAFE로 처리된다`() = runTest {
        val safeMessages = listOf(
            "오늘 저녁 뭐 먹을까요?",
            "생일 축하해!",
            "내일 몇 시에 만날까요?"
        )

        safeMessages.forEach { text ->
            val result = ruleEngine.analyze(RiskInput(text))
            assertThat(result.riskLevel).isEqualTo(RiskLevel.SAFE)
        }

        println("[시나리오7] 안전 메시지 ${safeMessages.size}개 모두 SAFE")
    }

    // ── 성능 포함 통합 테스트 ──────────────────────────────

    @Test
    fun `5가지 시나리오 전체가 2초 이내에 완료된다`() = runTest {
        val scenarios = listOf(
            "엄마 나야 새번호야 50만원 보내줘 말하지마",
            "검사입니다 안전계좌로 이체해주세요",
            "원금보장 VIP방 세력 매집",
            "출금하려면 세금 먼저",
            "AnyDesk 설치 화면 공유"
        )

        val start = System.currentTimeMillis()
        scenarios.forEach { text ->
            val result = ruleEngine.analyze(RiskInput(text))
            explanationGenerator.generateExplanation(result.input, result, emptyList())
        }
        val elapsed = System.currentTimeMillis() - start

        println("[통합] 시나리오 5개 완료: ${elapsed}ms")
        assertThat(elapsed).isLessThan(2000L)
    }
}
