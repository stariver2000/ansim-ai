package com.ansim.guardian.agent.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Android 기본 TextToSpeech 래퍼. 어르신 친화 파라미터.
 *
 * tts_copy.json tts_params: speech_rate 0.8 / pitch 0.95 / ko-KR
 *
 * 자세한 설계:
 *  - docs/codex-design/11-product-elderly/TTS_COPY_DICTIONARY_KO.md §1 톤 원칙
 *  - docs/codex-design/11-product-elderly/ON_DEVICE_AGENT_TECH_KO.md §1.D
 */
class AndroidTts(
    private val context: Context,
    private val speechRate: Float = 0.8f,
    private val pitch: Float = 0.95f,
) : TextToSpeak {

    private var tts: TextToSpeech? = null
    @Volatile private var initialized = false

    override suspend fun initialize(): Result<Unit> = suspendCancellableCoroutine { cont ->
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                cont.resume(Result.failure(IllegalStateException("TTS init failed: $status")))
                return@TextToSpeech
            }
            tts?.apply {
                val langResult = setLanguage(Locale.KOREAN)
                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    cont.resume(Result.failure(
                        IllegalStateException("Korean TTS not available (status=$langResult)")
                    ))
                    return@TextToSpeech
                }
                setSpeechRate(speechRate)
                setPitch(pitch)
            }
            initialized = true
            Log.i(TAG, "AndroidTts ready (rate=$speechRate, pitch=$pitch)")
            cont.resume(Result.success(Unit))
        }
    }

    override suspend fun speak(text: String): Unit = suspendCancellableCoroutine { cont ->
        val engine = tts
        if (!initialized || engine == null) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        val utteranceId = UUID.randomUUID().toString()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(Unit)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(Unit)
            }
            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && cont.isActive) cont.resume(Unit)
            }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        cont.invokeOnCancellation { engine.stop() }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {}
        tts = null
        initialized = false
    }

    companion object { private const val TAG = "AndroidTts" }
}
