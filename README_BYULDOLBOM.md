# 별돌봄 v3 진화 (`byul-dolbom` 브랜치)

> 이 브랜치는 안심동행AI를 어르신용 *AI 에이전트*로 진화시키는 작업 공간.
> `main` 브랜치(클라우드 단순화 안심동행AI)와 분리해 PoC 진행 → 검증 후 fork/in-place 결정.

## 한 줄 정체

> "어르신이 '별돌봄아' 부르면, 폰 안에서 사기를 막고, 자녀에게 연락하고, 막힌 화면을 짚어주는 — 외부에 원문 안 보내는 가정 NAS-tethered AI 에이전트."

## 베이스 + 진화

| 안심동행AI (main) | 별돌봄 v3 (byul-dolbom) |
|---|---|
| 수동 사기 탐지기 | 능동 음성 AI 에이전트 |
| Gemini 클라우드 단일 | 폰 로컬 sLLM + NAS sLLM + Gemini 옵트인 |
| 사기 한 도메인 | 사기 + 통신 + 가이드 + 가족 호출 + 약 기록 |
| Notification 가로채기만 | + 음성 + TTS + Accessibility 가이드 |

## 신규 모듈 구조

```
app/src/main/
├── assets/
│   ├── agent/                          ★신규
│   │   ├── tool_catalog.json           (도구 20개 정의)
│   │   ├── byuldolbom-tool-call.gbnf   (llama.cpp grammar)
│   │   ├── nlu_system_prompt.txt
│   │   ├── few_shot_examples.json      (32개 예제)
│   │   ├── tts_copy.json               (어르신 페르소나 카피)
│   │   ├── contact_aliases.json        (가족 호칭 사전)
│   │   └── agent_constants.json        (침묵 임계·강등 키워드 등)
│   └── models/                         ★신규 (Phase 1+에서 다운로드)
│       ├── stt/                        (sherpa-onnx Zipformer-ko)
│       └── nlu/                        (Gemma 3 1B GGUF)
└── java/com/ansim/guardian/
    └── agent/                          ★신규 (interface 스켈레톤)
        ├── AgentSession.kt             (오케스트레이션 진입점)
        ├── wake/                       (Phase 10, PoC는 버튼)
        ├── stt/SpeechRecognizer.kt     (Phase 1)
        ├── nlu/IntentRouter.kt         (Phase 2)
        ├── nlu/ToolCall.kt
        ├── nlu/ToolCatalog.kt
        ├── action/ActionRunner.kt      (Phase 3)
        ├── action/SafetyStrip.kt       (위험 의도 강등)
        ├── tts/TtsCopyProvider.kt      (Phase 4)
        ├── clarify/DialogueState.kt    (Phase 6)
        └── escalate/NasPlanner.kt      (Phase 7 stub)
```

기존 코드는 *건드리지 않음*. 별돌봄 모듈은 *추가만*.

## Phase 0a 완료 항목 (2026-05-27)

- ✅ `byul-dolbom` 브랜치 생성
- ✅ `assets/agent/` 자료 파일 7개
- ✅ `agent/` Kotlin interface + data class 10개
- ✅ 본 README

## Phase 1 완료 항목 (2026-05-27)

- ✅ Gradle 의존성: `sherpa-onnx-1.13.2.aar` (libs 드롭인) + `commons-compress:1.26.2`
- ✅ NDK abiFilters: `arm64-v8a`, `armeabi-v7a`
- ✅ AndroidManifest: `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`, `ACCESS_NETWORK_STATE`
- ✅ `agent/stt/SherpaOnnxRecognizer.kt` — `SpeechRecognizer` 실 구현 (한국어 streaming Zipformer, int8)
- ✅ `agent/stt/AudioCaptureSource.kt` — 16kHz mono PCM Flow (VOICE_RECOGNITION)
- ✅ `agent/stt/ModelDownloader.kt` — GitHub Releases tar.bz2 → encoder/decoder/joiner/tokens.txt 추출
- ✅ `proguard-rules.pro` — sherpa-onnx native 메서드 보호
- ✅ `PermissionSetupScreen` — 마이크 권한 카드 추가

## ⚠️ Phase 1 사용 전 — AAR + 모델 다운로드

**1) sherpa-onnx AAR (약 60MB)** — Maven Central 공식 배포 없음, GitHub Releases에서 직접:

```bash
cd ~/ansim-ai/android/AnsimAI
wget -O app/libs/sherpa-onnx-1.13.2.aar \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-1.13.2.aar
```

**2) STT 모델 (약 200MB tar)** — 앱 첫 실행 시 `ModelDownloader.ensureModel()`이 자동 다운로드.
수동으로 미리 받으려면:

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-korean-2024-06-16.tar.bz2
# 풀고 int8 파일들만 골라 /data/data/com.ansim.guardian/files/models/stt-ko-zipformer/sherpa-onnx-streaming-zipformer-korean-2024-06-16/ 에 push
```

**3) 빌드 검증** (Android Studio 또는 명령줄):
```bash
./gradlew :app:assembleDebug
```
NDK + Kotlin 2.0 + compose-bom 2024.10.00 환경 필요. ABI 필터로 빌드 크기 ↑ 주의.

## 다음 단계 (Phase 2+)

| Phase | 작업 | 상태 |
|---|---|---|
| 1 | STT — sherpa-onnx Zipformer-ko 통합 | ✅ 완료 |
| 2 | NLU — Gemma 3 1B + GBNF grammar 강제 | 대기 |
| 3 | Action runner — L1 표준 Intent 5개 (call/scam_check/app_open/web_search/today_summary) | 대기 |
| 4 | TTS — Android 기본 TTS 0.75× | 대기 |
| 5 | Wake — 큰 마이크 버튼 (Porcupine는 Phase 10) | 대기 |
| 6 | Clarification — 침묵 = NO + 풀네임 confirm | 대기 |
| 7 | NAS escalate stub → Phase 11에서 실제 구현 | 대기 |

상세: [docs/codex-design/11-product-elderly/POC_IMPLEMENTATION_PLAN_KO.md](../Byul_NAS/docs/codex-design/11-product-elderly/POC_IMPLEMENTATION_PLAN_KO.md) (별서버 레포)

## 설계 문서 (별서버 레포 `~/Byul_NAS/docs/codex-design/11-product-elderly/`)

- `BYULDOLBOM_V3_DESIGN_KO.md` — 본체 설계
- `AGENT_TOOL_CATALOG_KO.md` — 도구 19개 + GBNF + 안전 강등
- `NLU_PROMPTS_KO.md` — 시스템 프롬프트 + few-shot 32개 + 평가셋 20개
- `TTS_COPY_DICTIONARY_KO.md` — 어르신 페르소나 카피 사전
- `CLARIFICATION_DIALOGUE_KO.md` — 다이얼로그 룰
- `POC_IMPLEMENTATION_PLAN_KO.md` — 7 phase 구현 가이드
- `NAS_PLANNER_DESIGN_KO.md` — NAS planner (EXAONE-2.4B + ReAct)
- `ANSIM_AI_MAPPING_KO.md` — 파일별 재사용/확장/신규 매핑
- `AI_AGENT_LANDSCAPE_KO.md` — 시장 빈자리 D1~D5
- `ON_DEVICE_AGENT_TECH_KO.md` — 5레이어 기술 옵션

## main 브랜치와의 관계

- **main** = 안심동행AI 정통 (Gemini 클라우드, 단순 사기 탐지)
- **byul-dolbom** = AI 에이전트 진화
- 룰 업데이트는 main → byul-dolbom으로 cherry-pick 양방향 가능 (패키지명·UI 변경 최소화로 충돌 줄임)
- 별돌봄 코드는 `agent/` 폴더에 격리 → main에 영향 X
- PoC 검증 후 *in-place 진화* / *fork* 결정 (현재는 보류)

## 외부 LLM 단서조항

별서버 안전 정책상 외부 API 원칙 금지지만, **별돌봄에 한해 옵트인 예외 허용** (사기 탐지 정확도가 어르신 재산 보호와 직결):

- 기본값 OFF
- 어르신 본인 또는 자녀가 설정에서 명시 토글 ON
- 사기 탐지 LLM 한 가지 용도만
- 마스킹 필수 (`services/masker.py`)
- audit_log 모든 호출 기록
- 기존 Gemini 백엔드는 옵트인 어댑터로 유지

---

🛡️ 별돌봄 v3 — 2026-05-27 설계 완료, Phase 0a + Phase 1 STT 완료. AAR/모델 드롭인 후 빌드 검증 필요. Phase 2 NLU 대기.
