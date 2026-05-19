package com.ansim.guardian.domain.model

data class DetectedSignal(
    val matchedKeyword: String,
    val category: SignalCategory,
    val score: Int,
    val description: String,
    val isCriticalTrigger: Boolean = false
)
