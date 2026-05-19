cmake_minimum_required(VERSION 3.22.1)
project("ansim_llm_jni")

# llama.cpp 소스 디렉토리 (git clone 후 경로 맞춤)
# set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# JNI 래퍼 라이브러리 빌드
add_library(
    ansim_llm_jni
    SHARED
    ansim_llm_jni.cpp
)

# llama.cpp 연동 후 아래 블록 활성화
# add_subdirectory(${LLAMA_DIR} llama_build)
# target_include_directories(ansim_llm_jni PRIVATE ${LLAMA_DIR}/include)
# target_link_libraries(ansim_llm_jni llama)

target_link_libraries(
    ansim_llm_jni
    android
    log
)
