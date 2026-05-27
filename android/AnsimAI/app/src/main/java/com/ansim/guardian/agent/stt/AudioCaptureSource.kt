package com.ansim.guardian.agent.stt

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlin.concurrent.thread

/**
 * 마이크 → 16 kHz mono PCM Float[-1, 1] 청크 Flow.
 *
 * - 청크 100ms (1600 샘플). sherpa-onnx 스트리밍이 빈번한 청크 입력에 강함.
 * - VOICE_RECOGNITION 소스: 시스템 노이즈 억제 + AGC 적용 (시니어 발화 친화).
 * - back-pressure: trySend 실패 시 chunk drop (소비자가 너무 느리면 STT보다 마이크가 우선).
 *
 * 사용:
 *   AudioCaptureSource().pcmFloatFlow().collect { samples -> recognizer.feed(samples) }
 */
class AudioCaptureSource(
    private val sampleRate: Int = 16000,
    private val chunkMillis: Int = 100,
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
) {
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @SuppressLint("MissingPermission") // 외부 호출자가 권한 보장
    fun pcmFloatFlow(): Flow<FloatArray> = callbackFlow {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        check(minBuf > 0) { "AudioRecord.getMinBufferSize returned $minBuf" }

        // 최소 1초 버퍼링 (앱 정지/일시중단에도 손실 줄이기)
        val bufferSizeInBytes = (minBuf * 2).coerceAtLeast(sampleRate * 2)

        val record = AudioRecord(
            audioSource, sampleRate, channelConfig, audioFormat, bufferSizeInBytes
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize (state=${record.state})"
        }

        val chunkSamples = sampleRate * chunkMillis / 1000
        val shortBuf = ShortArray(chunkSamples)

        @Volatile var running = true
        record.startRecording()

        val readerThread = thread(name = "byuldolbom-mic", isDaemon = true) {
            try {
                while (running) {
                    val n = record.read(shortBuf, 0, shortBuf.size)
                    if (n <= 0) continue
                    // Short → Float [-1, 1]. sherpa-onnx 공식 데모와 동일.
                    val samples = FloatArray(n) { i -> shortBuf[i] / 32768.0f }
                    trySend(samples)
                }
            } catch (t: Throwable) {
                close(t)
            }
        }

        awaitClose {
            running = false
            try { record.stop() } catch (_: Throwable) {}
            record.release()
            readerThread.join(500)
        }
    }.flowOn(Dispatchers.IO)
}
