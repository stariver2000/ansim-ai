package com.ansim.guardian.financial

// 대화 텍스트에서 주식 종목명, 코드, 코인명을 추출
object TickerExtractor {

    // 주요 종목 키워드 → 코드 매핑 (확장 가능)
    private val wellKnownStocks = mapOf(
        "삼성전자" to "005930", "sk하이닉스" to "000660", "카카오" to "035720",
        "네이버" to "035420", "lg에너지솔루션" to "373220", "현대차" to "005380",
        "셀트리온" to "068270", "삼성바이오로직스" to "207940", "포스코홀딩스" to "005490",
        "기아" to "000270", "카카오뱅크" to "323410", "크래프톤" to "259960",
        "코스피" to "KOSPI", "코스닥" to "KOSDAQ"
    )

    // 주요 코인
    private val wellKnownCoins = setOf(
        "비트코인", "bitcoin", "btc",
        "이더리움", "ethereum", "eth",
        "리플", "xrp", "ripple",
        "솔라나", "solana", "sol",
        "도지코인", "dogecoin", "doge",
        "에이다", "cardano", "ada",
        "바이낸스", "bnb"
    )

    data class ExtractedFinancialEntity(
        val text: String,        // 원문에서 추출된 텍스트
        val type: EntityType,
        val ticker: String? = null  // 주식 코드 (알려진 경우)
    )

    enum class EntityType { STOCK, COIN, UNLISTED_STOCK, UNKNOWN_INVESTMENT }

    fun extract(text: String): List<ExtractedFinancialEntity> {
        val results = mutableListOf<ExtractedFinancialEntity>()
        val lowerText = text.lowercase()

        // 1. 알려진 코인 탐지
        for (coin in wellKnownCoins) {
            if (lowerText.contains(coin)) {
                results.add(ExtractedFinancialEntity(coin, EntityType.COIN))
            }
        }

        // 2. 알려진 주식 탐지
        for ((name, code) in wellKnownStocks) {
            if (lowerText.contains(name)) {
                results.add(ExtractedFinancialEntity(name, EntityType.STOCK, code))
            }
        }

        // 3. 종목 코드 패턴 탐지 (6자리 숫자)
        val tickerPattern = Regex("\\b(\\d{6})\\b")
        tickerPattern.findAll(text).forEach { match ->
            if (results.none { it.ticker == match.value }) {
                results.add(ExtractedFinancialEntity(match.value, EntityType.STOCK, match.value))
            }
        }

        // 4. 비상장주식 신호 탐지
        val unlistedSignals = listOf("비상장", "상장 예정", "상장 임박", "pre-ipo", "프리아이피오")
        if (unlistedSignals.any { lowerText.contains(it) }) {
            // 비상장 회사명 추출 시도 (간단 휴리스틱)
            val companyPattern = Regex("[가-힣a-zA-Z]{2,10}(?:주식회사|\\(주\\)|회사|그룹|홀딩스)?")
            companyPattern.find(text)?.let {
                results.add(ExtractedFinancialEntity(it.value, EntityType.UNLISTED_STOCK))
            }
        }

        // 5. 투자 관련 일반 언급
        val investmentKeywords = listOf("투자", "주식", "펀드", "etf", "채권", "선물", "옵션")
        if (investmentKeywords.any { lowerText.contains(it) } && results.isEmpty()) {
            results.add(ExtractedFinancialEntity("투자 관련 내용", EntityType.UNKNOWN_INVESTMENT))
        }

        return results.distinctBy { it.text }
    }
}
