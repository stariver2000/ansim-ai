package com.ansim.guardian.domain.model

enum class SignalCategory(val displayName: String) {
    VOICE_PHISHING("보이스피싱"),
    FAMILY_IMPERSONATION("가족 사칭"),
    INVESTMENT_FRAUD("투자 사기"),
    UNLISTED_STOCK("비상장주식 사기"),
    CRYPTO_FRAUD("코인 투자 사기"),
    REMOTE_CONTROL("원격제어 사기"),
    SMISHING("스미싱"),
    LOAN_FRAUD("대출 사기"),
    INSTITUTION_IMPERSONATION("기관 사칭"),
    MARKETPLACE_FRAUD("직거래 사기"),  // 중고거래·직거래 선입금 사기
    LLM_DETECTED("AI 탐지")           // 규칙 미탐 → LLM이 잡은 경우
}
