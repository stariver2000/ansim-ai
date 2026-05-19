package com.ansim.guardian.engine

import com.ansim.guardian.ai.TemplateExplanationGenerator
import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.*
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TemplateExplanationGeneratorTest {

    private lateinit var generator: TemplateExplanationGenerator
    private lateinit var ruleEngine: RuleBasedRiskEngine

    @Before
    fun setUp() {
        generator = TemplateExplanationGenerator()
        ruleEngine = RuleBasedRiskEngine()
    }

    @Test
    fun `CRITICAL 위험에 대한 설명이 생성된다`() = runTest {
        val result = ruleEngine.analyze(RiskInput("안전계좌로 돈을 옮겨주세요"))
        val explanation = generator.generateExplanation(result.input, result, emptyList())

        assertThat(explanation.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(explanation.summary).isNotEmpty()
        assertThat(explanation.whyDangerous).isNotEmpty()
        assertThat(explanation.doNotDo).isNotEmpty()
        assertThat(explanation.doNow).isNotEmpty()
        assertThat(explanation.askGuardian).isNotEmpty()
    }

    @Test
    fun `SAFE 결과에 대한 설명도 생성된다`() = runTest {
        val result = ruleEngine.analyze(RiskInput("오늘 날씨 좋네요"))
        val explanation = generator.generateExplanation(result.input, result, emptyList())

        assertThat(explanation.riskLevel).isEqualTo(RiskLevel.SAFE)
        assertThat(explanation.summary).isNotEmpty()
    }

    @Test
    fun `모든 SignalCategory에 대해 설명이 생성된다`() = runTest {
        val testCases = mapOf(
            SignalCategory.VOICE_PHISHING      to "검사입니다 수사 중입니다",
            SignalCategory.FAMILY_IMPERSONATION to "엄마 나야 새번호야 돈 보내줘",
            SignalCategory.INVESTMENT_FRAUD    to "원금보장 수익 보장 VIP방",
            SignalCategory.UNLISTED_STOCK      to "상장 확정 기관 물량 10배",
            SignalCategory.CRYPTO_FRAUD        to "출금하려면 세금 먼저",
            SignalCategory.REMOTE_CONTROL      to "AnyDesk 설치해주세요",
            SignalCategory.SMISHING            to "택배 조회 bit.ly/xxx"
        )

        for ((category, text) in testCases) {
            val result = ruleEngine.analyze(RiskInput(text))
            val explanation = generator.generateExplanation(result.input, result, emptyList())

            assertWithMessage("카테고리 $category 에 대한 summary가 비어있음")
                .that(explanation.summary).isNotEmpty()
            assertWithMessage("카테고리 $category 에 대한 doNow가 비어있음")
                .that(explanation.doNow).isNotEmpty()
        }
    }

    @Test
    fun `유사 사기 사례가 있으면 설명에 포함된다`() = runTest {
        val result = ruleEngine.analyze(RiskInput("엄마 나야 폰 고장났어 돈 보내줘"))
        val similarCase = ScamCase(
            id = 1,
            category = "가족 사칭",
            title = "자녀 사칭 급전 사기",
            pattern = "새 번호로 자녀 사칭",
            exampleText = "엄마 나야",
            explanationEasy = "원래 번호로 확인하세요",
            recommendedAction = "전화해서 확인",
            keywords = listOf("엄마", "나야", "새번호")
        )

        val explanation = generator.generateExplanation(result.input, result, listOf(similarCase))
        assertThat(explanation.similarCase).isNotNull()
        assertThat(explanation.similarCase!!.title).isEqualTo("자녀 사칭 급전 사기")
    }

    @Test
    fun `설명의 doNotDo에 금전 관련 경고가 포함된다`() = runTest {
        val result = ruleEngine.analyze(RiskInput("안전계좌로 인증번호 알려주세요"))
        val explanation = generator.generateExplanation(result.input, result, emptyList())

        val allText = explanation.doNotDo.joinToString(" ")
        assertThat(allText).containsMatch("(돈|인증번호|송금|이체)")
    }

    @Test
    fun `isFromLlm은 템플릿에서 항상 false다`() = runTest {
        val result = ruleEngine.analyze(RiskInput("검사입니다"))
        val explanation = generator.generateExplanation(result.input, result, emptyList())
        assertThat(explanation.isFromLlm).isFalse()
    }
}
