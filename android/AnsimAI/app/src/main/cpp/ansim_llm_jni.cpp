// ansim_llm_jni.cpp
// llama.cpp JNI 래퍼
//
// 빌드 전 준비사항:
// 1. llama.cpp 소스를 이 디렉토리의 llama.cpp/ 서브디렉토리에 복사
//    git clone https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp
//
// 2. CMakeLists.txt에서 llama.cpp의 소스 파일들을 포함시켜야 함

#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "AnsimLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// llama.cpp 헤더 (소스 복사 후 활성화)
// #include "llama.cpp/include/llama.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ansim_guardian_ai_llm_LlamaCppEngine_loadModelNative(
    JNIEnv* env, jobject /* this */, jstring modelPath
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("모델 로드 시작: %s", path);

    // llama.cpp 연동 후 활성화:
    // llama_model_params params = llama_model_default_params();
    // params.n_gpu_layers = 0;  // CPU 전용
    // llama_model* model = llama_load_model_from_file(path, params);

    env->ReleaseStringUTFChars(modelPath, path);

    // 스텁: llama.cpp 연동 전까지 0 반환
    LOGE("llama.cpp 라이브러리가 없습니다. 3단계 빌드 후 활성화하세요.");
    return 0L;
}

JNIEXPORT jlong JNICALL
Java_com_ansim_guardian_ai_llm_LlamaCppEngine_createContextNative(
    JNIEnv* /* env */, jobject /* this */, jlong modelPtr
) {
    if (modelPtr == 0) return 0L;

    // llama_context_params params = llama_context_default_params();
    // params.n_ctx = 2048;
    // params.n_threads = 4;
    // llama_context* ctx = llama_new_context_with_model(
    //     reinterpret_cast<llama_model*>(modelPtr), params);
    // return reinterpret_cast<jlong>(ctx);

    return 0L;
}

JNIEXPORT jstring JNICALL
Java_com_ansim_guardian_ai_llm_LlamaCppEngine_generateNative(
    JNIEnv* env, jobject /* this */, jlong contextPtr, jstring prompt, jint maxTokens
) {
    if (contextPtr == 0) {
        return env->NewStringUTF("LLM을 사용할 수 없습니다.");
    }

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);

    // llama.cpp 추론 로직 (연동 후 활성화):
    // std::string result = run_inference(
    //     reinterpret_cast<llama_context*>(contextPtr),
    //     std::string(promptStr),
    //     maxTokens
    // );

    env->ReleaseStringUTFChars(prompt, promptStr);

    return env->NewStringUTF("LLM 스텁: llama.cpp 연동 후 실제 응답이 여기에 표시됩니다.");
}

JNIEXPORT void JNICALL
Java_com_ansim_guardian_ai_llm_LlamaCppEngine_freeContextNative(
    JNIEnv* /* env */, jobject /* this */, jlong contextPtr
) {
    if (contextPtr == 0) return;
    // llama_free(reinterpret_cast<llama_context*>(contextPtr));
}

JNIEXPORT void JNICALL
Java_com_ansim_guardian_ai_llm_LlamaCppEngine_freeModelNative(
    JNIEnv* /* env */, jobject /* this */, jlong modelPtr
) {
    if (modelPtr == 0) return;
    // llama_free_model(reinterpret_cast<llama_model*>(modelPtr));
}

} // extern "C"
