package com.ansim.guardian.financial

data class StockInfo(
    val ticker: String,
    val companyName: String,
    val market: String = "",        // KOSPI, KOSDAQ 등
    val isManaged: Boolean = false, // 관리종목 여부
    val isSuspended: Boolean = false, // 거래정지 여부
    val auditOpinion: String = "",  // 감사의견
    val recentDisclosures: List<String> = emptyList(),
    val recentNews: List<String> = emptyList(),
    val riskFlags: List<String> = emptyList()  // 위험 요소 요약
)

data class FinancialRiskResult(
    val entity: TickerExtractor.ExtractedFinancialEntity,
    val stockInfo: StockInfo?,
    val riskScore: Int,
    val riskReasons: List<String>,
    val recommendation: String
)
