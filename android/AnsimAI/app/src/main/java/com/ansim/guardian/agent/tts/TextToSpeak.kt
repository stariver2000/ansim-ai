package com.ansim.guardian.agent.tts

/**
 * 어르신 친화 TTS 추상 인터페이스. AndroidTts(기본) / SherpaTts(Piper, 옵션) swap 용도.
 */
interface TextToSpeak {
    suspend fun initialize(): Result<Unit>

    /** 발화 끝까지 대기 (suspend). cancel 시 TTS 즉시 정지. */
    suspend fun speak(text: String)

    /** 진행 중인 발화 즉시 정지 (CANCEL_PATTERN 매칭 시 호출). */
    fun stop()

    fun release()
}
