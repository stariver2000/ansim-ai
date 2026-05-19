package com.ansim.guardian.ai

import com.ansim.guardian.domain.engine.ExplanationGenerator
import com.ansim.guardian.domain.model.Explanation
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskResult
import com.ansim.guardian.domain.model.ScamCase

// LLM 교체 가능한 구조
// 현재는 TemplateExplanationGenerator를 사용하며,
// 추후 Qwen2.5 Q4 (llama.cpp JNI) 또는 외부 API로 교체 가능
interface LlmProvider {
    suspend fun generate(prompt: String): String
    fun isAvailable(): Boolean
}

// 추후 llama.cpp JNI 연동 시 이 클래스를 구현
class QwenLocalLlmProvider : LlmProvider {
    override suspend fun generate(prompt: String): String {
        throw UnsupportedOperationException("아직 구현되지 않았습니다. 3단계에서 추가됩니다.")
    }
    override fun isAvailable(): Boolean = false
}

// 추후 Claude API 연동 시 이 클래스를 구현
class ClaudeApiLlmProvider(private val apiKey: String) : LlmProvider {
    override suspend fun generate(prompt: String): String {
        throw UnsupportedOperationException("아직 구현되지 않았습니다.")
    }
    override fun isAvailable(): Boolean = false
}

class LocalLlmManager(
    private val deviceChecker: DeviceCapabilityChecker,
    private val templateGenerator: TemplateExplanationGenerator,
    private val llmProvider: LlmProvider = QwenLocalLlmProvider()
) : ExplanationGenerator {

    override suspend fun generateExplanation(
        input: RiskInput,
        riskResult: RiskResult,
        cases: List<ScamCase>
    ): Explanation {
        // 기기가 LLM을 지원하고 프로바이더가 사용 가능하면 LLM 사용
        return if (deviceChecker.canRunLlm() && llmProvider.isAvailable()) {
            generateWithLlm(input, riskResult, cases)
        } else {
            // 폴백: 항상 동작하는 템플릿 방식
            templateGenerator.generateExplanation(input, riskResult, cases)
        }
    }

    private suspend fun generateWithLlm(
        input: RiskInput,
        riskResult: RiskResult,
        cases: List<ScamCase>
    ): Explanation {
        val prompt = buildPrompt(input, riskResult, cases)
        return try {
            val response = llmProvider.generate(prompt)
            parseLlmResponse(response, riskResult, cases)
        } catch (e: Exception) {
            // LLM 실패 시 템플릿으로 폴백
            templateGenerator.generateExplanation(input, riskResult, cases)
        }
    }

    private fun buildPrompt(
        input: RiskInput,
        riskResult: RiskResult,
        cases: List<ScamCase>
    ): String {
        val signals = riskResult.detectedSignals.joinToString("\n") { "- ${it.description}" }
        val caseText = cases.take(2).joinToString("\n") {
            "사례: ${it.title}\n${it.explanationEasy}"
        }
        return """
사용자 입력:
${input.text}

탐지된 위험 신호:
$signals

위험도:
${riskResult.riskLevel.label}

유사 사기 사례:
$caseText

요청:
노인 사용자가 이해하기 쉽게 짧은 문장으로 설명해줘.
사기라고 단정하지 말고, "사기일 수 있어요", "조심해야 해요"처럼 표현해줘.

다음 형식으로만 답해줘:
왜 조심해야 하나요: (1~3줄)
하지 말아야 할 행동: (1~3가지)
지금 해야 할 행동: (1~2가지)
보호자에게 물어볼 말: (한 문장)
        """.trimIndent()
    }

    private suspend fun parseLlmResponse(
        response: String,
        riskResult: RiskResult,
        cases: List<ScamCase>
    ): Explanation {
        // LLM 응답 파싱 로직 (추후 구현)
        // 현재는 템플릿으로 폴백
        return templateGenerator.generateExplanation(
            riskResult.input, riskResult, cases
        )
    }
}
