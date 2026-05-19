package com.ansim.guardian.regression

import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RegressionTestSuite {

    private lateinit var engine: RuleBasedRiskEngine

    data class TestCaseJson(
        val text: String,
        val expected_min_level: String,
        val expected_max_level: String,
        val category: String,
        val description: String
    )

    data class TestCase(
        val text: String,
        val expectedMinLevel: RiskLevel,
        val expectedMaxLevel: RiskLevel,
        val category: String,
        val description: String
    )

    @Before
    fun setUp() { engine = RuleBasedRiskEngine() }

    private fun loadTestCases(): List<TestCase> {
        val stream = javaClass.classLoader?.getResourceAsStream("test_dataset.json")
            ?: return emptyList()
        val json = stream.bufferedReader().readText()
        val type = object : TypeToken<List<TestCaseJson>>() {}.type
        val raw: List<TestCaseJson> = Gson().fromJson(json, type)
        return raw.map { r ->
            TestCase(
                text = r.text,
                expectedMinLevel = RiskLevel.valueOf(r.expected_min_level),
                expectedMaxLevel = RiskLevel.valueOf(r.expected_max_level),
                category = r.category,
                description = r.description
            )
        }
    }

    @Test
    fun `데이터셋 파일이 로드된다`() {
        val cases = loadTestCases()
        assertThat(cases).isNotEmpty()
        println("[회귀] 로드된 테스트 케이스: ${cases.size}개")
    }

    @Test
    fun `모든 데이터셋 케이스가 기대 범위 안에 있다`() = runTest {
        val cases = loadTestCases()
        val failures = mutableListOf<String>()

        cases.forEach { case ->
            val result = engine.analyze(RiskInput(case.text))
            val actual = result.riskLevel

            if (actual.ordinal < case.expectedMinLevel.ordinal) {
                failures.add("미탐 [${case.category}] ${case.description}: " +
                    "기대 ${case.expectedMinLevel}+ 실제 $actual ← '${case.text.take(40)}'")
            }
            if (actual.ordinal > case.expectedMaxLevel.ordinal) {
                failures.add("오탐 [${case.category}] ${case.description}: " +
                    "기대 ${case.expectedMaxLevel}이하 실제 $actual ← '${case.text.take(40)}'")
            }
        }

        val passRate = (cases.size - failures.size) * 100.0 / cases.size
        println("[회귀] 통과율: ${"%.1f".format(passRate)}% (${cases.size - failures.size}/${cases.size})")

        assertWithMessage("회귀 실패:\n${failures.joinToString("\n")}")
            .that(failures).isEmpty()
    }

    @Test
    fun `정상 문장 카테고리의 오탐률이 0퍼센트다`() = runTest {
        val cases = loadTestCases().filter { it.category == "normal" }
        val fps = cases.filter { case ->
            engine.analyze(RiskInput(case.text)).riskLevel.ordinal > case.expectedMaxLevel.ordinal
        }
        println("[회귀] 정상 오탐: ${fps.size}/${cases.size}")
        assertWithMessage("정상 문장 오탐: ${fps.map { it.description }}")
            .that(fps).isEmpty()
    }

    @Test
    fun `보이스피싱 카테고리의 미탐률이 0퍼센트다`() = runTest {
        val cases = loadTestCases().filter { it.category == "voice_phishing" }
        val misses = cases.filter { case ->
            engine.analyze(RiskInput(case.text)).riskLevel.ordinal < case.expectedMinLevel.ordinal
        }
        println("[회귀] 보이스피싱 미탐: ${misses.size}/${cases.size}")
        assertWithMessage("보이스피싱 미탐: ${misses.map { it.description }}")
            .that(misses).isEmpty()
    }

    @Test
    fun `원격제어 카테고리의 미탐률이 0퍼센트다`() = runTest {
        val cases = loadTestCases().filter { it.category == "remote_control" }
        val misses = cases.filter { case ->
            engine.analyze(RiskInput(case.text)).riskLevel.ordinal < case.expectedMinLevel.ordinal
        }
        println("[회귀] 원격제어 미탐: ${misses.size}/${cases.size}")
        assertWithMessage("원격제어 미탐: ${misses.map { it.description }}")
            .that(misses).isEmpty()
    }

    @Test
    fun `회귀 통계를 출력한다`() = runTest {
        val cases = loadTestCases()
        val stats = mutableMapOf<String, Triple<Int, Int, Int>>() // pass, miss, fp

        cases.forEach { case ->
            val actual = engine.analyze(RiskInput(case.text)).riskLevel
            val (p, m, f) = stats.getOrDefault(case.category, Triple(0, 0, 0))
            stats[case.category] = when {
                actual.ordinal < case.expectedMinLevel.ordinal -> Triple(p, m + 1, f)
                actual.ordinal > case.expectedMaxLevel.ordinal -> Triple(p, m, f + 1)
                else -> Triple(p + 1, m, f)
            }
        }

        println("\n[회귀 통계]")
        println("%-25s %5s %5s %5s".format("카테고리", "통과", "미탐", "오탐"))
        println("-".repeat(42))
        stats.forEach { (cat, t) ->
            println("%-25s %5d %5d %5d".format(cat, t.first, t.second, t.third))
        }
        val total = cases.size
        val totalPass = stats.values.sumOf { it.first }
        println("-".repeat(42))
        println("총계: ${total}건, 통과: ${totalPass}건 (${"%.1f".format(totalPass * 100.0 / total)}%)")
    }
}
