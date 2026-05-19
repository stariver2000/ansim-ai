package com.ansim.guardian.domain.model

data class RiskResult(
    val input: RiskInput,
    val riskLevel: RiskLevel,
    val totalScore: Int,
    val detectedSignals: List<DetectedSignal>,
    val primaryCategory: SignalCategory?,
    val isCriticalOverride: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isDangerous: Boolean get() = riskLevel == RiskLevel.DANGER || riskLevel == RiskLevel.CRITICAL
    val requiresImmediateAlert: Boolean get() = riskLevel == RiskLevel.CRITICAL
}
