package com.ansim.guardian.domain.model

enum class RiskLevel(
    val minScore: Int,
    val label: String,
    val shortLabel: String,
    val emoji: String
) {
    SAFE(0, "안전해요", "안전", "✅"),
    CAUTION(30, "주의가 필요해요", "주의", "⚠️"),
    DANGER(60, "위험해요", "위험", "🚨"),
    CRITICAL(80, "매우 위험해요", "매우 위험", "🆘");

    companion object {
        fun fromScore(score: Int, isCriticalOverride: Boolean = false): RiskLevel {
            if (isCriticalOverride) return CRITICAL
            return when {
                score >= 80 -> CRITICAL
                score >= 60 -> DANGER
                score >= 30 -> CAUTION
                else -> SAFE
            }
        }
    }
}
