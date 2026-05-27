package com.ansim.guardian.agent.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 한국어 streaming Zipformer 모델을 GitHub Releases(또는 NAS)에서 받아 풀어둔다.
 *
 * - 첫 실행 후 한 번만 받음 (verifyModel로 무결성 검사).
 * - int8 양자화 조합만 사용 → 약 133MB.
 * - 압축 해제: tar.bz2 → encoder/decoder/joiner/tokens.txt
 *
 * 별돌봄 우선순위:
 *   1순위 — NAS 페어링 시 NAS LAN에서 받기 (Phase 11+)
 *   2순위 — GitHub Releases 직접 다운로드 (현재 default)
 *
 * Wi-Fi 강제는 ConnectivityManager 체크로 호출자가 보장 (200MB tar라 셀룰러 부담 큼).
 */
class ModelDownloader(private val context: Context) {

    data class Progress(val bytesRead: Long, val totalBytes: Long) {
        val ratio: Float get() = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
    }

    /**
     * 모델이 이미 valid 하면 즉시 (1, 1) 한 번 emit하고 종료.
     * 없거나 손상되면 다운로드 + 압축 해제, 진행률 streaming.
     */
    fun ensureModel(downloadUrl: String = DEFAULT_URL): Flow<Progress> = flow {
        if (verifyModel()) {
            Log.i(TAG, "Model already present at ${modelDir().absolutePath}")
            emit(Progress(1, 1))
            return@flow
        }

        val tmpTar = File(context.cacheDir, "stt-model.tar.bz2")
        if (tmpTar.exists()) tmpTar.delete()

        val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(tmpTar).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var acc = 0L
                    while (input.read(buf).also { read = it } > 0) {
                        out.write(buf, 0, read)
                        acc += read
                        emit(Progress(acc, total))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        val root = File(context.filesDir, MODEL_ROOT_DIR)
        if (root.exists()) root.deleteRecursively()
        root.mkdirs()
        extractTarBz2(tmpTar, root)
        tmpTar.delete()

        check(verifyModel()) { "Model verification failed after extract" }
        Log.i(TAG, "Model extracted to ${modelDir().absolutePath}")
    }.flowOn(Dispatchers.IO)

    /** SherpaOnnxRecognizer에 넘길 최종 모델 디렉토리 (extracted subdir까지). */
    fun modelDir(): File = File(context.filesDir, "$MODEL_ROOT_DIR/$EXTRACTED_SUBDIR")

    fun verifyModel(): Boolean {
        val target = modelDir()
        if (!target.exists()) return false
        return REQUIRED_FILES.all { name ->
            target.resolve(name).let { it.exists() && it.length() > 0 }
        }
    }

    private fun extractTarBz2(tarBz2: File, destRoot: File) {
        tarBz2.inputStream().use { fis ->
            BZip2CompressorInputStream(fis.buffered()).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val outFile = File(destRoot, entry.name)
                        // tar slip 방어
                        check(outFile.canonicalPath.startsWith(destRoot.canonicalPath)) {
                            "Invalid tar entry path: ${entry.name}"
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                tar.copyTo(out, bufferSize = 64 * 1024)
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ModelDownloader"

        const val DEFAULT_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-streaming-zipformer-korean-2024-06-16.tar.bz2"

        const val MODEL_ROOT_DIR = "models/stt-ko-zipformer"
        const val EXTRACTED_SUBDIR = "sherpa-onnx-streaming-zipformer-korean-2024-06-16"

        // int8 양자화 조합 (모바일 권장, 약 133MB)
        val REQUIRED_FILES = listOf(
            "encoder-epoch-99-avg-1.int8.onnx",
            "decoder-epoch-99-avg-1.int8.onnx",
            "joiner-epoch-99-avg-1.int8.onnx",
            "tokens.txt",
        )
    }
}
