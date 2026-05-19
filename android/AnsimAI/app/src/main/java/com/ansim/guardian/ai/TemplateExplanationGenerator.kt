package com.ansim.guardian.ai

import com.ansim.guardian.domain.engine.ExplanationGenerator
import com.ansim.guardian.domain.model.*

// LLM이 없거나 저사양 기기에서 동작하는 템플릿 기반 설명 생성기
// LLM 연동 시에도 LLM 실패 시 폴백으로 사용
class TemplateExplanationGenerator : ExplanationGenerator {

    override suspend fun generateExplanation(
        input: RiskInput,
        riskResult: RiskResult,
        cases: List<ScamCase>
    ): Explanation {
        val category = riskResult.primaryCategory
        val signals = riskResult.detectedSignals

        val whyDangerous = buildWhyDangerous(signals, cases)
        val doNotDo = buildDoNotDo(category, signals)
        val doNow = buildDoNow(category, riskResult.riskLevel)
        val askGuardian = buildAskGuardian(category)
        val summary = buildSummary(category, riskResult.riskLevel, cases)

        return Explanation(
            riskLevel = riskResult.riskLevel,
            summary = summary,
            whyDangerous = whyDangerous,
            doNotDo = doNotDo,
            doNow = doNow,
            askGuardian = askGuardian,
            similarCase = cases.firstOrNull(),
            isFromLlm = false
        )
    }

    private fun buildSummary(
        category: SignalCategory?,
        level: RiskLevel,
        cases: List<ScamCase>
    ): String {
        val caseName = cases.firstOrNull()?.category ?: (category?.displayName ?: "사기")
        return when (level) {
            RiskLevel.CRITICAL -> "이 대화는 $caseName 사기일 가능성이 매우 높아요. 지금 바로 보호자에게 알려야 해요."
            RiskLevel.DANGER -> "이 대화는 $caseName 와(과) 비슷한 위험한 내용이 있어요."
            RiskLevel.CAUTION -> "이 대화에 조심해야 할 내용이 있어요. 혼자 결정하지 마세요."
            RiskLevel.SAFE -> "지금은 특별히 위험한 내용이 없어요."
        }
    }

    private fun buildWhyDangerous(
        signals: List<DetectedSignal>,
        cases: List<ScamCase>
    ): List<String> {
        val reasons = signals.take(3).map { it.description + "이(가) 있어요." }
        return reasons.ifEmpty {
            cases.firstOrNull()?.let { listOf(it.explanationEasy) } ?: listOf("위험한 표현이 포함되어 있어요.")
        }
    }

    private fun buildDoNotDo(
        category: SignalCategory?,
        signals: List<DetectedSignal>
    ): List<String> {
        val common = mutableListOf<String>()
        val hasMoneySignal = signals.any { it.score >= 40 }
        val hasAuthCode = signals.any { it.matchedKeyword.contains("인증번호") }
        val hasLinkSignal = signals.any { it.category == SignalCategory.SMISHING }
        val hasRemoteControl = signals.any { it.category == SignalCategory.REMOTE_CONTROL }
        val hasInstallSignal = signals.any { it.matchedKeyword.contains("설치") }

        if (hasMoneySignal) common.add("돈을 보내지 마세요.")
        if (hasAuthCode) common.add("인증번호를 알려주지 마세요.")
        if (hasLinkSignal) common.add("링크를 누르지 마세요.")
        if (hasRemoteControl || hasInstallSignal) common.add("앱을 설치하지 마세요.")

        when (category) {
            SignalCategory.VOICE_PHISHING, SignalCategory.INSTITUTION_IMPERSONATION ->
                common.add("수사기관이라도 전화로 돈을 요구하지 않아요.")
            SignalCategory.INVESTMENT_FRAUD, SignalCategory.UNLISTED_STOCK ->
                common.add("투자 수수료나 VIP방 입금을 하지 마세요.")
            SignalCategory.CRYPTO_FRAUD ->
                common.add("출금을 위한 추가 입금은 절대 하지 마세요.")
            else -> {}
        }

        return common.ifEmpty { listOf("혼자 결정하지 마세요.", "보호자에게 먼저 확인하세요.") }
    }

    private fun buildDoNow(category: SignalCategory?, level: RiskLevel): List<String> {
        val actions = mutableListOf<String>()

        if (level == RiskLevel.CRITICAL || level == RiskLevel.DANGER) {
            actions.add("지금 바로 보호자에게 연락하세요.")
        } else {
            actions.add("보호자에게 이 내용을 보여주세요.")
        }

        when (category) {
            SignalCategory.FAMILY_IMPERSONATION ->
                actions.add("원래 가족 번호로 직접 전화해서 확인하세요.")
            SignalCategory.VOICE_PHISHING, SignalCategory.INSTITUTION_IMPERSONATION ->
                actions.add("전화를 끊고 공식 대표번호로 다시 확인하세요.")
            SignalCategory.REMOTE_CONTROL ->
                actions.add("이미 앱을 설치했다면 즉시 삭제하고 인터넷을 끄세요.")
            SignalCategory.INVESTMENT_FRAUD, SignalCategory.UNLISTED_STOCK ->
                actions.add("금융감독원(1332)에 문의해보세요.")
            SignalCategory.CRYPTO_FRAUD ->
                actions.add("더 이상 돈을 보내지 말고 경찰(112)에 신고하세요.")
            else -> actions.add("112나 금융감독원(1332)에 신고할 수 있어요.")
        }

        return actions
    }

    private fun buildAskGuardian(category: SignalCategory?): String {
        return when (category) {
            SignalCategory.FAMILY_IMPERSONATION ->
                "\"아무개가 새 번호로 돈을 달라고 하는데, 정말 맞는지 확인해줄 수 있어요?\""
            SignalCategory.VOICE_PHISHING, SignalCategory.INSTITUTION_IMPERSONATION ->
                "\"검찰이라고 전화가 왔는데, 이거 사기인지 확인해줄 수 있어요?\""
            SignalCategory.INVESTMENT_FRAUD ->
                "\"이 주식 투자 권유가 사기인지 같이 확인해줄 수 있어요?\""
            SignalCategory.CRYPTO_FRAUD ->
                "\"코인 투자에서 출금하려면 돈을 더 내야 한다는데, 이거 맞는 건가요?\""
            SignalCategory.REMOTE_CONTROL ->
                "\"원격제어 앱을 설치하라고 하는데, 이거 설치해도 되는지 확인해줄 수 있어요?\""
            else ->
                "\"이 대화가 사기인지 아닌지 같이 봐줄 수 있어요?\""
        }
    }
}
