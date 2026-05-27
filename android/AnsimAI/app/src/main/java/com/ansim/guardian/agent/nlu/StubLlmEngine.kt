package com.ansim.guardian.agent.nlu

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 2a 임시 LLM 엔진. 실제 sLLM 추론 없이 assets/agent/few_shot_examples.json
 * 의 발화-출력 사전을 단순 매칭해 도구 호출 JSON을 반환한다.
 *
 * 매칭 룰:
 *  1. exact match (정규화 후 동일)
 *  2. 부분 키워드 매칭 (가장 긴 공통 부분 우선)
 *  3. 강등 키워드 감지 → app_guide_start 강제 (SafetyStrip의 안전망 역할)
 *  4. 매칭 실패 → clarify 폴백
 *
 * Phase 2b에서 LlamaCppEngine으로 교체.
 *
 * 이 stub의 목적:
 *  - 음성 파이프라인(STT → NLU → TTS) 전체 동작을 *native 없이* 검증
 *  - few-shot 사전 자체의 커버리지 점검 (NLU_PROMPTS_KO.md §6 평가셋 20개 기준)
 */
class StubLlmEngine(private val context: Context) : LlmEngine {

    private var examples: List<Pair<String, JSONObject>> = emptyList()
    private var downgradeKeywords: List<String> = emptyList()
    private var downgradeTaskMap: Map<String, String> = emptyMap()

    override suspend fun initialize(): Result<Unit> = runCatching {
        // few-shot 사전
        val json = context.assets.open("agent/few_shot_examples.json")
            .bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("examples")
        examples = (0 until arr.length()).map { i ->
            val ex = arr.getJSONObject(i)
            ex.getString("utterance").normalize() to ex.getJSONObject("output")
        }

        // 강등 키워드
        val consts = JSONObject(
            context.assets.open("agent/agent_constants.json")
                .bufferedReader().use { it.readText() }
        )
        val ssObj = consts.getJSONObject("safety_strip")
        val dkArr: JSONArray = ssObj.getJSONArray("downgrade_keywords")
        downgradeKeywords = (0 until dkArr.length()).map { dkArr.getString(it) }
        val dmObj = ssObj.getJSONObject("downgrade_task_map")
        downgradeTaskMap = buildMap {
            dmObj.keys().forEach { k -> put(k, dmObj.getString(k)) }
        }

        Log.i(TAG, "StubLlmEngine ready (${examples.size} examples, ${downgradeKeywords.size} downgrade keywords)")
    }

    override suspend fun generate(
        systemPrompt: String,
        userUtterance: String,
        grammarPath: String?,
        maxTokens: Int,
        temperature: Float,
    ): String {
        val normalized = userUtterance.normalize()

        // 1) 강등 키워드 먼저 — 위험 의도는 다른 매칭보다 우선
        val downgradeHit = downgradeKeywords.firstOrNull { normalized.contains(it.normalize()) }
        if (downgradeHit != null) {
            val task = downgradeTaskMap[downgradeHit] ?: "bank_send_money"
            return jsonOf("app_guide_start", mapOf(
                "task" to task,
                "downgraded_from_keyword" to downgradeHit
            ))
        }

        // 2) exact match
        examples.firstOrNull { it.first == normalized }?.let { return it.second.toString() }

        // 3) 부분 키워드 매칭 — 가장 긴 공통 substring 점수
        val scored = examples.map { (ex, out) ->
            Triple(ex, out, longestCommonSubstring(normalized, ex).length)
        }.filter { it.third >= 3 }  // 최소 3글자 이상 공통

        val best = scored.maxByOrNull { it.third }
        if (best != null) return best.second.toString()

        // 4) clarify 폴백
        return jsonOf("clarify", mapOf(
            "candidates" to listOf("전화", "문자", "앱"),
            "question" to "잘 못 알아들었어요. 다시 말씀해 주세요."
        ))
    }

    override fun release() {
        examples = emptyList()
    }

    // ─── 유틸 ─────────────────────────────────────────────────

    private fun String.normalize(): String =
        trim().lowercase().replace(Regex("\\s+"), " ")

    private fun longestCommonSubstring(a: String, b: String): String {
        if (a.isEmpty() || b.isEmpty()) return ""
        var best = ""
        for (i in a.indices) {
            for (j in b.indices) {
                var k = 0
                while (i + k < a.length && j + k < b.length && a[i + k] == b[j + k]) k++
                if (k > best.length) best = a.substring(i, i + k)
            }
        }
        return best
    }

    private fun jsonOf(tool: String, args: Map<String, Any?>): String =
        JSONObject().apply {
            put("tool", tool)
            put("args", JSONObject(args))
        }.toString()

    companion object { private const val TAG = "StubLlmEngine" }
}
