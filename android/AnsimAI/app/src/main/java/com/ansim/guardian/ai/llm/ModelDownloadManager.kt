package com.ansim.guardian.ai.llm

import android.content.Context
import android.util.Log
import com.ansim.guardian.ai.DeviceCapabilityChecker
import com.ansim.guardian.ai.DeviceTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val TAG = "ModelDownload"

// Qwen2.5 GGUF 모델 다운로드 매니저
// HuggingFace Hub에서 직접 다운로드
class ModelDownloadManager(
    private val context: Context,
    private val deviceChecker: DeviceCapabilityChecker
) {
    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percent: Int,
        val isDone: Boolean = false,
        val error: String? = null
    )

    private val modelDir = context.filesDir

    private val modelUrls = mapOf(
        DeviceTier.HIGH to Pair(
            "Qwen2.5-3B-Instruct-Q4_K_M.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf"
        ),
        DeviceTier.MID to Pair(
            "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
        )
    )

    fun getModelInfo(): Pair<String, String>? {
        val tier = deviceChecker.getDeviceTier()
        return modelUrls[tier]
    }

    fun isModelDownloaded(): Boolean {
        val info = getModelInfo() ?: return false
        return File(modelDir, info.first).exists()
    }

    fun getModelSizeLabel(): String = when (deviceChecker.getDeviceTier()) {
        DeviceTier.HIGH -> "약 1.8GB (Qwen2.5 3B)"
        DeviceTier.MID  -> "약 900MB (Qwen2.5 1.5B)"
        DeviceTier.LOW  -> "이 기기는 LLM을 지원하지 않아요"
    }

    fun downloadModel(): Flow<DownloadProgress> = flow {
        val info = getModelInfo()
        if (info == null) {
            emit(DownloadProgress(0, 0, 0, isDone = true, error = "이 기기는 LLM을 지원하지 않아요"))
            return@flow
        }

        val (fileName, urlStr) = info
        val outputFile = File(modelDir, "$fileName.tmp")
        val finalFile = File(modelDir, fileName)

        if (finalFile.exists()) {
            emit(DownloadProgress(finalFile.length(), finalFile.length(), 100, isDone = true))
            return@flow
        }

        withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection().apply { connect() }
                val totalBytes = connection.contentLengthLong
                var downloaded = 0L

                connection.getInputStream().use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            val percent = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else 0
                            emit(DownloadProgress(downloaded, totalBytes, percent))
                        }
                    }
                }

                outputFile.renameTo(finalFile)
                emit(DownloadProgress(downloaded, totalBytes, 100, isDone = true))
                Log.i(TAG, "모델 다운로드 완료: $fileName")
            } catch (e: Exception) {
                outputFile.delete()
                emit(DownloadProgress(0, 0, 0, error = e.message))
                Log.e(TAG, "모델 다운로드 실패: ${e.message}")
            }
        }
    }

    fun deleteModel() {
        val info = getModelInfo() ?: return
        File(modelDir, info.first).delete()
    }
}
