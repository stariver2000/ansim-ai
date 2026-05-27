package com.ansim.guardian.agent.nlu

/**
 * 작은 sLLM(폰 단독 1.5B 이하)에 prompt → JSON 응답을 받는 추상 진입점.
 *
 * Phase 2a (현재):
 *  - [StubLlmEngine] — few-shot 사전 매칭으로 빠른 PoC (네이티브 무의존)
 *
 * Phase 2b (다음):
 *  - LlamaCppEngine — llama.cpp + GGUF + GBNF grammar 강제 (JNI 재도입 필요)
 *  - 안심동행AI de35712에서 제거된 cpp/ 폴더 + ansim_llm_jni.cpp 재도입
 *
 * 자세한 설계:
 *  - docs/codex-design/11-product-elderly/AGENT_TOOL_CATALOG_KO.md §3 GBNF
 *  - docs/codex-design/11-product-elderly/NLU_PROMPTS_KO.md §1 시스템 프롬프트
 *  - docs/codex-design/11-product-elderly/ON_DEVICE_AGENT_TECH_KO.md §1.B
 */
interface LlmEngine {
    /** 모델 로드 / grammar 파싱. 무거운 작업이라 suspend. */
    suspend fun initialize(): Result<Unit>

    /**
     * @param systemPrompt nlu_system_prompt.txt 템플릿 치환된 결과
     * @param userUtterance 어르신 발화 (STT 결과)
     * @param grammarPath GBNF grammar 파일 경로 (assets/agent/byuldolbom-tool-call.gbnf 등)
     *                    Stub 구현에서는 무시.
     * @return 도구 호출 JSON 문자열. 항상 유효 JSON 보장 (실패 시 clarify fallback)
     */
    suspend fun generate(
        systemPrompt: String,
        userUtterance: String,
        grammarPath: String? = null,
        maxTokens: Int = 128,
        temperature: Float = 0.1f,
    ): String

    fun release()
}
