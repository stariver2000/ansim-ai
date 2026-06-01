package com.ansim.guardian.agent.escalate

import android.util.Log
import com.ansim.guardian.agent.nlu.AgentContext
import com.ansim.guardian.agent.nlu.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 폰 sLLM이 처리 못한 의도를 NAS planner(EXAONE-2.4B)에 escalate하는 실제 HTTP 구현.
 *
 * 계약(POST {baseUrl}/api/elderly/agent/plan, byulserver-elderly-nas 포트 8002):
 *   요청  : { "utterance": String, "screen_kind": String?, "app_pkg": String? }
 *   헤더  : X-Device-Token: <Device JWT>
 *   응답  : { tool, args, say, matched_card_id, family_notified }
 *
 * 설계 원칙(NAS_PLANNER_DESIGN_KO.md):
 *  - **폰 단독 우선**: NAS 미연결·시간 초과·코어 다운이면 [plan]은 **null** 반환 →
 *    AgentSession이 폰 단독 fallback("잘 모르겠어요")으로 자연스럽게 떨어진다. 절대 throw 안 함.
 *  - **원문은 NAS 안에서만**: utterance는 vault로만 가고, 응답엔 비식별 메타+say만 온다.
 *  - NAS가 돌려준 say(어르신용 안내 멘트)는 ToolCall.args["say"]에 실어 AgentSession이 그대로 읽게 한다.
 *
 * Phase 11에서 자체구축 WireGuard split VPN 페어링이 붙으면 [NasConnection]에 실제 NAS 주소/Device JWT가 주입된다.
 * 페어링 전(=현재)에는 AgentSessionFactory가 이 클라이언트를 만들지 않으므로(nasPlanner=null)
 * 프로덕션 동작에 영향이 없다.
 *
 * 자세한 설계: docs/codex-design/11-product-elderly/NAS_PLANNER_DESIGN_KO.md
 */
class NasPlannerHttp(
    private val connection: NasConnection,
    private val http: OkHttpClient = defaultClient(),
) : NasPlanner {

    override suspend fun plan(utterance: String, context: AgentContext): ToolCall? =
        withContext(Dispatchers.IO) {
            val token = connection.deviceToken() ?: run {
                Log.i(TAG, "NAS escalate 건너뜀: Device JWT 없음(미페어링)")
                return@withContext null
            }
            try {
                val payload = JSONObject().apply {
                    put("utterance", utterance)
                    put("screen_kind", context.screenKind)   // null이면 JSON null
                    put("app_pkg", context.currentApp)
                }.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("${connection.baseUrl.trimEnd('/')}/api/elderly/agent/plan")
                    .addHeader("X-Device-Token", token)
                    .post(payload)
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "NAS planner 응답 오류: ${response.code}")
                        return@withContext null
                    }
                    val raw = response.body?.string() ?: return@withContext null
                    parsePlan(raw)
                }
            } catch (e: Exception) {
                // 네트워크/타임아웃/파싱 — 폰 단독 fallback. 원문은 로그에 남기지 않음.
                Log.w(TAG, "NAS planner 미연결/실패(폰 단독 fallback): ${e.message}")
                null
            }
        }

    /**
     * NAS 로컬 sLLM planner가 준비됐는가(mode=="sllm")? 인증 불필요한 상태 probe.
     * 도달 실패/오류면 null(= 모름). NAS가 응답하되 기본모드면 false.
     */
    suspend fun isPlannerReady(): Boolean? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${connection.baseUrl.trimEnd('/')}/api/elderly/agent/planner-health")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val raw = response.body?.string() ?: return@withContext null
                JSONObject(raw).optString("mode") == "sllm"
            }
        } catch (e: Exception) {
            Log.w(TAG, "planner-health 확인 실패: ${e.message}")
            null
        }
    }

    private fun parsePlan(raw: String): ToolCall? = runCatching {
        val obj = JSONObject(raw)
        val tool = obj.getString("tool")
        val argsObj = obj.optJSONObject("args") ?: JSONObject()
        val args = buildMap<String, Any?> {
            argsObj.keys().forEach { key -> put(key, argsObj.get(key)) }
            // NAS가 만든 어르신용 안내 멘트를 args에 실어 보존
            obj.optString("say").takeIf { it.isNotBlank() }?.let { put("say", it) }
            if (!obj.isNull("matched_card_id")) put("matched_card_id", obj.optInt("matched_card_id"))
            put("family_notified", obj.optBoolean("family_notified", false))
            put("source", "nas_planner")
        }
        ToolCall(tool, args)
    }.getOrElse {
        Log.w(TAG, "NAS planner 응답 파싱 실패: ${it.message}")
        null
    }

    companion object {
        private const val TAG = "NasPlannerHttp"
        private val JSON = "application/json".toMediaType()

        /** planner 지연 예산: connect 2s, NAS sLLM 추론 여유 read 8s. 폰 UX는 길게 안 끈다. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * NAS 연결 정보. Phase 11 자체구축 WireGuard split VPN 페어링이 채운다.
 *
 * @param baseUrl       NAS 주소 (예: WireGuard 터널 대역 "http://10.x.x.x:8002")
 * @param deviceToken   유효한 Device JWT를 돌려주는 공급자. 미페어링이면 null →
 *                      NasPlannerHttp가 escalate를 조용히 건너뜀.
 */
class NasConnection(
    val baseUrl: String,
    val deviceToken: suspend () -> String?,
)
