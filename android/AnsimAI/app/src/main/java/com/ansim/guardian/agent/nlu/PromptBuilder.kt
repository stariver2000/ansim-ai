package com.ansim.guardian.agent.nlu

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * assets/agent/nlu_system_prompt.txt 템플릿을 컨텍스트로 치환.
 *
 * 템플릿 자리:
 *  - {TOOL_CATALOG_SUMMARY} ← ToolCatalog.summary()
 *  - {CURRENT_APP}          ← AgentContext.currentApp
 *  - {NOW_KO}               ← 어르신 친화 시각 표현
 *  - {KNOWN_ALIASES}        ← AgentContext.knownAliases
 *  - {USER_UTTERANCE}       ← 발화 (generate 호출 시 별도 전달이라 비워둠)
 *
 * 자세한 설계: docs/codex-design/11-product-elderly/NLU_PROMPTS_KO.md §1, §2, §7
 */
class PromptBuilder(
    context: Context,
    private val toolCatalog: ToolCatalog,
) {
    private val template: String = context.assets
        .open("agent/nlu_system_prompt.txt")
        .bufferedReader().use { it.readText() }

    fun build(agentContext: AgentContext): String {
        return template
            .replace("{TOOL_CATALOG_SUMMARY}", toolCatalog.summary())
            .replace("{CURRENT_APP}", agentContext.currentApp ?: "없음")
            .replace("{NOW_KO}", agentContext.nowKo.ifBlank { friendlyNow() })
            .replace("{KNOWN_ALIASES}", agentContext.knownAliases.joinToString(", ").ifBlank { "(미등록)" })
            // {USER_UTTERANCE}는 LlmEngine이 별도 전달, 템플릿엔 마지막 마커로 남김
    }

    /** "오후 2시 30분" 같은 어르신 친화 표기 (TTS_COPY_DICTIONARY_KO.md §11) */
    private fun friendlyNow(): String {
        val now = Date()
        val cal = java.util.Calendar.getInstance().apply { time = now }
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val m = cal.get(java.util.Calendar.MINUTE)
        val period = when (h) {
            in 5..11 -> "아침"
            in 12..12 -> "낮"
            in 13..17 -> "오후"
            in 18..21 -> "저녁"
            else -> "밤"
        }
        val displayH = if (h == 0 || h == 12) 12 else h % 12
        return "$period ${displayH}시" + if (m > 0) " ${m}분" else ""
    }
}
