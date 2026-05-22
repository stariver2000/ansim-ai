// ansim_llm_jni.cpp
// llama.cpp JNI 래퍼 — 실제 연동 버전

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.cpp/include/llama.h"

#define LOG_TAG "AnsimLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ctx + sampler를 함께 관리하는 구조체
struct AnsimCtx {
    llama_context* ctx;
    llama_sampler* smpl;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ansim_guardian_ai_llm_LlamaCppEngine_loadModelNative(
    JNIEnv* env, jobject /* this */, jstring modelPath
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("모델 로드 시작: %s", path);

    llama_backend_init();

    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;  // CPU 전용 (Android)

    llama_model* model = llama_model_load_from_file(path, params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("모델 로드 실패");
        return 0L;
    }

    LOGI("모델 로드 완료");
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT jlong JNICALL
Java_com_ansim_guardian_ai_llm_LlamaCppEngine_createContextNative(
    JNIEnv* /* env */, jobject /* this */, jlong modelPtr
) {
    if (modelPtr == 0) return 0L;

    auto* model = reinterpret_cast<llama_model*>(modelPtr);

    llama_context_params params = llama_context_default_params();
    params.n_ctx     = 2048;
    params.n_batch   = 512;
    params.n_threads = 4;

    llama_context* ctx = llama_init_from_model(model, params);
    if (!ctx) {
        LOGE("컨텍스트 생성 실패");
        return 0L;
    }

    // 샘플러 체인: top-k → top-p → temp → dist(random)
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(50));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    auto* ansim_ctx = new AnsimCtx{ctx, smpl};
    LOGI("컨텍스트 생성 완료");
    return reinterpret_cast<jlong>(ansim_ctx);
}

JNIEXPORT jstring JNICALL
Java_com_ansim_guardian_ai_llm_LlamaCppEngine_generateNative(
    JNIEnv* env, jobject /* this */, jlong contextPtr, jstring prompt, jint maxTokens
) {
    if (contextPtr == 0) {
        return env->NewStringUTF("LLM을 사용할 수 없습니다.");
    }

    auto* ansim_ctx = reinterpret_cast<AnsimCtx*>(contextPtr);
    llama_context* ctx  = ansim_ctx->ctx;
    llama_sampler* smpl = ansim_ctx->smpl;

    const llama_model* model = llama_get_model(ctx);
    const llama_vocab* vocab = llama_model_get_vocab(model);

    // 프롬프트 문자열 가져오기
    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    // 토크나이즈 (BOS 포함)
    std::vector<llama_token> tokens(prompt_str.size() + 32);
    int n_tokens = llama_tokenize(
        vocab,
        prompt_str.c_str(), (int32_t)prompt_str.size(),
        tokens.data(),      (int32_t)tokens.size(),
        /* add_special */ true,
        /* parse_special */ true
    );

    if (n_tokens < 0) {
        LOGE("토크나이즈 실패 (필요 버퍼: %d)", -n_tokens);
        return env->NewStringUTF("[오류] 토크나이즈 실패");
    }
    tokens.resize(n_tokens);
    LOGI("프롬프트 토큰 수: %d", n_tokens);

    // KV 캐시 초기화
    llama_memory_t mem = llama_get_memory(ctx);
    if (mem) llama_memory_clear(mem, /* data */ false);

    // 프롬프트 prefill
    {
        llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
        if (llama_decode(ctx, batch) != 0) {
            LOGE("디코드 실패 (prefill)");
            return env->NewStringUTF("[오류] 디코드 실패");
        }
    }

    // 토큰 생성 루프
    std::string result;
    char piece_buf[256];

    for (int i = 0; i < maxTokens; i++) {
        llama_token token = llama_sampler_sample(smpl, ctx, -1);

        // EOG (end-of-generation) 토큰이면 종료
        if (llama_vocab_is_eog(vocab, token)) {
            LOGI("EOG 도달, %d 스텝에서 종료", i);
            break;
        }

        // 토큰 → 문자열 변환
        int n_piece = llama_token_to_piece(
            vocab, token,
            piece_buf, (int32_t)sizeof(piece_buf) - 1,
            /* lstrip */ 0,
            /* special */ false
        );
        if (n_piece > 0) {
            piece_buf[n_piece] = '\0';
            result += piece_buf;
        }

        // 다음 스텝 디코드
        llama_batch batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("디코드 실패 (step %d)", i);
            break;
        }
    }

    LOGI("생성 완료: %zu 바이트", result.size());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_ansim_guardian_ai_llm_LlamaCppEngine_freeContextNative(
    JNIEnv* /* env */, jobject /* this */, jlong contextPtr
) {
    if (contextPtr == 0) return;
    auto* ansim_ctx = reinterpret_cast<AnsimCtx*>(contextPtr);
    llama_sampler_free(ansim_ctx->smpl);
    llama_free(ansim_ctx->ctx);
    delete ansim_ctx;
    LOGI("컨텍스트 해제 완료");
}

JNIEXPORT void JNICALL
Java_com_ansim_guardian_ai_llm_LlamaCppEngine_freeModelNative(
    JNIEnv* /* env */, jobject /* this */, jlong modelPtr
) {
    if (modelPtr == 0) return;
    llama_model_free(reinterpret_cast<llama_model*>(modelPtr));
    llama_backend_free();
    LOGI("모델 해제 완료");
}

} // extern "C"
