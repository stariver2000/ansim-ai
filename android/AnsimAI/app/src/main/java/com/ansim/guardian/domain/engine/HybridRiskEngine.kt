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

        // SAFE는 서버 호출 생략 (Gemini 한도 절약)
        if (ruleResult.riskLevel == RiskLevel.SAFE) {
            Log.d(TAG, "규칙 SAFE → 서버 호출 생략")
            return ruleResult
        }

        // 2단계: CAUTION / DANGER / CRITICAL → 서버(Gemini) 정밀 분석
        // 규칙이 탐지했어도 Gemini 이유 설명 + 더 정확한 점수 제공
        Log.d(TAG, "서버 재검토 시작 (규칙: ${ruleResult.riskLevel.label})")
        return try {
            val serverResult = ServerRiskEngine.analyze(input)
            if (serverResult != null) {
                // 서버 결과의 위험도가 같거나 높으면 서버 결과 사용 (Gemini 이유 포함)
                // 서버가 더 낮게 판단해도 규칙 점수 이상으로 보정
                val finalScore = maxOf(serverResult.totalScore, ruleResult.totalScore)
                val finalLevel = if (serverResult.riskLevel.ordinal >= ruleResult.riskLevel.ordinal)
                    serverResult.riskLevel else ruleResult.riskLevel
                Log.i(TAG, "Gemini 분석 완료: ${serverResult.riskLevel.label} / score=${serverResult.totalScore}")
                serverResult.copy(totalScore = finalScore, riskLevel = finalLevel)
            } else {
                ruleResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "서버 오류 — 규칙 결과 유지: ${e.message}")
            ruleResult
        }
    }
}
