package com.ansim.guardian.agent.nlu

import org.json.JSONObject

/**
 * sLLM이 생성한 도구 호출. GBNF grammar로 강제된 유효 JSON 1개.
 *
 * 도구 정의: assets/agent/tool_catalog.json
 * 자세한 설계: docs/codex-design/11-product-elderly/AGENT_TOOL_CATALOG_KO.md
 */
data class ToolCall(
    val tool: String,
    val args: Map<String, Any?>
) {
    fun argString(key: String): String? = args[key]?.toString()
    fun argInt(key: String): Int? = (args[key] as? Number)?.toInt()
    fun argBool(key: String): Boolean? = args[key] as? Boolean

    fun toJson(): JSONObject = JSONObject().apply {
        put("tool", tool)
        put("args", JSONObject(args))
    }

    companion object {
        /**
         * sLLM 출력 JSON을 파싱.
         * GBNF로 강제됐지만 만약 깨졌으면 ClarifyCall fallback.
         */
        fun parse(json: String): ToolCall {
            return runCatching {
                val obj = JSONObject(json)
                val tool = obj.getString("tool")
                val argsObj = obj.optJSONObject("args") ?: JSONObject()
                val args = buildMap<String, Any?> {
                    argsObj.keys().forEach { key ->
                        put(key, argsObj.get(key))
                    }
                }
                ToolCall(tool, args)
            }.getOrElse {
                clarifyFallback("응답이 잘 안 만들어졌어요")
            }
        }

        fun clarifyFallback(question: String): ToolCall =
            ToolCall("clarify", mapOf(
                "candidates" to listOf("전화", "문자", "앱"),
                "question" to question
            ))
    }
}
