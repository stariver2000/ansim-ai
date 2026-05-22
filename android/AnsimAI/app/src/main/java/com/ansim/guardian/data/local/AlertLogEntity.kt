package com.ansim.guardian.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 위험 감지 기록 — CAUTION 이상일 때 자동 저장 */
@Entity(tableName = "alert_log")
data class AlertLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val riskLevel: String,       // "CAUTION" | "DANGER" | "CRITICAL"
    val riskEmoji: String,       // "⚠️" | "🚨" | "🆘"
    val category: String,        // 카테고리 표시명 (e.g. "보이스피싱")
    val sourceLabel: String,     // "문자", "카카오톡", "직접 입력"
    val contentPreview: String,  // 원문 앞 120자
    val detectionMethod: String, // "규칙 기반" | "AI 분석"
    val reason: String           // 감지 이유 한 줄
)
