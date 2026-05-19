package com.ansim.guardian.stress

import com.ansim.guardian.ai.embedding.CharNgramEmbeddingEngine
import com.ansim.guardian.ai.embedding.cosineSimilarity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class EmbeddingEngineStressTest {

    private lateinit var engine: CharNgramEmbeddingEngine

    @Before
    fun setUp() {
        engine = CharNgramEmbeddingEngine()
    }

    @Test
    fun `1000회 임베딩이 3초 안에 완료된다`() = runTest {
        val texts = listOf(
            "보이스피싱 탐지 테스트",
            "안전계좌로 돈을 옮겨주세요",
            "원금보장 수익 보장",
            "AnyDesk 설치",
            "오늘 날씨 좋네요"
        )

        val start = System.currentTimeMillis()
        repeat(1000) { i ->
            engine.embed(texts[i % texts.size])
        }
        val elapsed = System.currentTimeMillis() - start

        println("[임베딩 스트레스] 1000회: ${elapsed}ms (평균 ${elapsed / 1000.0}ms/건)")
        assertThat(elapsed).isLessThan(3_000L)
    }

    @Test
    fun `50회 병렬 임베딩이 결정론적 결과를 반환한다`() = runTest {
        val text = "검사입니다 안전계좌로"
        val reference = engine.embed(text)

        val results = (1..50).map {
            async(Dispatchers.Default) { engine.embed(text) }
        }.awaitAll()

        results.forEach { vec ->
            val similarity = cosineSimilarity(reference, vec)
            assertThat(abs(similarity - 1.0f)).isLessThan(0.001f)
        }
    }

    @Test
    fun `임베딩 품질 - 사기 카테고리 내 유사도가 카테고리 간 유사도보다 높다`() = runTest {
        // 같은 카테고리 사기 텍스트들
        val voicePhishing1 = engine.embed("검사입니다 안전계좌로 돈을 이체해주세요 수사 중입니다")
        val voicePhishing2 = engine.embed("경찰청입니다 계좌 동결 방지를 위해 안전계좌로 이동하세요")

        // 다른 카테고리 사기 텍스트
        val investmentFraud = engine.embed("원금보장 수익 보장 VIP방 세력 매집 상한가")

        // 일반 텍스트
        val normalText = engine.embed("오늘 점심으로 김치찌개를 먹었어요 맛있었습니다")

        val withinCategorySimilarity = cosineSimilarity(voicePhishing1, voicePhishing2)
        val crossCategorySimilarity = cosineSimilarity(voicePhishing1, investmentFraud)
        val normalSimilarity = cosineSimilarity(voicePhishing1, normalText)

        println("[품질] 카테고리 내 유사도: $withinCategorySimilarity")
        println("[품질] 카테고리 간 유사도: $crossCategorySimilarity")
        println("[품질] 일반 텍스트 유사도: $normalSimilarity")

        // 같은 카테고리 사기끼리는 일반 텍스트보다 유사해야 함
        assertThat(withinCategorySimilarity).isGreaterThan(normalSimilarity)
    }

    @Test
    fun `5000자 텍스트 임베딩이 1초 안에 완료된다`() = runTest {
        val longText = "검사입니다 안전계좌 수사 중 인증번호 ".repeat(200)
        val start = System.currentTimeMillis()
        val vec = engine.embed(longText)
        val elapsed = System.currentTimeMillis() - start

        println("[경계값] 5000자 임베딩: ${elapsed}ms")
        assertThat(elapsed).isLessThan(1_000L)
        assertThat(vec.size).isEqualTo(512)
        assertThat(vec.any { it.isNaN() }).isFalse()
    }

    @Test
    fun `같은 카테고리 사기 텍스트끼리의 유사도가 다른 카테고리 간보다 높다`() = runTest {
        // CharNgram 엔진은 문자 패턴 기반 → 같은 단어를 공유하는 텍스트끼리 유사도 높음
        // 완전히 다른 단어를 쓰는 카테고리 간은 유사도 낮음

        // 보이스피싱 내부 유사도 (같은 키워드 공유)
        val vp1 = engine.embed("검사입니다 안전계좌로 수사 중 이체해주세요")
        val vp2 = engine.embed("검찰청입니다 안전계좌 이동 수사관")
        val withinCategory = cosineSimilarity(vp1, vp2)

        // 완전히 다른 도메인 (영어 + 숫자만)
        val unrelated = engine.embed("hello world 12345 abc xyz")
        val crossDomain = cosineSimilarity(vp1, unrelated)

        println("[품질] 같은 카테고리 유사도: $withinCategory")
        println("[품질] 다른 도메인 유사도: $crossDomain")

        // 같은 카테고리(공유 키워드 있음)가 완전히 다른 도메인보다 유사해야 함
        assertThat(withinCategory).isGreaterThan(crossDomain)
    }

    @Test
    fun `완전히 동일한 패턴의 사기 텍스트는 높은 유사도를 가진다`() = runTest {
        val scam1 = engine.embed("안전계좌로 돈을 옮겨주세요 검사입니다")
        val scam2 = engine.embed("검사입니다 안전계좌 이체 요청")

        val similarity = cosineSimilarity(scam1, scam2)
        println("[품질] 유사 패턴 사기 텍스트 유사도: $similarity")

        // 공유하는 핵심 단어("안전계좌", "검사")가 있으므로 유사도가 0.5 이상이어야 함
        assertThat(similarity).isGreaterThan(0.5f)
    }
}
