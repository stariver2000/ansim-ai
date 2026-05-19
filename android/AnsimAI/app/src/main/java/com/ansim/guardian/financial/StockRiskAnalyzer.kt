package com.ansim.guardian.financial

import android.util.Log
import com.ansim.guardian.domain.model.RiskResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val TAG = "StockRiskAnalyzer"

// 대화 분석 결과 + 종목 재무/공시 데이터를 결합해서 금융 위험도 계산
class StockRiskAnalyzer(private val dartClient: DartApiClient) {

    // 관리종목 캐시 (앱 번들에 포함하거나 주기적 업데이트)
    // 실제 서비스에서는 KRX API 또는 자체 서버에서 주기적으로 업데이트
    private val managedStockCache = setOf<String>(
        // 예시: "000000" 형식의 종목 코드
    )

    suspend fun analyze(
        text: String,
        conversationRiskResult: RiskResult
    ): List<FinancialRiskResult> = coroutineScope {

        val entities = TickerExtractor.extract(text)
        if (entities.isEmpty()) return@coroutineScope emptyList<FinancialRiskResult>()

        entities.map { entity ->
            async { analyzeEntity(entity, conversationRiskResult) }
        }.map { it.await() }
    }

    private suspend fun analyzeEntity(
        entity: TickerExtractor.ExtractedFinancialEntity,
        conversationRisk: RiskResult
    ): FinancialRiskResult {
        var score = 0
        val reasons = mutableListOf<String>()

        when (entity.type) {
            TickerExtractor.EntityType.STOCK -> {
                val stockInfo = entity.ticker?.let { dartClient.getCompanyInfo(it) }

                if (stockInfo != null) {
                    if (stockInfo.isManaged) {
                        score += 50
                        reasons.add("관리종목으로 지정된 회사예요")
                    }
                    if (stockInfo.isSuspended) {
                        score += 45
                        reasons.add("현재 거래가 정지된 주식이에요")
                    }
                    if (stockInfo.auditOpinion.contains("비적정") || stockInfo.auditOpinion.contains("의견거절")) {
                        score += 60
                        reasons.add("감사 의견이 좋지 않아요 (${stockInfo.auditOpinion})")
                    }
                    stockInfo.riskFlags.forEach {
                        score += 25
                        reasons.add(it)
                    }
                }

                // 대화 위험도와 결합
                if (conversationRisk.totalScore >= 60) {
                    score += 30
                    reasons.add("위험한 투자 권유 방식과 함께 언급되었어요")
                }

                return FinancialRiskResult(
                    entity = entity,
                    stockInfo = stockInfo,
                    riskScore = score,
                    riskReasons = reasons,
                    recommendation = buildRecommendation(score, entity.type, reasons)
                )
            }

            TickerExtractor.EntityType.COIN -> {
                score += 20  // 코인 자체의 기본 위험도
                reasons.add("암호화폐는 가격 변동이 매우 커요")

                if (conversationRisk.totalScore >= 40) {
                    score += 40
                    reasons.add("위험한 코인 권유 패턴이 감지되었어요")
                }

                return FinancialRiskResult(
                    entity = entity,
                    stockInfo = null,
                    riskScore = score,
                    riskReasons = reasons,
                    recommendation = buildRecommendation(score, entity.type, reasons)
                )
            }

            TickerExtractor.EntityType.UNLISTED_STOCK -> {
                score += 50  // 비상장주식 기본 고위험
                reasons.add("비상장주식은 실체 확인이 어렵고 팔기도 어려워요")
                reasons.add("상장 확정은 대부분 거짓말이에요")

                return FinancialRiskResult(
                    entity = entity,
                    stockInfo = null,
                    riskScore = score,
                    riskReasons = reasons,
                    recommendation = "개인 계좌로 입금하라고 하면 절대 하지 마세요. 보호자와 함께 금융감독원(1332)에 문의하세요."
                )
            }

            else -> {
                return FinancialRiskResult(
                    entity = entity,
                    stockInfo = null,
                    riskScore = conversationRisk.totalScore / 2,
                    riskReasons = listOf("투자 관련 내용이 포함되어 있어요"),
                    recommendation = "투자 전 보호자와 함께 확인하세요."
                )
            }
        }
    }

    private fun buildRecommendation(
        score: Int,
        type: TickerExtractor.EntityType,
        reasons: List<String>
    ): String {
        return when {
            score >= 80 -> "이 투자는 매우 위험해요. 절대 혼자 결정하지 마세요. 보호자에게 즉시 알리고 금융감독원(1332)에 신고하세요."
            score >= 60 -> "이 투자는 위험 신호가 있어요. 보호자와 함께 확인하기 전까지 돈을 보내지 마세요."
            score >= 30 -> "투자 전 공시 자료와 재무 상태를 확인하세요. 혼자 결정하지 마세요."
            else -> "이 투자에 대해 더 많은 정보를 확인한 후 결정하세요."
        }
    }
}
