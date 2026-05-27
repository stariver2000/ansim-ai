package com.ansim.guardian.agent.nlu

import android.content.Context
import org.json.JSONObject

/**
 * assets/agent/tool_catalog.json 로더. 도구 정의 + 안전 레벨 + needs_confirm.
 *
 * 도구 카탈로그: docs/codex-design/11-product-elderly/AGENT_TOOL_CATALOG_KO.md
 */
class ToolCatalog private constructor(
    val version: String,
    val updated: String,
    val tools: List<ToolDefinition>
) {
    fun get(toolName: String): ToolDefinition? = tools.firstOrNull { it.name == toolName }

    /** PromptBuilder가 시스템 프롬프트에 주입할 짧은 요약 (NLU_PROMPTS_KO §2 형식) */
    fun summary(): String = tools.joinToString("\n") { tool ->
        val argsHint = tool.parameters.optJSONObject("properties")?.keys()
            ?.asSequence()?.joinToString(", ") ?: ""
        """- ${tool.name}($argsHint): ${tool.description}"""
    }

    companion object {
        fun load(context: Context): ToolCatalog {
            val json = context.assets.open("agent/tool_catalog.json")
                .bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val toolsArr = obj.getJSONArray("tools")
            val tools = (0 until toolsArr.length()).map { i ->
                val t = toolsArr.getJSONObject(i)
                ToolDefinition(
                    name = t.getString("name"),
                    description = t.getString("description"),
                    level = SafetyLevel.valueOf(t.getString("level")),
                    needsConfirm = t.getBoolean("needs_confirm"),
                    parameters = t.getJSONObject("parameters")
                )
            }
            return ToolCatalog(
                version = obj.getString("version"),
                updated = obj.getString("updated"),
                tools = tools
            )
        }
    }
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val level: SafetyLevel,
    val needsConfirm: Boolean,
    val parameters: JSONObject
)

/**
 * L1 표준 Intent / L2 App Shortcut / L3 Accessibility 자동 조작 /
 * L4 가이드만 (어르신 직접 누름)
 */
enum class SafetyLevel { L1, L2, L3, L4 }
