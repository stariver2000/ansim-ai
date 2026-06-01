package com.ansim.guardian.agent

import android.content.Context
import com.ansim.guardian.agent.action.ActionRunnerImpl
import com.ansim.guardian.agent.action.ContactMatcher
import com.ansim.guardian.agent.action.L1Intent
import com.ansim.guardian.agent.action.L2Shortcut
import com.ansim.guardian.agent.action.L4Guide
import com.ansim.guardian.agent.action.ScamCheckTool
import com.ansim.guardian.agent.escalate.NasConnection
import com.ansim.guardian.agent.escalate.NasPlannerHttp
import com.ansim.guardian.agent.nlu.LlmIntentRouter
import com.ansim.guardian.agent.nlu.StubLlmEngine
import com.ansim.guardian.agent.nlu.ToolCatalog
import com.ansim.guardian.agent.stt.AudioCaptureSource
import com.ansim.guardian.agent.stt.ModelDownloader
import com.ansim.guardian.agent.stt.SherpaOnnxRecognizer
import com.ansim.guardian.agent.tts.AndroidTts
import com.ansim.guardian.agent.tts.TtsCopyProvider
import com.ansim.guardian.domain.engine.RiskEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * AgentSession + 모든 의존성을 한 번에 만드는 간단 DI 팩토리.
 * Hilt/Koin 도입 전까지 임시.
 *
 * 사용:
 *   val factory = AgentSessionFactory(applicationContext, hybridRiskEngine)
 *   val session = factory.build()
 *   session.initializeAll().getOrThrow()
 *   val result = session.handleOneTurn()
 */
class AgentSessionFactory(
    private val context: Context,
    private val riskEngine: RiskEngine,
    // Phase 11: NAS 페어링이 돼 있으면 NasConnection을 돌려주는 공급자 → NAS planner escalate 활성.
    // build() 시점에 매번 호출하므로 최신 페어링 상태(만료/해제 포함)를 반영. 기본은 폰 단독(null).
    private val nasConnectionProvider: () -> NasConnection? = { null },
) {

    fun build(): AgentSession {
        // STT
        val modelDownloader = ModelDownloader(context)
        val audioSource = AudioCaptureSource()
        val stt = SherpaOnnxRecognizer(modelDownloader.modelDir(), audioSource)

        // NLU
        val llm = StubLlmEngine(context)
        val toolCatalog = ToolCatalog.load(context)
        val nlu = LlmIntentRouter(context, llm, toolCatalog)

        // TTS
        val ttsCopy = TtsCopyProvider.load(context)
        val tts = AndroidTts(context, speechRate = ttsCopy.speechRate(), pitch = ttsCopy.pitch())

        // Action (Phase 3)
        val contactMatcher = ContactMatcher(context)
        val l1 = L1Intent(context, contactMatcher)
        val l2 = L2Shortcut(context)
        val l4 = L4Guide()
        val scamCheck = ScamCheckTool(riskEngine)
        val (downgradeKeywords, downgradeTaskMap) = loadSafetyStripConfig()
        val actionRunner = ActionRunnerImpl(
            context, toolCatalog, contactMatcher,
            l1, l2, l4, scamCheck,
            downgradeKeywords, downgradeTaskMap,
        )

        // NAS planner escalate (Phase 11) — 현재 페어링이 있을 때만 실 HTTP 클라이언트 연결.
        val nasPlanner = nasConnectionProvider()?.let { NasPlannerHttp(it) }

        return AgentSession(
            stt = stt, nlu = nlu, tts = tts, ttsCopy = ttsCopy,
            toolCatalog = toolCatalog, action = actionRunner,
            nasPlanner = nasPlanner,
        )
    }

    /** 모델 다운로드 (UI에서 진행률 표시) */
    fun modelDownloader(): ModelDownloader = ModelDownloader(context)

    private fun loadSafetyStripConfig(): Pair<List<String>, Map<String, String>> {
        val consts = JSONObject(
            context.assets.open("agent/agent_constants.json")
                .bufferedReader().use { it.readText() }
        )
        val ss = consts.getJSONObject("safety_strip")
        val kw: JSONArray = ss.getJSONArray("downgrade_keywords")
        val keywords = (0 until kw.length()).map { kw.getString(it) }
        val mapObj = ss.getJSONObject("downgrade_task_map")
        val taskMap = buildMap<String, String> {
            mapObj.keys().forEach { k -> put(k, mapObj.getString(k)) }
        }
        return keywords to taskMap
    }
}
