package com.ansim.guardian.domain.engine

import android.util.Log
import com.ansim.guardian.ai.llm.LlamaCppEngine
import com.ansim.guardian.domain.model.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "LlmRiskEngine"

/**
 * LlamaCppEngine을 사용해 규칙 미탐 메시지를 재분석하는 엔진.
 *
 * 모델: Qwen2.5-Instruct (ChatML 포맷)
 * 입력: 분석할 텍스트
 * 출력: JSON → RiskResult 파싱
 */
class LlmRiskEngine(private val llamaEngine: LlamaCppEngine) : RiskEngine {

    private val gson = Gson()

    override suspend fun analyze(input: RiskInput): RiskResult {
        if (!llamaEngine.isAvailable()) {
            Log.w(TAG, "LLM 준비 안 됨 — 분석 건너뜀")
            return safeResult(input)
        }

        return try {
            val prompt = buildPrompt(input.text)
            // 15초 타임아웃 — 초과 시 SAFE 반환 (ANR 방지 + UX)
            val raw = withTimeoutOrNull(15_000L) {
                llamaEngine.generate(prompt, maxTokens = 60)
            } ?: run {
                Log.w(TAG, "LLM 타임아웃 (15초) — SAFE 반환")
                return safeResult(input)
            }
            Log.d(TAG, "LLM 원문 응답: $raw")
            parseResponse(raw, input)
        } catch (e: Exception) {
            Log.w(TAG, "LLM 분석 실패: ${e.message}")
            safeResult(input)
        }
    }

    // ── Qwen2.5 ChatML 포맷 프롬프트 ─────────────────────────────────
    private fun buildPrompt(text: String): String {
        // 입력 텍스트를 200자로 제한 (토큰 절약)
        val truncated = text.take(200)
        return """<|im_start|>system
금융사기 탐지 AI. JSON만 응답.<|im_end|>
<|im_start|>user
"$truncated"
사기유형: 보이스피싱/가족사칭/투자사기/코인사기/원격제어/스미싱/대출사기/안전
JSON: {"score":0-100,"critical":false,"category":"유형","reason":"한줄이유"}<|im_end|>
<|im_start|>assistant
""".trimIndent()
    }

    // ── JSON 파싱 → RiskResult ────────────────────────────────────────
    private fun parseResponse(raw: String, input: RiskInput): RiskResult {
        return try {
            // 모델이 JSON 앞뒤에 텍스트를 붙이는 경우 방어
            val jsonStr = extractJson(raw)
            val judgement = gson.fromJson(jsonStr, LlmJudgement::class.java)

            val score    = judgement.score.coerceIn(0, 100)
            val category = categoryFromString(judgement.category)
            val level    = RiskLevel.fromScore(score, judgement.critical)

            val signals = if (score > 20) listOf(
                DetectedSignal(
                    matchedKeyword = judgement.category,
                    category = category,
                    score = score,
                    description = judgement.reason,
                    isCriticalTrigger = judgement.critical
                )
            ) else emptyList()

            RiskResult(
                input = input,
                riskLevel = level,
                totalScore = score,
                detectedSignals = signals,
                primaryCategory = signals.firstOrNull()?.category,
                isCriticalOverride = judgement.critical,
                llmReason = if (score > 20) judgement.reason else null
            )
        } catch (e: Exception) {
            Log.w(TAG, "JSON 파싱 실패: ${e.message}")
            safeResult(input)
        }
    }

    /** 문자열에서 첫 번째 {...} 블록만 추출 */
    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end   = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) throw IllegalArgumentException("JSON 없음")
        return raw.substring(start, end + 1)
    }

    private fun categoryFromString(category: String): SignalCategory = when (category) {
        "보이스피싱" -> SignalCategory.VOICE_PHISHING
        "가족사칭"   -> SignalCategory.FAMILY_IMPERSONATION
        "투자사기"   -> SignalCategory.INVESTMENT_FRAUD
        "직거래사기" -> SignalCategory.MARKETPLACE_FRAUD
        "코인사기"   -> SignalCategory.CRYPTO_FRAUD
        "원격제어"   -> SignalCategory.REMOTE_CONTROL
        "스미싱"     -> SignalCategory.SMISHING
        "대출사기"   -> SignalCategory.LOAN_FRAUD
        else         -> SignalCategory.LLM_DETECTED
    }

    private fun safeResult(input: RiskInput) = RiskResult(
        input = input,
        riskLevel = RiskLevel.SAFE,
        totalScore = 0,
        detectedSignals = emptyList(),
        primaryCategory = null
    )

    // ── 내부 DTO ──────────────────────────────────────────────────────
    private data class LlmJudgement(
        val score: Int = 0,
        val critical: Boolean = false,
        val category: String = "안전",
        val reason: String = ""
    )
}
