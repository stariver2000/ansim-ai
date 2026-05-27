package com.ansim.guardian.agent.nlu

/**
 * 폰의 작은 sLLM(Gemma 3 1B / Qwen2.5-1.5B)에 의도 분류 + 도구 호출 JSON 생성을 위임.
 *
 * Phase 2 구현:
 *  - 안심동행AI llama.cpp JNI 확장(GBNF grammar 옵션 추가)
 *  - assets/agent/nlu_system_prompt.txt + few_shot_examples.json 결합
 *  - assets/agent/byuldolbom-tool-call.gbnf 강제로 유효 JSON만 출력
 *
 * 자세한 설계: docs/codex-design/11-product-elderly/NLU_PROMPTS_KO.md
 */
interface IntentRouter {
    /**
     * 어르신 발화를 도구 호출 JSON으로 변환.
     * 모호하거나 unknown이면 ToolCall("clarify", ...)을 반환해야 함.
     * 위험 의도(송금·OTP·결제·앱설치 등)는 LLM 단계에서 app_guide_start로
     * 안내되도록 시스템 프롬프트 + few-shot에 강제되어 있음.
     * 추가 안전망은 SafetyStrip이 처리.
     */
    suspend fun route(utterance: String, context: AgentContext): ToolCall

    /** 모델 로드 / GBNF grammar 파싱 / 초기화 */
    suspend fun initialize(): Result<Unit>

    fun release()
}

/**
 * NLU 입력 컨텍스트. PromptBuilder가 시스템 프롬프트에 주입.
 */
data class AgentContext(
    val currentApp: String? = null,
    val nowKo: String = "",
    val knownAliases: List<String> = emptyList(),
    val recentTurns: List<String> = emptyList()
)
