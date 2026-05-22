package com.ansim.guardian.evaluation

import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 탐지 성능 종합 평가 테스트
 *
 * 규칙 엔진 기준으로 각 유형별 탐지율과 오탐율 측정.
 * LLM이 보완해야 할 취약 구간도 함께 표시.
 */
class DetectionEvaluationTest {

    private val engine = RuleBasedRiskEngine()

    // ─────────────────────────────────────────────────────────────────
    // 평가 데이터셋: (메시지, 예상 최소 위험등급, 설명)
    // ─────────────────────────────────────────────────────────────────

    data class TestCase(
        val label: String,
        val text: String,
        val expectedMin: RiskLevel,
        val isLlmOnly: Boolean = false  // 규칙 엔진이 못 잡고 LLM이 잡아야 하는 케이스
    )

    private val testCases = listOf(

        // ── 보이스피싱 (명확) ──────────────────────────────────────────
        TestCase("보이스피싱_검찰사칭_안전계좌",
            "서울중앙지검입니다. 계좌가 범죄에 연루되어 수사 중입니다. 안전계좌로 즉시 이체하세요.",
            RiskLevel.CRITICAL),

        TestCase("보이스피싱_금감원_계좌동결",
            "금융감독원입니다. 고객님 계좌가 동결될 예정입니다. 자산 보호를 위해 지금 바로 연락주세요.",
            RiskLevel.DANGER),

        TestCase("보이스피싱_경찰_수사",
            "형사입니다. 고객님 명의 대포통장이 발견됐습니다. 수사 중이니 비밀로 해주세요.",
            RiskLevel.DANGER),

        // ── 가족 사칭 ──────────────────────────────────────────────────
        TestCase("가족사칭_엄마나야_고립유도",
            "엄마 나야. 폰 고장났어. 급하니까 아빠한테 말하지 말고 50만원만 보내줘.",
            RiskLevel.CRITICAL),

        TestCase("가족사칭_새번호_상품권",
            "나 번호 바꿨어. 구글 기프트카드 30만원어치 사서 번호 찍어서 보내줘.",
            RiskLevel.DANGER),

        // ── 투자 사기 ──────────────────────────────────────────────────
        TestCase("투자사기_원금보장_VIP방",
            "VIP방에서만 공개합니다. 내부정보로 내일 상한가 확실합니다. 원금보장에 수익보장.",
            RiskLevel.DANGER),

        TestCase("투자사기_세력매집_급등",
            "세력이 지금 매집 중입니다. 오늘만 가능한 종목입니다. 지금 매수하세요.",
            RiskLevel.CAUTION),

        // ── 코인/출금 사기 ─────────────────────────────────────────────
        TestCase("코인사기_세금먼저",
            "수익이 났어요! 출금하려면 세금 먼저 입금하셔야 합니다.",
            RiskLevel.CRITICAL),

        TestCase("코인사기_보증금",
            "전액 출금 가능합니다. 보증금 100만원 입금 후 즉시 출금해드립니다.",
            RiskLevel.CRITICAL),

        // ── 원격제어 ───────────────────────────────────────────────────
        TestCase("원격제어_AnyDesk",
            "AnyDesk를 설치해서 화면 공유해주세요. 바로 처리해드리겠습니다.",
            RiskLevel.CRITICAL),

        TestCase("원격제어_TeamViewer",
            "TeamViewer 설치하시고 아이디 알려주세요. 원격으로 도와드리겠습니다.",
            RiskLevel.CRITICAL),

        // ── 스미싱 (URL 포함) ──────────────────────────────────────────
        TestCase("스미싱_택배",
            "[국제발송] 택배가 도착했습니다. http://fake-delivery.kr/confirm 에서 확인하세요.",
            RiskLevel.CAUTION),

        TestCase("스미싱_결제만료_URL",   // 사용자가 보고한 케이스
            "긴급 안내드립니다. 회원님의 결제 정보가 만료되어 자동 결제가 실패했습니다. 지금 즉시 본인 인증을 완료하지 않으면 계정이 일시 정지될 수 있습니다. 확인 페이지: https://sample-phishing-test.invalid/login",
            RiskLevel.DANGER),

        TestCase("스미싱_계정정지위협",
            "회원님 계정이 일시 정지될 예정입니다. 본인 인증을 완료해주세요: https://verify-now.fake/auth",
            RiskLevel.DANGER),

        TestCase("스미싱_청첩장",
            "청첩장을 보내드립니다. http://bit.ly/wedding-card 에서 확인하세요.",
            RiskLevel.CAUTION),

        // ── 대출 사기 ──────────────────────────────────────────────────
        TestCase("대출사기_선수수료",
            "대출 승인됐습니다. 선수수료 30만원 입금 후 3,000만원 대출 실행됩니다.",
            RiskLevel.DANGER),

        // ── LLM만 잡을 수 있는 케이스 (규칙 엔진 취약) ─────────────────
        TestCase("LLM전용_우회_스미싱",
            "고객님 카드 승인이 거절되었습니다. 본인 확인이 필요합니다. 아래 링크에서 정보를 업데이트해 주세요: https://secure-bank-update.xyz",
            RiskLevel.CAUTION,
            isLlmOnly = false),  // URL+계정위협 복합으로 규칙도 감지 가능

        TestCase("LLM전용_우회표현",
            "아버지 저 친구입니다. 급한 상황인데 잠깐 도움이 필요해요. 계좌번호로 좀 보내주실 수 있을까요? 나중에 꼭 갚겠습니다.",
            RiskLevel.CAUTION,
            isLlmOnly = true),  // '엄마나야' 같은 명시적 키워드 없음 → LLM 필요

        TestCase("LLM전용_완곡한투자권유",
            "제가 최근 꽤 높은 수익을 내고 있는 방법이 있는데 관심 있으시면 알려드릴게요. 알고 보면 어렵지 않아요.",
            RiskLevel.CAUTION,
            isLlmOnly = true),  // 명시적 키워드 없음 → LLM 필요

        // ── 안전 메시지 (오탐 방지) ───────────────────────────────────
        TestCase("안전_일상대화",
            "오늘 저녁 뭐 먹을까요? 치킨 어때요?",
            RiskLevel.SAFE),

        TestCase("안전_생일축하",
            "생일 축하해! 오늘 저녁 같이 밥 먹자.",
            RiskLevel.SAFE),

        TestCase("안전_정상송금",
            "엄마 나 용돈 좀 줘. 다음 달에 갚을게.",
            RiskLevel.SAFE),

        TestCase("안전_업무연락",
            "내일 오후 2시에 회의실에서 뵙겠습니다. 자료 준비해주세요.",
            RiskLevel.SAFE),

        TestCase("안전_인터넷쇼핑",
            "주문하신 상품이 오늘 발송되었습니다. 운송장 번호: 1234567890",
            RiskLevel.SAFE),

        TestCase("안전_정상은행알림",
            "[국민은행] 2024-01-15 12:30 [출금] 30,000원 잔액 150,230원",
            RiskLevel.SAFE)
    )

    // ─────────────────────────────────────────────────────────────────
    // 메인 평가 실행
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `전체_탐지_성능_평가`() = runTest {
        println("\n" + "=".repeat(70))
        println("  안심동행 AI — 규칙 엔진 탐지 성능 평가")
        println("=".repeat(70))

        var totalDanger = 0  // 위험 케이스 수
        var detectedDanger = 0  // 정상 탐지
        var llmOnlyMissed = 0  // 규칙 엔진이 못 잡은 LLM 전용
        var totalSafe = 0  // 안전 케이스 수
        var falsePositive = 0  // 오탐 (안전을 위험으로)

        val results = testCases.map { tc ->
            val result = engine.analyze(RiskInput(tc.text))
            val passed = result.riskLevel.ordinal >= tc.expectedMin.ordinal
            Triple(tc, result, passed)
        }

        // ── 유형별 출력 ──
        println("\n[위험 케이스 탐지 결과]")
        println("-".repeat(70))

        results.filter { (tc, _, _) -> tc.expectedMin != RiskLevel.SAFE }.forEach { (tc, result, passed) ->
            totalDanger++
            val icon = when {
                passed -> { detectedDanger++; "✅" }
                tc.isLlmOnly -> { llmOnlyMissed++; "🤖" }  // LLM이 잡아야 하는 케이스
                else -> "❌"
            }
            val signals = result.detectedSignals.joinToString(", ") { it.description.take(15) }
            println("$icon [${result.riskLevel.shortLabel}→기대:${tc.expectedMin.shortLabel}] ${tc.label}")
            if (!passed) println("   └ 감지된 신호: ${signals.ifEmpty { "없음" }}")
        }

        println("\n[안전 메시지 오탐 검사]")
        println("-".repeat(70))

        results.filter { (tc, _, _) -> tc.expectedMin == RiskLevel.SAFE }.forEach { (tc, result, _) ->
            totalSafe++
            val isFP = result.riskLevel != RiskLevel.SAFE
            if (isFP) {
                falsePositive++
                println("❌ 오탐 [${result.riskLevel.shortLabel}] ${tc.label}")
                println("   └ ${result.detectedSignals.joinToString { it.description }}")
            } else {
                println("✅ [안전] ${tc.label}")
            }
        }

        // ── 종합 점수 ──
        val detectionRate = if (totalDanger > 0) detectedDanger * 100 / totalDanger else 0
        val fpRate = if (totalSafe > 0) falsePositive * 100 / totalSafe else 0
        val ruleOnly = totalDanger - llmOnlyMissed
        val ruleDetected = detectedDanger
        val ruleDetectionRate = if (ruleOnly > 0) minOf(ruleDetected, ruleOnly) * 100 / ruleOnly else 0

        println("\n" + "=".repeat(70))
        println("  종합 평가 결과")
        println("=".repeat(70))
        println("  위험 케이스: $totalDanger 건")
        println("  ├ 규칙 엔진 탐지: $detectedDanger 건 (탐지율 ${detectionRate}%)")
        println("  ├ LLM 보완 필요: $llmOnlyMissed 건 (🤖 LLM이 잡아야 함)")
        println("  └ 미탐지: ${totalDanger - detectedDanger - llmOnlyMissed} 건 (❌ 개선 필요)")
        println()
        println("  안전 케이스: $totalSafe 건")
        println("  └ 오탐: $falsePositive 건 (오탐율 ${fpRate}%)")
        println()

        val grade = when {
            detectionRate >= 90 && fpRate == 0 -> "A+ (매우 우수)"
            detectionRate >= 80 && fpRate <= 10 -> "A  (우수)"
            detectionRate >= 70 && fpRate <= 20 -> "B  (양호)"
            detectionRate >= 60 -> "C  (보통 — LLM 보완 필요)"
            else -> "D  (미흡 — 규칙 보강 필요)"
        }
        println("  ★ 규칙 엔진 등급: $grade")
        println("=".repeat(70))
    }

    // ─────────────────────────────────────────────────────────────────
    // 개별 케이스 테스트 (CI에서 실패 케이스 명확히 표시)
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `사용자_보고_스미싱_반드시_탐지`() = runTest {
        val text = "긴급 안내드립니다. 회원님의 결제 정보가 만료되어 자동 결제가 실패했습니다. " +
                "지금 즉시 본인 인증을 완료하지 않으면 계정이 일시 정지될 수 있습니다. " +
                "확인 페이지: https://sample-phishing-test.invalid/login"

        val result = engine.analyze(RiskInput(text))

        println("\n[사용자 보고 케이스]")
        println("입력: ${text.take(60)}...")
        println("결과: ${result.riskLevel.emoji} ${result.riskLevel.label} (점수: ${result.totalScore})")
        println("감지 신호:")
        result.detectedSignals.forEach { println("  - ${it.description} (+${it.score})") }

        assert(result.riskLevel.ordinal >= RiskLevel.CAUTION.ordinal) {
            "스미싱 탐지 실패! 실제: ${result.riskLevel.label}, 기대: ${RiskLevel.CAUTION.label} 이상"
        }
        println("✅ 탐지 성공: ${result.riskLevel.label}")
    }

    @Test
    fun `오탐_정상은행알림_SAFE여야함`() = runTest {
        val text = "[국민은행] 2024-01-15 출금 30,000원 잔액 150,230원"
        val result = engine.analyze(RiskInput(text))

        println("\n[오탐 방지 체크] 정상 은행 알림")
        println("결과: ${result.riskLevel.label} (점수: ${result.totalScore})")

        assert(result.riskLevel == RiskLevel.SAFE) {
            "오탐 발생! 정상 은행 알림을 ${result.riskLevel.label}로 판정. 감지 신호: ${result.detectedSignals.map { it.description }}"
        }
        println("✅ 오탐 없음")
    }

    @Test
    fun `응답속도_20개_케이스_1초이내`() = runTest {
        val start = System.currentTimeMillis()
        testCases.forEach { tc ->
            engine.analyze(RiskInput(tc.text))
        }
        val elapsed = System.currentTimeMillis() - start

        println("\n[성능] ${testCases.size}개 케이스 처리: ${elapsed}ms (건당 평균 ${elapsed / testCases.size}ms)")
        assert(elapsed < 1000) { "응답 속도 초과: ${elapsed}ms (기준: 1000ms)" }
        println("✅ 성능 기준 통과")
    }
}
