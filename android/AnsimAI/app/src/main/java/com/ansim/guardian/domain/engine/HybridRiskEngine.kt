package com.ansim.guardian.domain.engine

import android.content.Context
import android.util.Log
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel
import com.ansim.guardian.domain.model.RiskResult
import com.ansim.guardian.network.ServerRiskEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "HybridRiskEngine"

// LLM 상태 — 이제 서버 연결 상태를 나타냄
enum class LlmStatus {
    UNAVAILABLE,   // 서버 미연결
    LOADING,       // 초기화 중
    READY          // 서버 사용 가능 (항상 READY로 시작)
}

/**
 * 규칙 기반 엔진 + Gemini 서버 하이브리드 엔진
 *
 * 흐름:
 * 1. RuleBasedRiskEngine으로 즉시 분석 (항상 동작, 오프라인도 OK)
 * 2. 규칙 엔진이 SAFE → 서버(Gemini)로 재검토
 * 3. 서버 오프라인이면 규칙 결과 유지 (폴백)
 */
class HybridRiskEngine(context: Context) : RiskEngine {

    private val ruleEngine = RuleBasedRiskEngine()

    // 서버는 항상 READY (네트워크 요청이므로 별도 초기화 불필요)
    private val _llmStatus = MutableStateFlow(LlmStatus.READY)
    val llmStatus: StateFlow<LlmStatus> = _llmStatus

    override suspend fun analyze(input: RiskInput): RiskResult {
        // 1단계: 규칙 엔진 (즉시, 오프라인도 동작)
        val ruleResult = ruleEngine.analyze(input)

        // 규칙 엔진이 DANGER 이상 → 확신 있으므로 서버 불필요
        if (ruleResult.riskLevel.ordinal >= RiskLevel.DANGER.ordinal) {
            Log.d(TAG, "규칙 탐지 (DANGER+): ${ruleResult.riskLevel.label}")
            return ruleResult
        }

        // 2단계: SAFE 또는 CAUTION → 서버(Gemini)로 정밀 분석
        Log.d(TAG, "규칙 미탐/주의 → 서버 재검토 (현재: ${ruleResult.riskLevel.label})")
        return try {
            val serverResult = ServerRiskEngine.analyze(input)
            if (serverResult != null && serverResult.riskLevel.ordinal > ruleResult.riskLevel.ordinal) {
                Log.i(TAG, "서버 탐지: ${serverResult.riskLevel.label} / score=${serverResult.totalScore}")
                serverResult
            } else {
                ruleResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "서버 오류 — 규칙 결과 유지: ${e.message}")
            ruleResult
        }
    }
}
