package com.ansim.guardian.agent.tts

import android.content.Context
import org.json.JSONObject

/**
 * assets/agent/tts_copy.json 로더 + {placeholder} 치환.
 *
 * 카피 사전: docs/codex-design/11-product-elderly/TTS_COPY_DICTIONARY_KO.md
 */
class TtsCopyProvider private constructor(private val root: JSONObject) {

    /** confirm.{key}, result_success.{key}, clarify.{key} 등 점 경로로 조회 */
    fun get(path: String, placeholders: Map<String, String> = emptyMap()): String {
        val template = lookupString(path) ?: return "(카피 없음: $path)"
        return placeholders.entries.fold(template) { acc, (k, v) ->
            acc.replace("{$k}", v)
        }
    }

    private fun lookupString(path: String): String? {
        var node: Any? = root
        for (segment in path.split('.')) {
            node = (node as? JSONObject)?.opt(segment) ?: return null
        }
        return node as? String
    }

    fun speechRate(): Float = root.getJSONObject("tts_params").getDouble("speech_rate").toFloat()
    fun pitch(): Float = root.getJSONObject("tts_params").getDouble("pitch").toFloat()

    companion object {
        fun load(context: Context): TtsCopyProvider {
            val json = context.assets.open("agent/tts_copy.json")
                .bufferedReader().use { it.readText() }
            return TtsCopyProvider(JSONObject(json))
        }
    }
}
