cmake_minimum_required(VERSION 3.22.1)
project("ansim_llm_jni")

set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# ── llama.cpp 빌드 옵션 ──────────────────────────────────────
# 테스트/예제/서버 제외 (빌드 속도 향상)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)

# Android에서 불필요한 백엔드 비활성화
set(GGML_METAL  OFF CACHE BOOL "" FORCE)
set(GGML_CUDA   OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# llama + ggml을 ansim_llm_jni에 정적 링크
# → libansim_llm_jni.so 하나만 배포하면 됨
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama_build)

# ── JNI 래퍼 라이브러리 ───────────────────────────────────────
add_library(
    ansim_llm_jni
    SHARED
    ansim_llm_jni.cpp
)

target_include_directories(ansim_llm_jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
)

target_link_libraries(
    ansim_llm_jni
    llama
    android
    log
)
