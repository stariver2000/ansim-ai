package com.ansim.guardian.stress

import com.ansim.guardian.ai.TemplateExplanationGenerator
import com.ansim.guardian.ai.embedding.CharNgramEmbeddingEngine
import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * 성능 지연시간 테스트
 *
 * 위험 경고는 즉시(0.5초 이내) 표시되어야 함.
 * 노인이 실수하기 전에 경고가 먼저 나와야 하기 때문.
 */
class PerformanceLatencyTest {

    private lateinit var ruleEngine: RuleBasedRiskEngine
    private lateinit var embeddingEngine: CharNgramEmbeddingEngine
    private lateinit var explanationGenerator: TemplateExplanationGenerator

    @Before
    fun setUp() {
        ruleEngine = RuleBasedRiskEngine()
        embeddingEngine = CharNgramEmbeddingEngine()
        explanationGenerator = TemplateExplanationGenerator()
    }

    // ── 규칙 엔진 지연시간 (목표: 100ms 이하) ─────────────────

    @Test
    fun `규칙 엔진 분석이 100ms 이내에 완료된다`() = runTest {
        val text = "검사입니다 안전계좌로 인증번호 알려주세요 원격제어 AnyDesk"
        val start = System.currentTimeMillis()
        ruleEngine.analyze(RiskInput(text))
        val elapsed = System.currentTimeMillis() - start

        println("[성능] 규칙 엔진: ${elapsed}ms")
        assertThat(elapsed).isLessThan(100L)
    }

    @Test
    fun `짧은 문자도 100ms 이내에 분석된다`() = runTest {
        val start = System.currentTimeMillis()
        ruleEngine.analyze(RiskInput("돈 보내줘"))
        val elapsed = System.currentTimeMillis() - start
        assertThat(elapsed).isLessThan(100L)
    }

    @Test
    fun `긴 문장도 100ms 이내에 분석된다`() = runTest {
        val longText = "검사입니다 안전계좌 인증번호 AnyDesk 원금보장 손실보전 ".repeat(50)
        val start = System.currentTimeMillis()
        ruleEngine.analyze(RiskInput(longText))
        val elapsed = System.currentTimeMillis() - start

        println("[성능] 긴 문장 규칙 엔진: ${elapsed}ms")
        assertThat(elapsed).isLessThan(100L)
    }

    // ── 임베딩 지연시간 (목표: 50ms 이하) ─────────────────────

    @Test
    fun `단일 임베딩이 50ms 이내에 완료된다`() = runTest {
        val start = System.currentTimeMillis()
        embeddingEngine.embed("검사입니다 안전계좌로 이체해주세요")
        val elapsed = System.currentTimeMillis() - start

        println("[성능] 단일 임베딩: ${elapsed}ms")
        assertThat(elapsed).isLessThan(50L)
    }

    // ── 템플릿 설명 생성 지연시간 (목표: 500ms 이하) ─────────────

    @Test
    fun `템플릿 설명 생성이 500ms 이내에 완료된다`() = runTest {
        val result = ruleEngine.analyze(RiskInput("검사입니다 안전계좌"))
        val start = System.currentTimeMillis()
        explanationGenerator.generateExplanation(result.input, result, emptyList())
        val elapsed = System.currentTimeMillis() - start

        println("[성능] 템플릿 설명: ${elapsed}ms")
        assertThat(elapsed).isLessThan(500L)
    }

    // ── 전체 파이프라인 지연시간 (목표: 500ms 이하) ───────────────

    @Test
    fun `규칙엔진 + 임베딩 + 설명이 500ms 이내에 완료된다`() = runTest {
        val text = "검사입니다 안전계좌로 인증번호 AnyDesk 설치"
        val start = System.currentTimeMillis()

        val result = ruleEngine.analyze(RiskInput(text))
        embeddingEngine.embed(text)
        explanationGenerator.generateExplanation(result.input, result, emptyList())

        val elapsed = System.currentTimeMillis() - start
        println("[성능] 전체 파이프라인: ${elapsed}ms")
        assertThat(elapsed).isLessThan(500L)
    }

    // ── 처리량 (목표: 초당 1000건) ───────────────────────────

    @Test
    fun `초당 1000건 이상 분석 처리량을 달성한다`() = runTest {
        val texts = listOf(
            "오늘 날씨 좋네요",
            "검사입니다 안전계좌",
            "엄마 나야 폰 고장났어",
            "원금보장 VIP방",
            "AnyDesk 설치해주세요"
        )
        val iterations = 1000
        val start = System.currentTimeMillis()

        repeat(iterations) { i ->
            ruleEngine.analyze(RiskInput(texts[i % texts.size]))
        }

        val elapsed = System.currentTimeMillis() - start
        val throughput = iterations * 1000L / elapsed

        println("[성능] 처리량: ${throughput}건/초 (${elapsed}ms for ${iterations}건)")
        assertThat(throughput).isAtLeast(1000L)
    }

    // ── 저사양 시뮬레이션 (최악 케이스) ──────────────────────

    @Test
    fun `최악의 입력에서도 1초 이내에 완료된다`() = runTest {
        // 모든 위험 키워드가 담긴 최악의 입력
        val worstCase = """
            검사입니다 경찰청 금감원 대포통장 범죄에 연루 안전계좌 수사 중
            가족에게 말하지 마세요 인증번호 계좌 동결 자산 보호
            엄마 나야 폰 고장났어 새번호야 급해 돈 좀 보내줘 말하지 마
            상한가 세력 내부정보 VIP방 원금보장 손실보전 수익 보장 지금 매수
            상장 확정 10배 기관 물량 특별 배정 일반인은 못 사는 주식
            출금하려면 세금 먼저 보증금 입금 이 링크로 설치
            AnyDesk TeamViewer QuickSupport RustDesk 원격제어 화면 공유
            택배 조회 bit.ly 청첩장 부고
        """.trimIndent()

        val start = System.currentTimeMillis()
        val result = ruleEngine.analyze(RiskInput(worstCase))
        val elapsed = System.currentTimeMillis() - start

        println("[성능] 최악 케이스: ${elapsed}ms, 점수: ${result.totalScore}")
        assertThat(elapsed).isLessThan(1000L)
        assertThat(result.riskLevel).isEqualTo(RiskLevel.CRITICAL)
    }

    // ── 메모리 안정성 (반복 실행) ──────────────────────────

    @Test
    fun `10000회 반복 후에도 성능 저하가 없다`() = runTest {
        val text = "검사입니다 안전계좌 AnyDesk"

        // 워밍업
        repeat(100) { ruleEngine.analyze(RiskInput(text)) }

        // 측정 1
        val start1 = System.currentTimeMillis()
        repeat(1000) { ruleEngine.analyze(RiskInput(text)) }
        val elapsed1 = System.currentTimeMillis() - start1

        // 9000번 추가 실행
        repeat(9000) { ruleEngine.analyze(RiskInput(text)) }

        // 측정 2 (10000번 후)
        val start2 = System.currentTimeMillis()
        repeat(1000) { ruleEngine.analyze(RiskInput(text)) }
        val elapsed2 = System.currentTimeMillis() - start2

        println("[성능] 처음 1000회: ${elapsed1}ms, 10000회 후 1000회: ${elapsed2}ms")
        // 10000회 후에도 처음보다 2배 이상 느려지면 안 됨
        assertThat(elapsed2).isAtMost(elapsed1 * 2 + 50)
    }
}
