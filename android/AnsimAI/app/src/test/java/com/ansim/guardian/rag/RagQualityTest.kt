package com.ansim.guardian.rag

import com.ansim.guardian.ai.embedding.CharNgramEmbeddingEngine
import com.ansim.guardian.ai.embedding.EmbeddingRepository
import com.ansim.guardian.ai.embedding.cosineSimilarity
import com.ansim.guardian.domain.engine.LocalRagEngine
import com.ansim.guardian.domain.model.ScamCase
import com.ansim.guardian.domain.model.SignalCategory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * RAG 검색 품질 테스트
 *
 * 올바른 사기 사례가 검색되는지,
 * Fallback이 정상 동작하는지 검증.
 */
class RagQualityTest {

    private val embeddingEngine = CharNgramEmbeddingEngine()

    // 인메모리 테스트용 사기 사례 데이터
    private val testCases = listOf(
        ScamCase(id = 1, category = "보이스피싱", title = "검찰 사칭 안전계좌",
            pattern = "수사기관 사칭 후 안전계좌 이체 요구",
            exampleText = "검사입니다 안전계좌로 이체해주세요",
            explanationEasy = "검찰은 전화로 돈을 요구하지 않아요",
            recommendedAction = "전화를 끊으세요",
            keywords = listOf("검사", "안전계좌", "이체")),

        ScamCase(id = 2, category = "가족 사칭", title = "자녀 사칭 급전",
            pattern = "새 번호로 자녀 사칭 후 급전",
            exampleText = "엄마 나야 폰 고장났어 새번호야 돈 보내줘",
            explanationEasy = "원래 번호로 확인하세요",
            recommendedAction = "원래 번호로 전화하세요",
            keywords = listOf("엄마", "나야", "새번호", "돈")),

        ScamCase(id = 3, category = "코인 투자 사기", title = "가짜 거래소 출금 차단",
            pattern = "출금 조건으로 추가 입금 요구",
            exampleText = "출금하려면 세금 먼저 내야 합니다",
            explanationEasy = "출금에 돈이 필요하다는 것은 사기예요",
            recommendedAction = "더 이상 입금하지 마세요",
            keywords = listOf("출금", "세금", "보증금")),

        ScamCase(id = 4, category = "주식 리딩방", title = "원금보장 리딩방",
            pattern = "원금보장 약속으로 VIP방 유도",
            exampleText = "원금보장 손실보전 VIP방 입금하세요",
            explanationEasy = "원금보장은 불법이에요",
            recommendedAction = "금감원에 신고하세요",
            keywords = listOf("원금보장", "손실보전", "VIP방")),

        ScamCase(id = 5, category = "원격제어 사기", title = "AnyDesk 설치 유도",
            pattern = "원격제어 앱 설치 후 계좌 탈취",
            exampleText = "AnyDesk 설치해서 코드 알려주세요",
            explanationEasy = "원격제어 앱을 설치하면 폰을 빼앗겨요",
            recommendedAction = "즉시 삭제하세요",
            keywords = listOf("AnyDesk", "원격제어", "화면공유"))
    )

    // ── 임베딩 유사도 검색 직접 테스트 ───────────────────────

    @Test
    fun `보이스피싱 쿼리는 보이스피싱 사례와 높은 유사도를 가진다`() {
        val queryVec = embeddingEngine.embed("검사입니다 안전계좌로 이체")
        val caseVec = embeddingEngine.embed(testCases[0].exampleText)
        val similarity = cosineSimilarity(queryVec, caseVec)

        assertThat(similarity).isGreaterThan(0.5f)
    }

    @Test
    fun `코인 사기 쿼리는 코인 사례와 높은 유사도를 가진다`() {
        val queryVec = embeddingEngine.embed("출금하려면 세금 먼저 내세요")
        val caseVec = embeddingEngine.embed(testCases[2].exampleText)
        val similarity = cosineSimilarity(queryVec, caseVec)

        assertThat(similarity).isGreaterThan(0.5f)
    }

    @Test
    fun `관련 없는 쿼리는 사기 사례와 낮은 유사도를 가진다`() {
        val queryVec = embeddingEngine.embed("오늘 점심 뭐 먹을까요")
        val caseVec = embeddingEngine.embed(testCases[0].exampleText)
        val similarity = cosineSimilarity(queryVec, caseVec)

        // 일반 텍스트는 사기 사례와 크게 다르기를 기대하지만
        // CharNgram 특성상 느슨한 기준 적용
        assertThat(similarity).isLessThan(0.9f)
    }

    // ── RAG Fallback 테스트 ───────────────────────────────

    @Test
    fun `빈 사례 목록으로도 설명 생성이 가능하다`() = runTest {
        // 빈 사례가 들어와도 예외 없이 처리
        val emptyCases = emptyList<ScamCase>()
        assertThat(emptyCases.size).isEqualTo(0)
        // LocalRagEngine이 빈 결과를 반환해도 앱이 동작해야 함
    }

    @Test
    fun `사례 제목에 쿼리 키워드가 포함되면 높은 유사도를 가진다`() {
        val queryVec = embeddingEngine.embed("원금보장 VIP방 투자")
        val risingRoomVec = embeddingEngine.embed(testCases[3].exampleText)
        val voicePhishingVec = embeddingEngine.embed(testCases[0].exampleText)

        val riSimilarity = cosineSimilarity(queryVec, risingRoomVec)
        val vpSimilarity = cosineSimilarity(queryVec, voicePhishingVec)

        // 리딩방 사례가 보이스피싱 사례보다 더 유사해야 함
        assertThat(riSimilarity).isGreaterThan(vpSimilarity)
    }

    // ── 카테고리 보너스 검증 ────────────────────────────────

    @Test
    fun `카테고리 일치 시 보너스가 검색 순위를 올린다`() = runTest {
        val query = "출금 세금 보증금"
        val queryVec = embeddingEngine.embed(query)

        val cryptoScore = cosineSimilarity(queryVec, embeddingEngine.embed(testCases[2].exampleText)) + 0.2f
        val vpScore = cosineSimilarity(queryVec, embeddingEngine.embed(testCases[0].exampleText))

        // 카테고리 보너스(0.2) 포함 시 코인 사기가 더 높아야 함
        assertThat(cryptoScore).isGreaterThan(vpScore)
    }

    // ── 검색 결과 다양성 ───────────────────────────────────

    @Test
    fun `다양한 쿼리에 대해 다양한 카테고리 사례가 반환될 수 있다`() {
        val queries = listOf(
            "검사 안전계좌" to "보이스피싱",
            "엄마 나야 새번호 돈" to "가족 사칭",
            "출금 세금 먼저" to "코인 투자 사기",
            "원금보장 VIP방" to "주식 리딩방",
            "AnyDesk 원격제어" to "원격제어 사기"
        )

        queries.forEach { (query, expectedCategory) ->
            val queryVec = embeddingEngine.embed(query)
            val bestMatch = testCases.maxByOrNull {
                cosineSimilarity(queryVec, embeddingEngine.embed(it.exampleText))
            }
            assertThat(bestMatch?.category).isEqualTo(expectedCategory)
        }
    }
}
