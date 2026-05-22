package com.ansim.guardian.domain.engine

import android.content.Context
import android.util.Log
import com.ansim.guardian.ai.DeviceCapabilityChecker
import com.ansim.guardian.ai.llm.LlamaCppEngine
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel
import com.ansim.guardian.domain.model.RiskResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "HybridRiskEngine"

enum class LlmStatus {
    UNAVAILABLE,   // 모델 파일 없음 or RAM 부족
    LOADING,       // 로드 + pre-warm 중
    READY          // 사용 가능
}

/**
 * 규칙 기반 엔진 + On-device LLM 폴백 하이브리드 엔진
 *
 * 흐름:
 * 1. RuleBasedRiskEngine으로 즉시 분석 (항상 동작)
 * 2. 결과가 SAFE이고 LLM 준비가 됐으면 → LlmRiskEngine으로 재검토
 * 3. LLM이 위험 감지 시 LLM 결과 반환, 아니면 규칙 결과 유지
 *
 * LLM 초기화는 백그라운드에서 비동기로 진행.
 * 모델 파일이 없거나 기기 RAM이 부족하면 LLM 없이 규칙만 사용.
 */
class HybridRiskEngine(context: Context) : RiskEngine {

    private val ruleEngine = RuleBasedRiskEngine()
    private val llmEngine = AtomicReference<LlmRiskEngine?>(null)
    private val initScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // UI에서 구독 가능한 로딩 상태
    private val _llmStatus = MutableStateFlow(LlmStatus.LOADING)
    val llmStatus: StateFlow<LlmStatus> = _llmStatus

    init {
        initScope.launch {
            val start = System.currentTimeMillis()
            val deviceChecker = DeviceCapabilityChecker(context)

            if (!deviceChecker.canRunLlm()) {
                Log.i(TAG, "기기 RAM 부족 — LLM 비활성화")
                _llmStatus.value = LlmStatus.UNAVAILABLE
                return@launch
            }

            val llamaEngine = LlamaCppEngine(context, deviceChecker)
            val loaded = llamaEngine.loadModel()  // 내부에서 pre-warm까지 실행

            if (loaded) {
                llmEngine.set(LlmRiskEngine(llamaEngine))
                val elapsed = System.currentTimeMillis() - start
                Log.i(TAG, "✅ On-device LLM 준비 완료 (${elapsed}ms, pre-warm 포함)")
                _llmStatus.value = LlmStatus.READY
            } else {
                Log.i(TAG, "모델 파일 없음 — LLM 비활성화")
                _llmStatus.value = LlmStatus.UNAVAILABLE
            }
        }
    }

    override suspend fun analyze(input: RiskInput): RiskResult {
        // 1단계: 규칙 엔진 (항상 즉시 실행)
        val ruleResult = ruleEngine.analyze(input)

        // 규칙 엔진이 위험 감지 → LLM 호출 불필요
        if (ruleResult.riskLevel != RiskLevel.SAFE) {
            Log.d(TAG, "규칙 탐지: ${ruleResult.riskLevel.label} (score=${ruleResult.totalScore})")
            return ruleResult
        }

        // 2단계: LLM 폴백 (SAFE 판정 + LLM 준비된 경우만)
        val llm = llmEngine.get() ?: return ruleResult

        return try {
            Log.d(TAG, "규칙 미탐 → LLM 재검토")
            val llmResult = llm.analyze(input)

            if (llmResult.riskLevel != RiskLevel.SAFE) {
                Log.i(TAG, "LLM 탐지: ${llmResult.riskLevel.label} / ${llmResult.primaryCategory?.displayName}")
                llmResult
            } else {
                ruleResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM 분석 실패 — 규칙 결과 유지: ${e.message}")
            ruleResult
        }
    }

    val isLlmReady: Boolean get() = llmEngine.get() != null
}
