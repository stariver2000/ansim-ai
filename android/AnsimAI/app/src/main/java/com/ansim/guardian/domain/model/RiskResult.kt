package com.ansim.guardian.domain.model

data class RiskResult(
    val input: RiskInput,
    val riskLevel: RiskLevel,
    val totalScore: Int,
    val detectedSignals: List<DetectedSignal>,
    val primaryCategory: SignalCategory?,
    val isCriticalOverride: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    /** LLM이 직접 감지한 경우 이유 한 줄 (규칙 엔진만 사용 시 null) */
    val llmReason: String? = null
) {
    val isDangerous: Boolean get() = riskLevel == RiskLevel.DANGER || riskLevel == RiskLevel.CRITICAL
    val requiresImmediateAlert: Boolean get() = riskLevel == RiskLevel.CRITICAL
    val isLlmDetected: Boolean get() = llmReason != null
}
