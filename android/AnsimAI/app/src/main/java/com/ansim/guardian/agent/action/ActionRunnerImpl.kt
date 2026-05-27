package com.ansim.guardian.agent.action

import android.content.Context
import android.util.Log
import com.ansim.guardian.agent.nlu.ToolCall
import com.ansim.guardian.agent.nlu.ToolDefinition
import com.ansim.guardian.agent.nlu.ToolCatalog

/**
 * [ActionRunner] 실 구현. Phase 3에서 L1/L2/L4 도구 routing.
 *
 * 흐름:
 *   1. SafetyStrip — 위험 의도 자동 강등 (송금 → app_guide_start)
 *   2. 도구별 어댑터 호출 (L1Intent / L2Shortcut / L4Guide / ScamCheckTool)
 *   3. 결과 ActionResult 반환 — AgentSession이 TTS로 read
 *
 * Phase 8 (Accessibility L3)·Phase 11 (NAS escalate)는 별도 case 추가.
 */
class ActionRunnerImpl(
    @Suppress("unused") private val context: Context,
    private val toolCatalog: ToolCatalog,
    private val contactMatcher: ContactMatcher,
    private val l1: L1Intent,
    private val l2: L2Shortcut,
    private val l4: L4Guide,
    private val scamCheck: ScamCheckTool,
    private val downgradeKeywords: List<String>,
    private val downgradeTaskMap: Map<String, String>,
) : ActionRunner {

    override suspend fun run(toolCall: ToolCall, definition: ToolDefinition): ActionResult {
        // 0) Safety net (LLM이 강등 안 했을 경우 마지막 안전망)
        val safe = SafetyStrip.strip(toolCall, downgradeKeywords, downgradeTaskMap)
        if (safe.tool != toolCall.tool) {
            Log.i(TAG, "Safety downgrade: ${toolCall.tool} → ${safe.tool}")
        }

        return when (safe.tool) {
            // L1 표준 Intent
            "call_contact" -> {
                val ref = safe.argString("person_ref") ?: return invalid("person_ref missing")
                l1.callContact(ref)
            }
            "call_emergency" -> {
                val kind = safe.argString("kind") ?: return invalid("kind missing")
                if (kind == "family") {
                    // family는 NAS planner의 call_family_for_help로 위임 권장 (Phase 11)
                    ActionResult.Escalated(toFamily = true)
                } else {
                    l1.callEmergency(kind)
                }
            }
            "web_search" -> {
                val q = safe.argString("query") ?: return invalid("query missing")
                l1.webSearch(q)
            }
            "map_navigate" -> {
                val dest = safe.argString("destination") ?: return invalid("destination missing")
                val mode = safe.argString("mode") ?: "transit"
                l1.mapNavigate(dest, mode)
            }

            // L2 App Shortcut
            "app_open" -> {
                val alias = safe.argString("app_alias") ?: return invalid("app_alias missing")
                l2.openApp(alias)
            }

            // L4 가이드만
            "app_guide_start" -> {
                val task = safe.argString("task") ?: return invalid("task missing")
                val from = safe.argString("downgraded_from_keyword")
                    ?: safe.argString("downgraded_from")
                l4.appGuideStart(task, from)
            }
            "guide_card_show" -> l4.guideCardShow(
                safe.argString("card_id"), safe.argString("target_app")
            )

            // 사기 탐지 (안심동행AI HybridRiskEngine 어댑터)
            "scam_check_text" -> {
                val text = safe.argString("text") ?: return invalid("text missing")
                scamCheck.checkText(text, safe.argString("sender_hint"))
            }

            // Phase 11+에서 NAS planner 위임
            "call_family_for_help", "notify_family_silent" ->
                ActionResult.Escalated(toFamily = true)

            // Phase 8 Accessibility L3 도구
            "scam_check_screen", "scam_check_call",
            "today_summary", "weather_today", "medication_log_check",
            "medication_log_add", "video_call_family", "send_sms_contact",
            "app_guide_step_next" -> {
                Log.i(TAG, "Tool '${safe.tool}' not implemented yet (Phase 4~11)")
                ActionResult.Failed("not_implemented", "결과는 다음 단계에서 만들어드릴게요.")
            }

            // clarify는 AgentSession이 처리 (여기 안 들어옴)
            "clarify" -> ActionResult.Canceled("clarify_should_be_handled_by_session")

            else -> {
                Log.w(TAG, "Unknown tool: ${safe.tool}")
                ActionResult.Failed("unknown_tool", "그건 아직 못 도와드려요.")
            }
        }
    }

    private fun invalid(msg: String): ActionResult.Failed =
        ActionResult.Failed("invalid_args", msg)

    companion object { private const val TAG = "ActionRunnerImpl" }
}
