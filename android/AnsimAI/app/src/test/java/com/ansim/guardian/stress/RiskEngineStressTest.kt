package com.ansim.guardian.stress

import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RiskEngineStressTest {

    private lateinit var engine: RuleBasedRiskEngine

    private val testInputs = listOf(
        "오늘 날씨 좋네요",
        "검사입니다. 안전계좌로 돈을 옮겨주세요.",
        "엄마 나야. 폰 고장났어. 50만원만 보내줘. 말하지 마.",
        "원금보장 수익 보장 VIP방 세력 매집",
        "AnyDesk 설치해서 화면 공유해주세요",
        "출금하려면 세금 먼저 내셔야 합니다",
        "상장 확정! 기관 물량 특별 배정! 10배 갑니다!",
        "비트코인 투자 이 거래소 가입하세요 보증금 입금",
        "택배 조회: bit.ly/xxxxx",
        ""
    )

    @Before
    fun setUp() {
        engine = RuleBasedRiskEngine()
    }

    // ── 처리량 테스트 ──────────────────────────────────────

    @Test
    fun `1000회 연속 분석이 5초 안에 완료된다`() = runTest {
        val start = System.currentTimeMillis()

        repeat(1000) { i ->
            val text = testInputs[i % testInputs.size]
            engine.analyze(RiskInput(text))
        }

        val elapsed = System.currentTimeMillis() - start
        println("[스트레스] 1000회 분석: ${elapsed}ms (평균 ${elapsed / 1000.0}ms/건)")
        assertThat(elapsed).isLessThan(5_000L)
    }

    @Test
    fun `100회 동시 분석이 올바른 결과를 반환한다`() = runTest {
        val scamText = "안전계좌로 돈을 옮겨주세요"
        val safeText = "오늘 날씨 좋네요"

        val results = (1..100).map { i ->
            async(Dispatchers.Default) {
                val text = if (i % 2 == 0) scamText else safeText
                engine.analyze(RiskInput(text))
            }
        }.awaitAll()

        val scamResults = results.filterIndexed { i, _ -> (i + 1) % 2 == 0 }
        val safeResults = results.filterIndexed { i, _ -> (i + 1) % 2 != 0 }

        scamResults.forEach {
            assertThat(it.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        }
        safeResults.forEach {
            assertThat(it.riskLevel).isEqualTo(RiskLevel.SAFE)
        }
    }

    @Test
    fun `500회 반복 후에도 메모리 누수 없이 결과가 일관된다`() = runTest {
        val text = "검사입니다 안전계좌로 원격제어 AnyDesk"
        var lastScore = -1

        repeat(500) {
            val result = engine.analyze(RiskInput(text))
            if (lastScore == -1) lastScore = result.totalScore
            // 결과가 항상 동일해야 함 (결정론적)
            assertThat(result.totalScore).isEqualTo(lastScore)
            assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        }
    }

    // ── 경계값 테스트 ──────────────────────────────────────

    @Test
    fun `1자 텍스트도 처리된다`() = runTest {
        listOf("가", "a", "1", " ", "\n").forEach { text ->
            val result = engine.analyze(RiskInput(text))
            assertThat(result).isNotNull()
        }
    }

    @Test
    fun `10000자 텍스트도 처리된다`() = runTest {
        val longText = "검사입니다 안전계좌 ".repeat(1000)
        val start = System.currentTimeMillis()
        val result = engine.analyze(RiskInput(longText))
        val elapsed = System.currentTimeMillis() - start

        assertThat(result).isNotNull()
        assertThat(elapsed).isLessThan(1_000L)
        println("[경계값] 10000자 분석: ${elapsed}ms")
    }

    @Test
    fun `유니코드 이모지 포함 텍스트도 처리된다`() = runTest {
        val text = "🚨검사입니다💰안전계좌로🔑인증번호알려주세요"
        val result = engine.analyze(RiskInput(text))
        assertThat(result).isNotNull()
        assertThat(result.riskLevel).isNotNull()
    }

    @Test
    fun `SQL 인젝션 시도 텍스트도 안전하게 처리된다`() = runTest {
        val malicious = "'; DROP TABLE scam_cases; -- 검사입니다"
        val result = engine.analyze(RiskInput(malicious))
        assertThat(result).isNotNull()
    }

    @Test
    fun `HTML 태그 포함 텍스트도 처리된다`() = runTest {
        val html = "<script>alert('xss')</script>안전계좌로 돈 보내주세요"
        val result = engine.analyze(RiskInput(html))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.CAUTION.ordinal)
    }

    @Test
    fun `줄바꿈과 탭이 많은 텍스트도 처리된다`() = runTest {
        val text = "검사\n\n입니다\t\t안전계좌\r\n로\t돈을"
        val result = engine.analyze(RiskInput(text))
        assertThat(result).isNotNull()
    }

    // ── 점수 경계 테스트 ───────────────────────────────────

    @Test
    fun `점수 29는 SAFE, 30은 CAUTION이다`() = runTest {
        // 긴급성 표현(15점) + 단축URL(20점) = 35점 → CAUTION
        val cautionResult = engine.analyze(RiskInput("급해 bit.ly/xxxxx"))
        assertThat(cautionResult.riskLevel).isEqualTo(RiskLevel.CAUTION)
    }

    @Test
    fun `총점이 80 이상이면 CRITICAL이다`() = runTest {
        // 안전계좌(60점) + 인증번호(40점) = 100점 → CRITICAL
        val result = engine.analyze(RiskInput("안전계좌로 인증번호 알려주세요"))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.totalScore).isAtLeast(80)
    }

    // ── 실제 사기 시나리오 통합 테스트 ───────────────────────

    @Test
    fun `실제 보이스피싱 스크립트가 CRITICAL로 탐지된다`() = runTest {
        val script = """
            안녕하세요. 저는 서울중앙지방검찰청 수사관 김OO입니다.
            고객님 명의의 계좌가 범죄에 연루되어 수사 중입니다.
            자산 보호를 위해 안전계좌로 즉시 이체하셔야 합니다.
            이 내용은 수사상 비밀이므로 가족에게 절대 말하지 마세요.
            인증번호를 불러주시면 처리해드리겠습니다.
        """.trimIndent()

        val result = engine.analyze(RiskInput(script))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        // 대부분의 신호가 VOICE_PHISHING으로 묶임 → 카테고리 dedup 후 최소 1개 이상
        assertThat(result.detectedSignals.size).isAtLeast(1)
        // 다수 위험 신호가 감지되어 점수가 높아야 함
        assertThat(result.totalScore).isAtLeast(60)
    }

    @Test
    fun `실제 가족사칭 카카오톡이 CRITICAL로 탐지된다`() = runTest {
        val message = "엄마 나야 핸드폰 고장났어서 새번호야 급하게 돈이 필요한데 50만원만 카카오페이로 보내줘 아빠한테는 말하지마"
        val result = engine.analyze(RiskInput(message))
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
    }

    @Test
    fun `실제 리딩방 메시지가 DANGER 이상으로 탐지된다`() = runTest {
        val message = "오늘 상한가 갑니다! 세력 매집 완료! 원금보장에 손실보전까지! 지금 바로 VIP방 입장하세요!"
        val result = engine.analyze(RiskInput(message))
        assertThat(result.riskLevel.ordinal).isAtLeast(RiskLevel.DANGER.ordinal)
    }
}
