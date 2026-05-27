package com.ansim.guardian.agent.action

import com.ansim.guardian.agent.nlu.ToolCall

/**
 * 위험 의도를 자동으로 L4 가이드(app_guide_start)로 강등.
 *
 * 강등 키워드 사전: assets/agent/agent_constants.json safety_strip.downgrade_keywords
 *
 * 정책 근거:
 *  - BYULDOLBOM_V3_DESIGN_KO.md D2 — 위험 액션은 무조건 L4
 *  - Rabbit R1 실패 교훈: 위험 액션 자동화는 사람에게 돌려줌
 *
 * Phase 3 구현.
 */
object SafetyStrip {
    /**
     * sLLM 결과가 위험 키워드를 args에 포함하면 app_guide_start로 강제 교체.
     * sLLM이 시스템 프롬프트로 이미 강등하도록 학습되지만, 안전망으로 한 번 더.
     */
    fun strip(toolCall: ToolCall, downgradeKeywords: List<String>, taskMap: Map<String, String>): ToolCall {
        // 도구가 이미 app_guide_start / guide_card_show면 통과
        if (toolCall.tool in setOf("app_guide_start", "guide_card_show", "clarify")) return toolCall

        // args의 string 값들에서 강등 키워드 검사
        val joinedArgs = toolCall.args.values
            .filterIsInstance<String>()
            .joinToString(" ")
        val hit = downgradeKeywords.firstOrNull { it in joinedArgs }
        return if (hit != null) {
            val task = taskMap[hit] ?: "bank_send_money"
            ToolCall("app_guide_start", mapOf("task" to task, "downgraded_from" to toolCall.tool))
        } else {
            toolCall
        }
    }
}
