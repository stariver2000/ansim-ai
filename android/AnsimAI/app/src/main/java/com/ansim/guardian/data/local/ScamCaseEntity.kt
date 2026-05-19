package com.ansim.guardian.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scam_cases")
data class ScamCaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val title: String,
    val pattern: String,
    val exampleText: String,
    val explanationEasy: String,
    val recommendedAction: String,
    val keywords: String  // JSON 배열 문자열로 저장
)
