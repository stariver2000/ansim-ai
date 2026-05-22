package com.ansim.guardian.network

import android.util.Log
import com.ansim.guardian.domain.model.*

private const val TAG = "ServerRiskEngine"

/**
 * FastAPI 서버 분석 결과 → RiskResult 변환
 */
object ServerRiskEngine {

    suspend fun analyze(input: RiskInput): RiskResult? {
        val sourceType = when (input.source) {
            InputSource.SMS -> "sms"
            InputSource.NOTIFICATION_KAKAO -> "kakao"
            InputSource.MANUAL -> "unknown"
            else -> "unknown"
        }

        val result = PhishingApiClient.analyze(input.text, sourceType) ?: return null

        val riskLevel = when {
            result.riskScore >= 80 -> RiskLevel.CRITICAL
            result.riskScore >= 60 -> RiskLevel.DANGER
            result.riskScore >= 30 -> RiskLevel.CAUTION
            else -> RiskLevel.SAFE
        }

        val category = inferCategory(result.detectedSignals)

        val signals = result.reasons.mapIndexed { i, reason ->
            DetectedSignal(
                matchedKeyword = result.detectedSignals.getOrElse(i) { "서버 감지" },
                category = category,
                score = result.riskScore / maxOf(1, result.reasons.size),
                description = reason,
                isCriticalTrigger = result.label == "phishing" && riskLevel == RiskLevel.CRITICAL
            )
        }

        val llmReason = if (result.analyzedBy == "gemini" && result.reasons.isNotEmpty())
            result.reasons.first() else null

        Log.i(TAG, "서버 분석: ${result.label} / ${result.riskScore}점 / ${result.analyzedBy}")

        return RiskResult(
            input = input,
            riskLevel = riskLevel,
            totalScore = result.riskScore,
            detectedSignals = signals,
            primaryCategory = category,
            isCriticalOverride = result.label == "phishing" && result.riskScore >= 80,
            llmReason = llmReason
        )
    }

    private fun inferCategory(signals: List<String>): SignalCategory {
        val text = signals.joinToString(" ").lowercase()
        return when {
            "안전계좌" in text || "금감원" in text || "검찰" in text -> SignalCategory.VOICE_PHISHING
            "가족" in text || "새번호" in text -> SignalCategory.FAMILY_IMPERSONATION
            "투자" in text || "수익" in text || "원금" in text -> SignalCategory.INVESTMENT_FRAUD
            "코인" in text || "출금" in text || "세금" in text -> SignalCategory.CRYPTO_FRAUD
            "apk" in text || "설치" in text || "원격" in text -> SignalCategory.REMOTE_CONTROL
            "url" in text || "링크" in text || "택배" in text -> SignalCategory.SMISHING
            "대출" in text -> SignalCategory.LOAN_FRAUD
            else -> SignalCategory.SMISHING
        }
    }
}
