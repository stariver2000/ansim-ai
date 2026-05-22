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
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "HybridRiskEngine"

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

    // 모델 로드 완료 후 세팅됨 (null = LLM 비활성)
    private val llmEngine = AtomicReference<LlmRiskEngine?>(null)

    private val initScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        initScope.launch {
            val deviceChecker = DeviceCapabilityChecker(context)
            if (!deviceChecker.canRunLlm()) {
                Log.i(TAG, "기기 RAM 부족 — LLM 비활성화 (규칙 엔진만 사용)")
                return@launch
            }

            val llamaEngine = LlamaCppEngine(context, deviceChecker)
            val loaded = llamaEngine.loadModel()

            if (loaded) {
                llmEngine.set(LlmRiskEngine(llamaEngine))
                Log.i(TAG, "✅ On-device LLM 준비 완료")
            } else {
                Log.i(TAG, "모델 파일 없음 — LLM 비활성화 (규칙 엔진만 사용)")
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
