package com.ansim.guardian.domain.model

data class ScamCase(
    val id: Long = 0,
    val category: String,
    val title: String,
    val pattern: String,
    val exampleText: String,
    val explanationEasy: String,
    val recommendedAction: String,
    val keywords: List<String>
)
