package com.ansim.guardian.explanation

import com.ansim.guardian.ai.TemplateExplanationGenerator
import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.*
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * LLM/템플릿 설명 품질 테스트
 *
 * 설명이 노인에게 쉽게 전달되는지,
 * 단정 표현이 없는지, 위험도와 일치하는지 검증.
 */
class ExplanationQualityTest {

    private lateinit var engine: RuleBasedRiskEngine
    private lateinit var generator: TemplateExplanationGenerator

    // 법적으로 문제되는 단정 표현 목록
    private val forbiddenPhrases = listOf(
        "사기꾼입니다",
        "무조건 사기",
        "범죄자입니다",
        "확실히 사기",
        "100% 사기",
        "절대 사기"
    )

    // 너무 어려운 용어 목록
    private val hardTerms = listOf(
        "피싱 벡터",
        "사회공학",
        "소셜 엔지니어링",
        "위험 프로파일",
        "이상 탐지",
        "머신러닝"
    )

    @Before
    fun setUp() {
        engine = RuleBasedRiskEngine()
        generator = TemplateExplanationGenerator()
    }

    // ── 단정 표현 방지 ─────────────────────────────────────

    @Test
    fun `설명에 단정적 단어가 없다`() = runTest {
        val dangerTexts = listOf(
            "검사입니다 안전계좌로",
            "엄마 나야 새번호야 돈 보내줘",
            "원금보장 손실보전 VIP방"
        )

        dangerTexts.forEach { text ->
            val result = engine.analyze(RiskInput(text))
            val explanation = generator.generateExplanation(result.input, result, emptyList())
            val allText = listOf(explanation.summary, explanation.askGuardian)
                .plus(explanation.whyDangerous)
                .plus(explanation.doNotDo)
                .plus(explanation.doNow)
                .joinToString(" ")

            forbiddenPhrases.forEach { forbidden ->
                assertWithMessage("단정 표현 '$forbidden' 발견됨 (입력: $text)")
                    .that(allText).doesNotContain(forbidden)
            }
        }
    }

    // ── 쉬운 말 사용 ──────────────────────────────────────

    @Test
    fun `설명에 어려운 기술 용어가 없다`() = runTest {
        val result = engine.analyze(RiskInput("검사입니다 안전계좌로 이체해주세요"))
        val explanation = generator.generateExplanation(result.input, result, emptyList())
        val allText = listOf(explanation.summary)
            .plus(explanation.whyDangerous)
            .joinToString(" ")

        hardTerms.forEach { term ->
            assertWithMessage("어려운 용어 '$term' 발견됨")
                .that(allText.lowercase()).doesNotContain(term.lowercase())
        }
    }

    // ── 위험도와 설명 일치 ────────────────────────────────

    @Test
    fun `CRITICAL 위험도의 설명은 강한 경고를 포함한다`() = runTest {
        val result = engine.analyze(RiskInput("안전계좌로 인증번호 AnyDesk"))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        val explanation = generator.generateExplanation(result.input, result, emptyList())

        // CRITICAL 설명은 위험 또는 경고 표현 포함
        assertThat(explanation.summary).containsMatch("(위험|경고|사기|조심)")
        assertThat(explanation.doNow).isNotEmpty()
    }

    @Test
    fun `SAFE 판정에서는 과도한 공포 유발을 하지 않는다`() = runTest {
        val result = engine.analyze(RiskInput("오늘 날씨 좋네요"))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.SAFE)
        val explanation = generator.generateExplanation(result.input, result, emptyList())

        // 안전하다고 했는데 "위험" "사기"같은 단어가 나오면 안 됨
        val panicWords = listOf("매우 위험", "즉시", "긴급", "사기일 가능성이 매우")
        panicWords.forEach { word ->
            assertWithMessage("SAFE에서 공포 유발 단어 '$word' 발견")
                .that(explanation.summary).doesNotContain(word)
        }
    }

    // ── 출력 형식 ─────────────────────────────────────────

    @Test
    fun `모든 설명에 summary가 존재한다`() = runTest {
        listOf(RiskLevel.SAFE, RiskLevel.CAUTION, RiskLevel.DANGER, RiskLevel.CRITICAL).forEach { level ->
            val input = RiskInput("test")
            val mockResult = RiskResult(
                input = input,
                riskLevel = level,
                totalScore = level.minScore,
                detectedSignals = emptyList(),
                primaryCategory = null
            )
            val explanation = generator.generateExplanation(input, mockResult, emptyList())
            assertWithMessage("${level} 레벨에 summary 없음")
                .that(explanation.summary).isNotEmpty()
        }
    }

    @Test
    fun `DANGER 이상에서 doNow가 반드시 존재한다`() = runTest {
        listOf(
            "안전계좌 이체",
            "AnyDesk 설치",
            "엄마 나야 새번호 돈 보내줘"
        ).forEach { text ->
            val result = engine.analyze(RiskInput(text))
            if (result.riskLevel.ordinal >= RiskLevel.DANGER.ordinal) {
                val explanation = generator.generateExplanation(result.input, result, emptyList())
                assertWithMessage("$text → ${result.riskLevel} 에서 doNow 비어있음")
                    .that(explanation.doNow).isNotEmpty()
            }
        }
    }

    @Test
    fun `보호자에게 물어볼 말이 항상 존재한다`() = runTest {
        val result = engine.analyze(RiskInput("검사입니다 안전계좌"))
        val explanation = generator.generateExplanation(result.input, result, emptyList())
        assertThat(explanation.askGuardian).isNotEmpty()
    }

    // ── 설명 길이 ─────────────────────────────────────────

    @Test
    fun `summary는 100자 이내다`() = runTest {
        val result = engine.analyze(RiskInput("안전계좌 검사입니다"))
        val explanation = generator.generateExplanation(result.input, result, emptyList())
        assertWithMessage("summary 너무 긺: ${explanation.summary.length}자")
            .that(explanation.summary.length).isAtMost(100)
    }

    @Test
    fun `doNow 각 항목은 50자 이내다`() = runTest {
        val result = engine.analyze(RiskInput("안전계좌 검사입니다"))
        val explanation = generator.generateExplanation(result.input, result, emptyList())
        explanation.doNow.forEach { action ->
            assertWithMessage("doNow 항목 너무 긺: '$action'")
                .that(action.length).isAtMost(50)
        }
    }

    // ── Fallback 동작 ─────────────────────────────────────

    @Test
    fun `LLM 없이도 템플릿으로 완전한 설명이 생성된다`() = runTest {
        val result = engine.analyze(RiskInput("원금보장 VIP방 세력"))
        val explanation = generator.generateExplanation(result.input, result, emptyList())

        // 모든 필수 필드가 채워져야 함
        assertThat(explanation.summary).isNotEmpty()
        assertThat(explanation.whyDangerous).isNotEmpty()
        assertThat(explanation.doNotDo).isNotEmpty()
        assertThat(explanation.doNow).isNotEmpty()
        assertThat(explanation.askGuardian).isNotEmpty()
        assertThat(explanation.isFromLlm).isFalse()
    }
}
