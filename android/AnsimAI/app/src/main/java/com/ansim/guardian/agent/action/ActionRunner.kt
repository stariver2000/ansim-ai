package com.ansim.guardian.agent.action

import com.ansim.guardian.agent.nlu.ToolCall
import com.ansim.guardian.agent.nlu.ToolDefinition

/**
 * 도구 호출을 실제 안드로이드 액션으로 dispatch.
 *
 * 안전 흐름 (AGENT_TOOL_CATALOG_KO §5):
 *  1. validate (스키마)
 *  2. SafetyStrip (위험 의도 → L4 강등)
 *  3. ContactMatcher (person_ref 매칭, 후보 ≥2면 C2 clarify)
 *  4. needs_confirm이면 TTS confirm + 침묵=NO 대기
 *  5. L1/L2/L3/L4 어댑터로 dispatch
 *  6. audit log 기록
 *
 * Phase 3 구현 — L1만 먼저:
 *  - call_contact, scam_check_text, app_open, web_search, today_summary
 */
interface ActionRunner {
    suspend fun run(toolCall: ToolCall, definition: ToolDefinition): ActionResult
}

sealed class ActionResult {
    data class Success(val message: String, val data: Map<String, Any?> = emptyMap()) : ActionResult()
    data class Failed(val errorCode: String, val message: String) : ActionResult()
    data class Canceled(val reason: String) : ActionResult()
    data class Escalated(val toNas: Boolean = false, val toFamily: Boolean = false) : ActionResult()
}
