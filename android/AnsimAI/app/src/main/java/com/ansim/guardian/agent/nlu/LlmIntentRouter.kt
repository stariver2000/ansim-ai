package com.ansim.guardian.agent.nlu

import android.content.Context
import android.util.Log

/**
 * LlmEngine + PromptBuilder + ToolCatalog 결합 실 구현.
 * Phase 2a — StubLlmEngine 위에서도 동작. Phase 2b에서 LlamaCppEngine으로 swap.
 *
 * 흐름:
 *   utterance + AgentContext
 *     → PromptBuilder.build(context) → systemPrompt
 *     → LlmEngine.generate(systemPrompt, utterance, grammarPath)
 *     → JSON 문자열
 *     → ToolCall.parse → 도구 검증
 *     → 알 수 없는 도구면 clarify fallback
 */
class LlmIntentRouter(
    context: Context,
    private val llm: LlmEngine,
    private val toolCatalog: ToolCatalog = ToolCatalog.load(context),
) : IntentRouter {

    private val promptBuilder = PromptBuilder(context, toolCatalog)
    private val grammarPath = GRAMMAR_ASSET_PATH

    override suspend fun initialize(): Result<Unit> = llm.initialize()

    override suspend fun route(utterance: String, context: AgentContext): ToolCall {
        val systemPrompt = promptBuilder.build(context)
        val rawJson = try {
            llm.generate(
                systemPrompt = systemPrompt,
                userUtterance = utterance,
                grammarPath = grammarPath,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "LLM generate failed: ${t.message}")
            return ToolCall.clarifyFallback("응답을 못 만들었어요. 다시 말씀해 주세요.")
        }

        val call = ToolCall.parse(rawJson)

        // 카탈로그에 없는 도구면 clarify
        if (toolCatalog.get(call.tool) == null) {
            Log.w(TAG, "Unknown tool from LLM: ${call.tool}")
            return ToolCall.clarifyFallback("그건 아직 못 도와드려요.")
        }

        return call
    }

    override fun release() = llm.release()

    companion object {
        private const val TAG = "LlmIntentRouter"
        const val GRAMMAR_ASSET_PATH = "agent/byuldolbom-tool-call.gbnf"
    }
}
