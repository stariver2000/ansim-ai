package com.ansim.guardian.engine

import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * 미탐 테스트: 실제 위험한 내용을 놓치지 않는지 검증
 *
 * 미탐은 오탐보다 더 치명적 — 실제 사기를 못 잡으면 피해 발생.
 * 이 테스트는 위험 문장이 반드시 CAUTION 이상으로 분류되는지 보장.
 */
class FalseNegativeTest {

    private lateinit var engine: RuleBasedRiskEngine

    @Before
    fun setUp() { engine = RuleBasedRiskEngine() }

    // ── 핵심 위험: 절대 놓치면 안 되는 것들 ───────────────────

    @Test
    fun `안전계좌 언급은 반드시 CRITICAL이다`() = runTest {
        listOf(
            "안전계좌로 이체해주세요",
            "안전계좌에 돈을 옮겨두어야 합니다",
            "안전 계좌로 보내주세요"
        ).forEach { text ->
            val result = engine.analyze(RiskInput(text))
            assertWithMessage("안전계좌 미탐: '$text'")
                .that(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        }
    }

    @Test
    fun `특정 원격제어 앱 명은 즉시 CRITICAL이다`() = runTest {
        // AnyDesk/TeamViewer 등 명시적 앱명은 isCriticalTrigger=true
        listOf("AnyDesk 설치해주세요", "TeamViewer 설치하세요").forEach { text ->
            val result = engine.analyze(RiskInput(text))
            assertWithMessage("원격제어 미탐: '$text'")
                .that(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        }
    }

    @Test
    fun `원격제어 일반 표현은 DANGER 이상이다`() = runTest {
        // "깔아주세요" 등 일반 표현은 DANGER (critical trigger 없음)
        val result = engine.analyze(RiskInput("원격제어 앱 깔아주세요"))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)
    }

    @Test
    fun `출금 전 추가 입금 요구는 반드시 CRITICAL이다`() = runTest {
        listOf(
            "출금하려면 세금 먼저 내세요",
            "출금하기 전에 보증금 입금하세요",
            "인출하려면 수수료 먼저 납부해야 합니다"
        ).forEach { text ->
            val result = engine.analyze(RiskInput(text))
            assertWithMessage("출금 전 추가입금 미탐: '$text'")
                .that(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        }
    }

    @Test
    fun `가족사칭 + 비밀 + 송금 조합은 반드시 CRITICAL이다`() = runTest {
        listOf(
            "엄마 나야 새번호야 50만원 보내줘 아빠한테 말하지마",
            "아빠 나야 폰 고장났어 이 계좌로 급하게 보내줘 엄마한테는 말하지 마"
        ).forEach { text ->
            val result = engine.analyze(RiskInput(text))
            assertWithMessage("가족사칭 미탐: '$text'")
                .that(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        }
    }

    // ── 금융사기: 반드시 CAUTION 이상 ──────────────────────

    @Test
    fun `원금보장은 반드시 CAUTION 이상이다`() = runTest {
        // "원금 보장" 키워드가 포함된 직접 표현
        val result = engine.analyze(RiskInput("원금 보장해드립니다"))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
    }

    @Test
    fun `VIP방 결제 유도는 반드시 CAUTION 이상이다`() = runTest {
        val result = engine.analyze(RiskInput("VIP방 입장하시면 내부 정보 드립니다"))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
    }

    @Test
    fun `비상장 상장확정 주장은 반드시 CAUTION 이상이다`() = runTest {
        val result = engine.analyze(RiskInput("이 회사 상장 확정입니다 지금 사면 10배 갑니다"))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
    }

    // ── 실제 보이스피싱 스크립트 미탐 방지 ─────────────────────

    @Test
    fun `실제 사용되는 보이스피싱 패턴 5가지를 모두 탐지한다`() = runTest {
        val scripts = mapOf(
            "검사입니다 안전계좌로 이체해주세요" to RiskLevel.CRITICAL,
            "금감원입니다 인증번호 알려주세요" to RiskLevel.DANGER,
            "AnyDesk 설치해서 코드 알려주세요" to RiskLevel.CRITICAL,
            "출금하려면 세금 먼저 내세요" to RiskLevel.CRITICAL,
            "엄마 나야 새번호야 돈 보내줘 말하지마" to RiskLevel.CRITICAL
        )

        scripts.forEach { (text, minLevel) ->
            val result = engine.analyze(RiskInput(text))
            assertWithMessage("미탐: '$text' → 기대 $minLevel 이상, 실제 ${result.riskLevel}")
                .that(result.riskLevel.ordinal).isAtLeast(minLevel.ordinal)
        }
    }

    // ── 미탐률 전체 검사 ───────────────────────────────────

    @Test
    fun `위험 문장 20개 중 CAUTION 미만 미탐이 0개다`() = runTest {
        val dangerTexts = listOf(
            "안전계좌로 이체",
            "AnyDesk 설치",
            "출금하려면 세금",
            "엄마 나야 새번호 돈 보내줘",
            "원금보장 수익보장",
            "인증번호 알려주세요",
            "검사입니다 수사중",
            "가족에게 말하지 마세요",
            "보증금 먼저 입금",
            "상한가 세력 매집 VIP방",
            "상장 확정 기관 물량 10배",
            "원격제어 화면공유",
            "TeamViewer 설치",
            "이 링크로 APK 설치",
            "손실보전 원금보장",
            "폰 고장났어 돈 보내줘 말하지마",
            "수익이 났어요 출금하려면",
            "비상장 일반인 못 사는 오늘만",
            "세력 내부정보 지금 매수",
            "계좌 동결 안전계좌 이체"
        )

        val misses = dangerTexts.filter { text ->
            val result = engine.analyze(RiskInput(text))
            result.riskLevel.ordinal < RiskLevel.CAUTION.ordinal
        }

        assertWithMessage("미탐 발생 문장: $misses")
            .that(misses).isEmpty()
    }
}
