package com.ansim.guardian.embedding

import com.ansim.guardian.ai.embedding.CharNgramEmbeddingEngine
import com.ansim.guardian.ai.embedding.cosineSimilarity
import com.ansim.guardian.ai.embedding.toBytes
import com.ansim.guardian.ai.embedding.toFloatArray
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class CharNgramEmbeddingEngineTest {

    private lateinit var engine: CharNgramEmbeddingEngine

    @Before
    fun setUp() {
        engine = CharNgramEmbeddingEngine()
    }

    @Test
    fun `항상 isReady는 true다`() {
        assertThat(engine.isReady()).isTrue()
    }

    @Test
    fun `임베딩 차원은 512다`() {
        assertThat(engine.dimension).isEqualTo(512)
    }

    @Test
    fun `임베딩 결과는 정확히 512차원이다`() {
        val vec = engine.embed("테스트 문장입니다")
        assertThat(vec.size).isEqualTo(512)
    }

    @Test
    fun `임베딩 벡터는 L2 정규화되어 있다`() {
        val vec = engine.embed("안전계좌로 돈을 옮겨주세요")
        val norm = sqrt(vec.fold(0f) { acc, f -> acc + f * f })
        assertThat(abs(norm - 1.0f)).isLessThan(0.001f)
    }

    @Test
    fun `빈 문자열도 처리된다`() {
        val vec = engine.embed("")
        assertThat(vec.size).isEqualTo(512)
        // 모두 0이거나 정규화 불가 → NaN 없어야 함
        assertThat(vec.any { it.isNaN() }).isFalse()
    }

    @Test
    fun `동일한 텍스트는 동일한 임베딩을 생성한다`() {
        val text = "보이스피싱 탐지 테스트"
        val vec1 = engine.embed(text)
        val vec2 = engine.embed(text)
        assertThat(vec1.contentEquals(vec2)).isTrue()
    }

    @Test
    fun `유사한 사기 텍스트끼리 코사인 유사도가 높다`() {
        val scam1 = engine.embed("검사입니다 안전계좌로 돈을 옮겨주세요 수사 중입니다")
        val scam2 = engine.embed("검찰청입니다 계좌를 안전계좌로 이동해야 합니다")
        val unrelated = engine.embed("오늘 저녁 뭐 먹을까요 날씨가 맑네요")

        val scamSimilarity = cosineSimilarity(scam1, scam2)
        val unrelatedSimilarity = cosineSimilarity(scam1, unrelated)

        assertThat(scamSimilarity).isGreaterThan(unrelatedSimilarity)
    }

    @Test
    fun `다른 텍스트는 다른 임베딩을 생성한다`() {
        val vec1 = engine.embed("보이스피싱")
        val vec2 = engine.embed("날씨가 맑습니다")
        val similarity = cosineSimilarity(vec1, vec2)
        assertThat(similarity).isLessThan(0.99f)
    }

    @Test
    fun `매우 긴 텍스트도 처리된다`() {
        val longText = "보이스피싱 탐지 ".repeat(500)
        val vec = engine.embed(longText)
        assertThat(vec.size).isEqualTo(512)
        assertThat(vec.any { it.isNaN() }).isFalse()
    }

    @Test
    fun `특수문자 포함 텍스트도 처리된다`() {
        val text = "!@#$%^&*()_+{}|:<>?검사입니다~`[]\\;',./"
        val vec = engine.embed(text)
        assertThat(vec.size).isEqualTo(512)
        assertThat(vec.any { it.isNaN() }).isFalse()
    }

    @Test
    fun `ByteArray 직렬화 역직렬화가 정확하다`() {
        val original = engine.embed("직렬화 테스트")
        val bytes = original.toBytes()
        val restored = bytes.toFloatArray()

        assertThat(original.size).isEqualTo(restored.size)
        for (i in original.indices) {
            assertThat(abs(original[i] - restored[i])).isLessThan(0.0001f)
        }
    }

    @Test
    fun `코사인 유사도는 -1에서 1 사이다`() {
        val vec1 = engine.embed("첫 번째 텍스트")
        val vec2 = engine.embed("두 번째 텍스트")
        val similarity = cosineSimilarity(vec1, vec2)
        assertThat(similarity).isAtLeast(-1.0f)
        assertThat(similarity).isAtMost(1.0f)
    }

    @Test
    fun `자기 자신과의 코사인 유사도는 1이다`() {
        val vec = engine.embed("자기 자신 테스트")
        val similarity = cosineSimilarity(vec, vec)
        assertThat(abs(similarity - 1.0f)).isLessThan(0.001f)
    }
}
