package com.ansim.guardian.agent.stt

import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * sherpa-onnx 한국어 streaming Zipformer 기반 [SpeechRecognizer] 구현.
 *
 * - 모델은 [ModelDownloader]로 받아둔 디렉토리에서 직접 로드 (assets 번들 X).
 * - 마이크는 [AudioCaptureSource]에서 16kHz mono Float 청크로 받음.
 * - sherpa-onnx의 endpoint detection을 그대로 사용해 [SttResult.isFinal] 결정.
 *
 * Phase 1 — POC_IMPLEMENTATION_PLAN_KO.md 1.4 인터페이스 매핑.
 *
 * 라이프사이클:
 *   val rec = SherpaOnnxRecognizer(downloader.modelDir(), mic)
 *   rec.initialize().getOrThrow()
 *   rec.startStreaming().collect { ... }
 *   rec.release()
 */
class SherpaOnnxRecognizer(
    private val modelDir: File,
    private val audioSource: AudioCaptureSource,
    private val numThreads: Int = 2,
    private val provider: String = "cpu",
) : SpeechRecognizer {

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun initialize(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            require(modelDir.exists()) { "Model dir not found: ${modelDir.absolutePath}" }

            val encoder = File(modelDir, "encoder-epoch-99-avg-1.int8.onnx")
            val decoder = File(modelDir, "decoder-epoch-99-avg-1.int8.onnx")
            val joiner = File(modelDir, "joiner-epoch-99-avg-1.int8.onnx")
            val tokens = File(modelDir, "tokens.txt")
            listOf(encoder, decoder, joiner, tokens).forEach {
                require(it.exists()) { "Missing model file: ${it.absolutePath}" }
            }

            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        joiner = joiner.absolutePath,
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = numThreads,
                    provider = provider,
                    // Korean Zipformer는 modelType 빈 문자열 권장 (sherpa-onnx 공식 README)
                    modelType = "",
                    debug = false,
                ),
                endpointConfig = EndpointConfig(), // 기본 rule (2.4s/1.4s/20s)
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            )
            recognizer = OnlineRecognizer(assetManager = null, config = config)
            stream = recognizer!!.createStream()
            Log.i(TAG, "OnlineRecognizer ready (provider=$provider, threads=$numThreads)")
        }
    }

    override suspend fun startStreaming(): Flow<SttResult> = callbackFlow {
        val rec = checkNotNull(recognizer) { "Call initialize() first" }
        val st = checkNotNull(stream) { "Stream not initialized" }

        var lastEmittedText = ""

        val collector = scope.launch {
            audioSource.pcmFloatFlow().collect { samples ->
                st.acceptWaveform(samples, sampleRate = 16000)

                while (rec.isReady(st)) {
                    rec.decode(st)
                }

                val isEndpoint = rec.isEndpoint(st)
                val result = rec.getResult(st)
                val text = result.text
                val avgConfidence = if (result.ysProbs.isEmpty()) 1.0f
                    else result.ysProbs.average().toFloat()

                if (text != lastEmittedText || isEndpoint) {
                    trySend(
                        SttResult(
                            text = text,
                            confidence = avgConfidence,
                            isFinal = isEndpoint,
                        )
                    )
                    lastEmittedText = text
                }

                if (isEndpoint) {
                    // 다음 발화 시작을 위해 reset (안 부르면 결과 텍스트가 무한 누적)
                    rec.reset(st)
                    lastEmittedText = ""
                }
            }
        }

        awaitClose { collector.cancel() }
    }.flowOn(Dispatchers.Default)

    override fun release() {
        scope.cancel()
        stream?.release()
        stream = null
        recognizer?.release()
        recognizer = null
        Log.i(TAG, "Released")
    }

    companion object {
        private const val TAG = "SherpaOnnxRecognizer"
    }
}
