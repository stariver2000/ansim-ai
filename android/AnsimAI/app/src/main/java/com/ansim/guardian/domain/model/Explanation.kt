package com.ansim.guardian.domain.model

data class Explanation(
    val riskLevel: RiskLevel,
    val summary: String,
    val whyDangerous: List<String>,
    val doNotDo: List<String>,
    val doNow: List<String>,
    val askGuardian: String,
    val similarCase: ScamCase? = null,
    val isFromLlm: Boolean = false
)
