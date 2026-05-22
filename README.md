# 안심동행 AI (Ansim Guardian AI)

노인·지적장애인 대상 AI 금융사기 탐지 Android 앱

---

## 프로젝트 구조

```
ansim-ai/
├── android/AnsimAI/   # Android 앱 (Kotlin + Jetpack Compose)
└── backend/           # 백엔드 서버
```

---

## ⚠️ 별도 설치 필요 항목

이 저장소를 클론한 것만으로는 LLM 기능이 동작하지 않습니다.  
아래 두 가지를 **직접 설치**해야 합니다.

---

### 1. llama.cpp 소스 클론

llama.cpp 엔진은 용량 문제로 저장소에 포함되어 있지 않습니다.

```bash
cd android/AnsimAI/app/src/main/cpp
git clone https://github.com/ggml-org/llama.cpp
```

클론 후 디렉토리 구조:
```
app/src/main/cpp/
├── llama.cpp/          ← 여기에 클론
├── ansim_llm_jni.cpp
└── CMakeLists.txt
```

---

### 2. LLM 모델 파일 다운로드

모델 파일(.gguf)은 용량이 크므로 저장소에 포함되지 않습니다.  
기기 RAM에 맞는 모델을 선택해서 다운로드하세요.

#### 권장 모델 (Qwen2.5-Instruct GGUF)

| 기기 RAM | 모델 | 크기 | 다운로드 링크 |
|---|---|---|---|
| 4GB | Qwen2.5-1.5B-Instruct-Q4_K_M | ~900MB | [HuggingFace](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf) |
| 6GB+ | Qwen2.5-3B-Instruct-Q4_K_M | ~1.8GB | [HuggingFace](https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf) |

#### 터미널에서 다운로드 (1.5B 기준)

```bash
wget "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf" \
     -O ~/Downloads/qwen2.5-1.5b-instruct-q4_k_m.gguf
```

#### 기기에 모델 넣기

**방법 1 — adb (테스트/개발용):**
```bash
# /sdcard에 복사 (앱이 자동으로 찾음)
adb push ~/Downloads/qwen2.5-1.5b-instruct-q4_k_m.gguf \
    /sdcard/qwen2.5-1.5b-instruct-q4_k_m.gguf
```

**방법 2 — 앱 내부 저장소 (권장):**
```bash
# 앱 설치 및 최초 실행 후
adb push ~/Downloads/qwen2.5-1.5b-instruct-q4_k_m.gguf \
    /data/data/com.ansim.guardian/files/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf
```

> 파일명 대소문자 주의: 앱 내부 저장소는 `Qwen2.5-1.5B-Instruct-Q4_K_M.gguf` (대문자),  
> /sdcard는 소문자 모두 인식합니다.

---

## 빌드 방법

### 사전 요구사항

- Android Studio (최신 버전)
- Android NDK (Android Studio SDK Manager에서 설치)
- llama.cpp 소스 클론 완료 (위 참고)

### 빌드

```bash
cd android/AnsimAI
./gradlew assembleDebug
```

### 기기에 설치

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## LLM 없이도 동작하는 기능

| 기능 | LLM 없이 동작 여부 |
|---|---|
| 규칙 기반 사기 탐지 | ✅ 동작 |
| 알림 모니터링 | ✅ 동작 |
| SMS 분석 | ✅ 동작 |
| AI 심층 분석 (LLM) | ❌ 모델 파일 필요 |

LLM 모델이 없으면 규칙 기반 탐지만 동작하며, AI 심층 분석 기능은 비활성화됩니다.
