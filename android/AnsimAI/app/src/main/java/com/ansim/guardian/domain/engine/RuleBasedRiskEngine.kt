package com.ansim.guardian.domain.engine

import com.ansim.guardian.domain.model.*

class RuleBasedRiskEngine : RiskEngine {

    private val rules: List<RiskRule> = buildRules()

    // 계좌번호: 10자리 이상 연속 숫자 (카카오뱅크·토스뱅크 포함)
    private val accountNumberRegex = Regex("""\d{10,}""")

    // 금액: "N만원", "N천원" 형태
    private val moneyAmountRegex = Regex("""[1-9]\d*\s*만\s*원|[1-9]\d*\s*천\s*원""")

    // URL: http / https 링크 감지
    private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    override suspend fun analyze(input: RiskInput): RiskResult {
        val text = input.text.lowercase()
        val detectedSignals = mutableListOf<DetectedSignal>()
        var totalScore = 0
        var isCritical = false

        for (rule in rules) {
            val matched = rule.keywords.firstOrNull { text.contains(it.lowercase()) }
            if (matched != null) {
                detectedSignals.add(
                    DetectedSignal(
                        matchedKeyword = matched,
                        category = rule.category,
                        score = rule.score,
                        description = rule.description,
                        isCriticalTrigger = rule.isCriticalTrigger
                    )
                )
                totalScore += rule.score
                if (rule.isCriticalTrigger) isCritical = true
            }
        }

        // ── URL + 긴급성 + 계정위협 복합 감지 (스미싱 핵심 패턴) ───────────
        val hasUrl = urlRegex.containsMatchIn(input.text)
        val hasAccountThreat = listOf(
            "계정", "정지", "만료", "일시정지", "차단", "중지"
        ).count { text.contains(it) } >= 2
        val hasUrgency = listOf(
            "즉시", "긴급", "지금 바로", "지금즉시", "바로 지금", "빨리", "당장"
        ).any { text.contains(it) }
        val hasAuthRequest = listOf(
            "본인 인증", "본인인증", "인증을 완료", "인증하세요", "로그인", "개인정보"
        ).any { text.contains(it) }

        if (hasUrl && (hasAccountThreat || hasAuthRequest) && hasUrgency) {
            detectedSignals.add(
                DetectedSignal(
                    matchedKeyword = "URL+계정위협+긴급",
                    category = SignalCategory.SMISHING,
                    score = 75,
                    description = "링크 + 계정 위협 + 긴급 유도 — 스미싱 전형 패턴",
                    isCriticalTrigger = false
                )
            )
            totalScore += 75
        } else if (hasUrl && (hasAccountThreat || hasAuthRequest)) {
            // 긴급성 없어도 URL + 계정위협이면 위험
            detectedSignals.add(
                DetectedSignal(
                    matchedKeyword = "URL+계정위협",
                    category = SignalCategory.SMISHING,
                    score = 55,
                    description = "링크 + 계정 위협 감지 — 스미싱 의심",
                    isCriticalTrigger = false
                )
            )
            totalScore += 55
        } else if (hasUrl && hasUrgency) {
            // URL + 긴급성
            detectedSignals.add(
                DetectedSignal(
                    matchedKeyword = "URL+긴급",
                    category = SignalCategory.SMISHING,
                    score = 35,
                    description = "링크 + 긴급 유도 — 스미싱 의심",
                    isCriticalTrigger = false
                )
            )
            totalScore += 35
        }

        // ── 계좌번호 + 금액 + 송금 복합 감지 (직거래 사기 핵심 패턴) ──────
        // ex) "3333100399250 카카오뱅크 여기로 5만원 보내주시면 택배로 보내드리겠습니다"
        val hasAccountNumber = accountNumberRegex.containsMatchIn(input.text)
        val hasMoneyAmount   = moneyAmountRegex.containsMatchIn(input.text)
        val hasSendKeyword   = listOf("보내", "입금", "이체", "송금").any { text.contains(it) }

        if (hasAccountNumber && hasMoneyAmount && hasSendKeyword) {
            detectedSignals.add(
                DetectedSignal(
                    matchedKeyword = "계좌번호+금액+송금",
                    category = SignalCategory.MARKETPLACE_FRAUD,
                    score = 65,
                    description = "계좌번호·금액·송금 요구 동시 감지 — 직거래 선입금 사기 의심",
                    isCriticalTrigger = false
                )
            )
            totalScore += 65
        }

        // 복합 조건 즉시 위험 판정
        isCritical = isCritical || checkCriticalCombinations(detectedSignals)

        val riskLevel = RiskLevel.fromScore(totalScore, isCritical)

        val primaryCategory = detectedSignals
            .groupBy { it.category }
            .maxByOrNull { (_, signals) -> signals.sumOf { it.score } }
            ?.key

        return RiskResult(
            input = input,
            riskLevel = riskLevel,
            totalScore = totalScore,
            detectedSignals = detectedSignals.distinctBy { it.category },
            primaryCategory = primaryCategory,
            isCriticalOverride = isCritical
        )
    }

    private fun checkCriticalCombinations(signals: List<DetectedSignal>): Boolean {
        val categories = signals.map { it.category }.toSet()
        val keywords = signals.map { it.matchedKeyword.lowercase() }.toSet()

        val hasMoneyRequest = signals.any { it.score >= 40 &&
            it.category in setOf(
                SignalCategory.FAMILY_IMPERSONATION,
                SignalCategory.VOICE_PHISHING
            )
        }
        val hasSecrecy = keywords.any { it in listOf("말하지 마", "비밀로", "가족에게 말하지", "아무한테도") }
        val hasGuarantee = signals.any { it.description.contains("보장") || it.description.contains("보전") }
        val hasHighReturn = signals.any { it.description.contains("수익") || it.description.contains("급등") }

        return (hasMoneyRequest && hasSecrecy) ||
               (hasGuarantee && hasHighReturn) ||
               categories.containsAll(setOf(SignalCategory.INVESTMENT_FRAUD, SignalCategory.CRYPTO_FRAUD))
    }

    private fun buildRules(): List<RiskRule> = listOf(

        // ── 보이스피싱 ──────────────────────────────────────────
        RiskRule(listOf("검찰", "검사님", "검사입니다"), SignalCategory.VOICE_PHISHING, 35, "수사기관 사칭"),
        RiskRule(listOf("경찰청", "경찰입니다", "형사"), SignalCategory.VOICE_PHISHING, 30, "수사기관 사칭"),
        RiskRule(listOf("금감원", "금융감독원"), SignalCategory.VOICE_PHISHING, 35, "금융당국 사칭"),
        RiskRule(listOf("대포통장"), SignalCategory.VOICE_PHISHING, 40, "대포통장 언급"),
        RiskRule(listOf("범죄에 연루", "범죄 연루", "범죄연루"), SignalCategory.VOICE_PHISHING, 40, "범죄 연루 주장"),
        RiskRule(listOf("안전계좌", "안전 계좌"), SignalCategory.VOICE_PHISHING, 60, "안전계좌 요구", isCriticalTrigger = true),
        RiskRule(listOf("수사 중", "수사중", "수사를 받고"), SignalCategory.VOICE_PHISHING, 35, "수사 진행 주장"),
        RiskRule(listOf("가족에게 말하지", "아무에게도 말하지", "절대 말하면 안"), SignalCategory.VOICE_PHISHING, 50, "고립 유도"),
        RiskRule(listOf("인증번호"), SignalCategory.VOICE_PHISHING, 40, "인증번호 요구"),
        RiskRule(listOf("계좌 동결", "계좌가 동결", "통장 동결"), SignalCategory.VOICE_PHISHING, 45, "계좌 동결 위협"),
        RiskRule(listOf("자산 보호", "자산을 보호"), SignalCategory.VOICE_PHISHING, 40, "자산 보호 명목"),

        // ── 가족 사칭 ──────────────────────────────────────────
        RiskRule(listOf("폰 고장났어", "휴대폰 고장", "핸드폰 고장", "폰이 고장"), SignalCategory.FAMILY_IMPERSONATION, 35, "휴대폰 고장 사칭"),
        RiskRule(listOf("새 번호야", "새번호야", "번호 바꿨어", "번호바꿨어"), SignalCategory.FAMILY_IMPERSONATION, 40, "새 번호 사칭"),
        RiskRule(listOf("엄마 나야", "아빠 나야", "엄마나야", "아빠나야", "나야 나"), SignalCategory.FAMILY_IMPERSONATION, 45, "가족 사칭"),
        RiskRule(listOf("급해", "급한데", "빨리"), SignalCategory.FAMILY_IMPERSONATION, 15, "긴급성 조작"),
        RiskRule(listOf("돈 좀 보내줘", "돈 보내줘", "송금해줘", "이체해줘", "계좌로 보내",
                         "보내줘", "입금해줘", "입금해주세요", "입금하세요",
                         "보내야", "이체해야", "보내줄 수", "보내줄게"), SignalCategory.FAMILY_IMPERSONATION, 40, "송금 요구"),
        RiskRule(listOf("말하지 마", "아무한테도", "아빠한테는", "엄마한테는 말하지"), SignalCategory.FAMILY_IMPERSONATION, 50, "고립 유도"),
        RiskRule(listOf("상품권", "기프티콘", "구글 기프트"), SignalCategory.FAMILY_IMPERSONATION, 45, "상품권 구매 요구"),

        // ── 투자 사기 / 주식 리딩방 ────────────────────────────
        RiskRule(listOf("상한가", "따상", "따상상"), SignalCategory.INVESTMENT_FRAUD, 25, "주가 단정 예측"),
        RiskRule(listOf("세력", "세력들", "세력 매집"), SignalCategory.INVESTMENT_FRAUD, 30, "세력 언급"),
        RiskRule(listOf("내부정보", "내부 정보", "미공개 정보"), SignalCategory.INVESTMENT_FRAUD, 40, "내부 정보 주장"),
        RiskRule(listOf("VIP방", "vip방", "VIP 방", "프리미엄방"), SignalCategory.INVESTMENT_FRAUD, 30, "VIP방 유도"),
        RiskRule(listOf("원금보장", "원금 보장", "원금을 보장", "원금은 보장", "원금이 보장"), SignalCategory.INVESTMENT_FRAUD, 40, "원금보장 약속"),
        RiskRule(listOf("손실보전", "손실 보전", "손실 보상", "손실나면"), SignalCategory.INVESTMENT_FRAUD, 35, "손실 보전 약속"),
        RiskRule(listOf("수익 보장", "수익보장", "고수익 보장", "수익률 보장"), SignalCategory.INVESTMENT_FRAUD, 35, "수익 보장 약속"),
        RiskRule(listOf("오늘만", "오늘 마감", "오늘까지만"), SignalCategory.INVESTMENT_FRAUD, 20, "마감 기한 압박"),
        RiskRule(listOf("지금 매수", "바로 매수", "즉시 매수", "지금 사야"), SignalCategory.INVESTMENT_FRAUD, 25, "즉시 매수 압박"),
        RiskRule(listOf("곧 급등", "급등 예정", "급등합니다"), SignalCategory.INVESTMENT_FRAUD, 30, "급등 예측"),
        RiskRule(listOf("리딩방", "종목 추천", "종목추천"), SignalCategory.INVESTMENT_FRAUD, 25, "리딩방"),
        RiskRule(listOf("리딩비", "월정액", "입회비"), SignalCategory.INVESTMENT_FRAUD, 35, "리딩 수수료 요구"),

        // ── 비상장주식 사기 ────────────────────────────────────
        RiskRule(listOf("곧 상장", "상장 임박", "상장임박"), SignalCategory.UNLISTED_STOCK, 40, "상장 임박 주장"),
        RiskRule(listOf("상장 확정", "상장확정", "상장이 확정"), SignalCategory.UNLISTED_STOCK, 45, "상장 확정 주장"),
        RiskRule(listOf("10배", "20배", "100배", "몇 배"), SignalCategory.UNLISTED_STOCK, 35, "비현실적 수익 예측"),
        RiskRule(listOf("기관 물량", "기관물량", "기관 자금"), SignalCategory.UNLISTED_STOCK, 35, "기관 물량 언급"),
        RiskRule(listOf("특별 배정", "특별배정", "특별히 드립니다"), SignalCategory.UNLISTED_STOCK, 40, "특별 배정 주장"),
        RiskRule(listOf("일반인은 못 사는", "일반인 못사는", "아무나 못 사는", "일반인 못 사는", "일반에는 못"), SignalCategory.UNLISTED_STOCK, 40, "희소성 조작"),
        RiskRule(listOf("오늘만 가능", "오늘 마지막"), SignalCategory.UNLISTED_STOCK, 30, "마감 압박"),

        // ── 코인 / 가짜 거래소 ─────────────────────────────────
        RiskRule(listOf("출금하려면", "출금 하려면", "인출하려면"), SignalCategory.CRYPTO_FRAUD, 60, "출금 조건 추가 입금", isCriticalTrigger = true),
        RiskRule(listOf("세금 먼저", "세금을 먼저", "세금부터"), SignalCategory.CRYPTO_FRAUD, 60, "세금 명목 추가 입금", isCriticalTrigger = true),
        RiskRule(listOf("보증금 입금", "보증금을", "보증금이", "보증금", "보증금 먼저"), SignalCategory.CRYPTO_FRAUD, 60, "보증금 요구", isCriticalTrigger = true),
        RiskRule(listOf("거래소 가입", "거래소에 가입", "이 거래소"), SignalCategory.CRYPTO_FRAUD, 30, "외부 거래소 유도"),
        RiskRule(listOf("이 링크로 설치", "링크로 설치", "링크로 거래소", "링크로 앱", "apk 설치"), SignalCategory.CRYPTO_FRAUD, 55, "외부 APK 설치 유도"),
        RiskRule(listOf("수익이 났어요", "수익이 발생", "수익 확인"), SignalCategory.CRYPTO_FRAUD, 25, "허위 수익 표시"),

        // ── 원격제어 사기 ──────────────────────────────────────
        RiskRule(listOf("anydesk", "AnyDesk"), SignalCategory.REMOTE_CONTROL, 60, "원격제어 앱 설치 요구", isCriticalTrigger = true),
        RiskRule(listOf("teamviewer", "TeamViewer"), SignalCategory.REMOTE_CONTROL, 60, "원격제어 앱 설치 요구", isCriticalTrigger = true),
        RiskRule(listOf("quicksupport", "QuickSupport"), SignalCategory.REMOTE_CONTROL, 60, "원격제어 앱 설치 요구", isCriticalTrigger = true),
        RiskRule(listOf("rustdesk", "RustDesk"), SignalCategory.REMOTE_CONTROL, 60, "원격제어 앱 설치 요구", isCriticalTrigger = true),
        RiskRule(listOf("원격제어", "원격 제어", "원격으로 연결"), SignalCategory.REMOTE_CONTROL, 55, "원격제어 요구"),
        RiskRule(listOf("화면 공유", "화면공유", "화면을 공유"), SignalCategory.REMOTE_CONTROL, 50, "화면 공유 요구"),
        RiskRule(listOf("앱 설치해 주세요", "앱을 설치해", "설치해 주세요", "깔아주세요", "깔아줘", "앱 깔아"), SignalCategory.REMOTE_CONTROL, 40, "출처 불명 앱 설치 유도"),

        // ── 스미싱 ─────────────────────────────────────────────
        RiskRule(listOf("택배 조회", "택배를 확인", "배송 조회", "배송이 완료"), SignalCategory.SMISHING, 25, "택배 사칭 링크"),
        RiskRule(listOf("청첩장", "부고", "초대장"), SignalCategory.SMISHING, 25, "청첩장/부고 사칭 링크"),
        RiskRule(listOf("클릭하세요", "확인하세요", "지금 바로 확인", "지금 확인"), SignalCategory.SMISHING, 15, "링크 클릭 유도"),
        RiskRule(listOf("http://", "https://", "bit.ly", "tinyurl", "goo.gl"), SignalCategory.SMISHING, 20, "URL 링크 포함"),
        // 계정·결제 위협 패턴 (피싱 공통)
        RiskRule(listOf("계정이 정지", "계정 정지", "계정 일시 정지", "일시 정지", "계정이 일시"), SignalCategory.SMISHING, 40, "계정 정지 위협"),
        RiskRule(listOf("결제 정보가 만료", "결제 정보 만료", "카드가 만료", "결제 실패", "자동 결제가 실패"), SignalCategory.SMISHING, 35, "결제 만료·실패 사칭"),
        RiskRule(listOf("본인 인증", "본인인증을", "인증을 완료", "인증이 필요"), SignalCategory.SMISHING, 35, "본인 인증 요구"),
        RiskRule(listOf("지금 즉시", "즉시 완료", "즉시 처리", "즉시 확인", "긴급 안내"), SignalCategory.SMISHING, 25, "긴급 유도"),
        RiskRule(listOf("계정이 만료", "서비스가 정지", "서비스 이용이 제한"), SignalCategory.SMISHING, 35, "서비스 중단 위협"),

        // ── 대출 사기 ──────────────────────────────────────────
        RiskRule(listOf("저금리 대출", "무직자 대출", "신용불량자 대출"), SignalCategory.LOAN_FRAUD, 30, "불법 대출 광고"),
        RiskRule(listOf("선이자", "선수수료", "보증보험료 먼저"), SignalCategory.LOAN_FRAUD, 50, "선납금 요구"),
        RiskRule(listOf("대출 승인", "대출 확정"), SignalCategory.LOAN_FRAUD, 20, "대출 승인 사칭"),

        // ── 직거래 사기 (중고거래 선입금 사기) ───────────────────
        // 복합 감지(계좌번호+금액+송금)가 주 탐지 수단이고, 아래는 보조 키워드
        RiskRule(listOf("선입금", "선 입금"), SignalCategory.MARKETPLACE_FRAUD, 45, "선입금 요구"),
        RiskRule(listOf("입금 확인 후 발송", "입금 후 발송", "입금되면 발송", "입금 후 배송"), SignalCategory.MARKETPLACE_FRAUD, 40, "선입금 조건부 발송 약속"),
        RiskRule(listOf("입금해주시면", "보내주시면 바로", "입금하시면 바로"), SignalCategory.MARKETPLACE_FRAUD, 35, "정중한 선입금 요구"),
        RiskRule(listOf("무통장입금", "무통장 입금"), SignalCategory.MARKETPLACE_FRAUD, 20, "무통장 입금 요구"),
        RiskRule(listOf("당근이에요", "번개장터", "중고나라"), SignalCategory.MARKETPLACE_FRAUD, 10, "중고거래 플랫폼 언급")
    )
}

data class RiskRule(
    val keywords: List<String>,
    val category: SignalCategory,
    val score: Int,
    val description: String,
    val isCriticalTrigger: Boolean = false
)
