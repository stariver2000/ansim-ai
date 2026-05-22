package com.ansim.guardian.ai.llm

import android.content.Context
import android.util.Log
import com.ansim.guardian.ai.DeviceCapabilityChecker
import com.ansim.guardian.ai.DeviceTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LlamaCppEngine"

// llama.cpp Android JNI 래퍼
//
// 사용 방법:
// 1. llama.cpp 소스 클론: git clone https://github.com/ggml-org/llama.cpp
// 2. Android NDK로 빌드:
//    cd llama.cpp
//    mkdir build-android && cd build-android
//    cmake .. -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
//             -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26
//    make -j4
// 3. 생성된 .so 파일을 app/src/main/jniLibs/arm64-v8a/ 에 복사
// 4. 모델 파일 다운로드 후 앱 내부 저장소에 복사
//    - 4GB RAM: Qwen2.5-1.5B-Instruct-Q4_K_M.gguf (~900MB)
//    - 6GB RAM: Qwen2.5-3B-Instruct-Q4_K_M.gguf (~1.8GB)
//    - HuggingFace: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF

class LlamaCppEngine(
    private val context: Context,
    private val deviceChecker: DeviceCapabilityChecker
) : LlmEngine {

    private var contextPtr: Long = 0L
    private var modelPtr: Long = 0L
    private var isLoaded = false

    companion object {
        private var libraryLoaded = false

        fun tryLoadLibrary(): Boolean {
            if (libraryLoaded) return true
            return try {
                // llama + ggml이 ansim_llm_jni에 정적 링크됨 → 하나만 로드
                System.loadLibrary("ansim_llm_jni")
                libraryLoaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "llama.cpp 네이티브 라이브러리 없음: ${e.message}")
                false
            }
        }

        fun selectModelFile(tier: DeviceTier): String = when (tier) {
            DeviceTier.HIGH -> "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
            DeviceTier.MID  -> "qwen2.5-0.5b-instruct-q4_k_m.gguf"  // 속도 우선
            DeviceTier.LOW  -> ""
        }

        /** 0.5B 우선 탐색 (속도), 없으면 1.5B 폴백 */
        fun selectModelFileFast(tier: DeviceTier): List<String> = when (tier) {
            DeviceTier.HIGH, DeviceTier.MID -> listOf(
                "qwen2.5-0.5b-instruct-q4_k_m.gguf",   // 빠름 (~6tok/s)
                "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"    // 폴백 (~2tok/s)
            )
            DeviceTier.LOW -> emptyList()
        }
    }

    override fun isAvailable(): Boolean = isLoaded && contextPtr != 0L

    override suspend fun generate(prompt: String, maxTokens: Int): String {
        if (!isAvailable()) throw IllegalStateException("LLM이 로드되지 않았습니다")
        // JNI 블로킹 콜 — 반드시 IO 스레드에서 실행
        return withContext(Dispatchers.IO) {
            generateNative(contextPtr, prompt, maxTokens)
        }
    }

    fun loadModel(): Boolean {
        if (!tryLoadLibrary()) return false
        val tier = deviceChecker.getDeviceTier()
        if (tier == DeviceTier.LOW) return false

        val modelFileName = selectModelFile(tier)

        // 1순위: 앱 내부 저장소 (배포 시)
        // 2순위: /sdcard (테스트 시 adb push로 복사)
        // 0.5B 우선, 없으면 1.5B 폴백으로 탐색
        val candidates = selectModelFileFast(tier).flatMap { name ->
            listOf(
                context.getExternalFilesDir(null)?.absolutePath + "/$name",
                context.filesDir.absolutePath + "/$name",
                "/sdcard/$name"
            )
        }
        val modelPath = candidates.firstOrNull { java.io.File(it).exists() } ?: run {
            Log.w(TAG, "모델 파일 없음 — 탐색: $candidates")
            return false
        }
        Log.i(TAG, "사용 모델: $modelPath")

        return try {
            modelPtr = loadModelNative(modelPath)
            if (modelPtr == 0L) return false
            contextPtr = createContextNative(modelPtr)
            isLoaded = contextPtr != 0L
            Log.i(TAG, "LLM 로드 완료: $modelFileName (tier=$tier)")
            isLoaded
        } catch (e: Exception) {
            Log.e(TAG, "LLM 로드 실패: ${e.message}")
            false
        }
    }

    fun unloadModel() {
        if (contextPtr != 0L) freeContextNative(contextPtr)
        if (modelPtr != 0L) freeModelNative(modelPtr)
        contextPtr = 0L
        modelPtr = 0L
        isLoaded = false
    }

    // ── JNI 네이티브 함수 선언 ────────────────────────────────
    // app/src/main/cpp/ansim_llm_jni.cpp 에 구현
    private external fun loadModelNative(modelPath: String): Long
    private external fun createContextNative(modelPtr: Long): Long
    private external fun generateNative(contextPtr: Long, prompt: String, maxTokens: Int): String
    private external fun freeContextNative(contextPtr: Long)
    private external fun freeModelNative(modelPtr: Long)
}
