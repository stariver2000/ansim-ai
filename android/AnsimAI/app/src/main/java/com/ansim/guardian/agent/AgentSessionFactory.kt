package com.ansim.guardian.agent

import android.content.Context
import com.ansim.guardian.agent.nlu.LlmIntentRouter
import com.ansim.guardian.agent.nlu.StubLlmEngine
import com.ansim.guardian.agent.nlu.ToolCatalog
import com.ansim.guardian.agent.stt.AudioCaptureSource
import com.ansim.guardian.agent.stt.ModelDownloader
import com.ansim.guardian.agent.stt.SherpaOnnxRecognizer
import com.ansim.guardian.agent.tts.AndroidTts
import com.ansim.guardian.agent.tts.TtsCopyProvider

/**
 * AgentSession + 모든 의존성을 한 번에 만드는 간단 DI 팩토리.
 * Hilt/Koin 도입 전까지 임시.
 *
 * 사용:
 *   val factory = AgentSessionFactory(applicationContext)
 *   val session = factory.build()
 *   session.initializeAll().getOrThrow()
 *   val result = session.handleOneTurn()
 */
class AgentSessionFactory(private val context: Context) {

    fun build(): AgentSession {
        val modelDownloader = ModelDownloader(context)
        val audioSource = AudioCaptureSource()
        val stt = SherpaOnnxRecognizer(modelDownloader.modelDir(), audioSource)

        val llm = StubLlmEngine(context)
        val toolCatalog = ToolCatalog.load(context)
        val nlu = LlmIntentRouter(context, llm, toolCatalog)

        val ttsCopy = TtsCopyProvider.load(context)
        val tts = AndroidTts(context, speechRate = ttsCopy.speechRate(), pitch = ttsCopy.pitch())

        return AgentSession(stt = stt, nlu = nlu, tts = tts, ttsCopy = ttsCopy)
    }

    /**
     * 모델 다운로드까지 포함한 헬퍼. 호출자가 진행률을 표시할 수 있게 Flow 반환.
     */
    fun modelDownloader(): ModelDownloader = ModelDownloader(context)
}
