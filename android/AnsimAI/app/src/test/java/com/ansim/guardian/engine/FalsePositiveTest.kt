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
 * 오탐 테스트: 정상 대화를 사기로 잘못 분류하지 않는지 검증
 *
 * 오탐이 많으면 사용자가 앱을 신뢰하지 않고 꺼버림.
 * 이 테스트는 CRITICAL/DANGER로 잘못 분류되는 케이스를 방지.
 */
class FalsePositiveTest {

    private lateinit var engine: RuleBasedRiskEngine

    @Before
    fun setUp() { engine = RuleBasedRiskEngine() }

    // ── 일반 가족 대화 ─────────────────────────────────────

    @Test
    fun `폰 고장 언급만으로는 CRITICAL이 되지 않는다`() = runTest {
        val result = engine.analyze(RiskInput("엄마 나 폰 고장났는데 집에 가서 고칠게"))
        assertThat(result.riskLevel).isNotEqualTo(RiskLevel.CRITICAL)
    }

    @Test
    fun `생일선물 비밀은 사기가 아니다`() = runTest {
        val result = engine.analyze(RiskInput("아빠한테 말하지 말고 생일선물 준비하자"))
        assertWithMessage("생일선물 비밀은 투자사기 아님")
            .that(result.riskLevel.ordinal).isAtMost(RiskLevel.CAUTION.ordinal)
    }

    @Test
    fun `단순 가족 대화는 위험하지 않다`() = runTest {
        listOf(
            "엄마 오늘 저녁 뭐 먹어요?",
            "아빠 언제 들어와요?",
            "할머니 잘 지내세요?",
            "오빠 내일 시간 있어?"
        ).forEach { text ->
            val result = engine.analyze(RiskInput(text))
            assertWithMessage("일반 가족 대화 '$text' 가 오탐됨")
                .that(result.riskLevel).isEqualTo(RiskLevel.SAFE)
        }
    }

    // ── 정상 금융 대화 ──────────────────────────────────────

    @Test
    fun `일반 주식 뉴스 언급은 위험하지 않다`() = runTest {
        val result = engine.analyze(RiskInput("삼성전자 실적이 좋아졌다는 뉴스가 있네"))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.SAFE)
    }

    @Test
    fun `단순 코인 의견 묻기는 SAFE다`() = runTest {
        val result = engine.analyze(RiskInput("비트코인 요즘 어떻게 생각해?"))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.SAFE)
    }

    @Test
    fun `저금리 정보 탐색은 SAFE 또는 CAUTION이다`() = runTest {
        val result = engine.analyze(RiskInput("저금리 대출 어디서 알아볼 수 있어?"))
        assertThat(result.riskLevel.ordinal).isAtMost(RiskLevel.CAUTION.ordinal)
    }

    @Test
    fun `본인이 인증번호 요청하는 건 낮은 위험이다`() = runTest {
        val result = engine.analyze(RiskInput("카카오 인증번호가 안 와서 다시 요청했어"))
        assertWithMessage("본인 인증번호 요청이 오탐됨")
            .that(result.riskLevel.ordinal).isAtMost(RiskLevel.CAUTION.ordinal)
    }

    @Test
    fun `은행 앱 자체 안내 문자는 낮은 위험이다`() = runTest {
        val result = engine.analyze(RiskInput("국민은행 앱 이용 고객님 보안 업데이트 안내드립니다"))
        assertThat(result.riskLevel.ordinal).isAtMost(RiskLevel.CAUTION.ordinal)
    }

    // ── 숫자/코드 포함 정상 대화 ──────────────────────────────

    @Test
    fun `일반 숫자 포함 대화는 SAFE다`() = runTest {
        listOf(
            "오늘 기온이 30도야",
            "3시간 후에 만나자",
            "100번 버스 타면 돼"
        ).forEach { text ->
            val result = engine.analyze(RiskInput(text))
            assertWithMessage("숫자 포함 일반 대화 '$text' 오탐")
                .that(result.riskLevel).isEqualTo(RiskLevel.SAFE)
        }
    }

    // ── 경계값: CAUTION 이상 되어선 안 되는 케이스 ─────────────

    @Test
    fun `투자 정보 공유는 CAUTION을 넘지 않는다`() = runTest {
        val result = engine.analyze(RiskInput("이 ETF 요즘 수익률 좋다고 하더라"))
        assertThat(result.riskLevel.ordinal).isAtMost(RiskLevel.CAUTION.ordinal)
    }

    @Test
    fun `택배 조회 안내는 링크 없으면 낮은 위험이다`() = runTest {
        val result = engine.analyze(RiskInput("택배 도착 예정이에요. 집에 계세요."))
        assertThat(result.riskLevel.ordinal).isAtMost(RiskLevel.CAUTION.ordinal)
    }

    // ── 오탐률 전체 검사 ───────────────────────────────────

    @Test
    fun `정상 문장 20개 중 DANGER 이상 오탐이 0개다`() = runTest {
        val normalTexts = listOf(
            "오늘 날씨 좋네요",
            "점심 뭐 먹을까요",
            "삼성전자 어때요",
            "엄마 오늘 저녁 뭐예요",
            "아빠 퇴근했어요",
            "생일 축하해",
            "오늘 영화 볼까?",
            "주말에 여행 가자",
            "새해 복 많이 받으세요",
            "감사합니다 잘 받았어요",
            "내일 회의 몇 시예요",
            "택배 도착했나요",
            "은행 몇 시까지 해요",
            "버스 언제 와요",
            "밥 먹었어요",
            "잘 자요",
            "안녕하세요",
            "감기 조심하세요",
            "주문 완료되었습니다",
            "배달 완료되었습니다"
        )

        val falsePositives = normalTexts.filter { text ->
            val result = engine.analyze(RiskInput(text))
            result.riskLevel.ordinal >= RiskLevel.DANGER.ordinal
        }

        assertWithMessage("오탐 발생 문장: $falsePositives")
            .that(falsePositives).isEmpty()
    }
}
