package com.ansim.guardian.agent.stt

import kotlinx.coroutines.flow.Flow

/**
 * 온디바이스 STT 인터페이스.
 *
 * Phase 1 구현 후보:
 *  - sherpa-onnx Zipformer-ko (권장)
 *  - Vosk-ko-small (저사양 폰)
 *  - Whisper.cpp tiny/base (정밀도 우선)
 *
 * 자세한 비교: docs/codex-design/11-product-elderly/ON_DEVICE_AGENT_TECH_KO.md §1.A
 */
interface SpeechRecognizer {
    /**
     * 마이크 입력 시작. partial 및 final SttResult를 Flow로 흘림.
     * 호출자가 cancel하거나 침묵 임계(agent_constants.json silence_timeout_session_ms)
     * 도달 시 종료.
     */
    suspend fun startStreaming(): Flow<SttResult>

    /** 모델 로드 / 마이크 권한 확인 / 초기화 */
    suspend fun initialize(): Result<Unit>

    /** 정리 */
    fun release()
}

/**
 * STT 결과. confidence < agent_constants.json stt.min_confidence 면
 * C4 다이얼로그(재시도)로 라우팅 (CLARIFICATION_DIALOGUE_KO.md §5).
 */
data class SttResult(
    val text: String,
    val confidence: Float,
    val isFinal: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)
