package com.ansim.guardian.domain.engine

import com.ansim.guardian.ai.embedding.EmbeddingRepository
import com.ansim.guardian.data.repository.ScamRepository
import com.ansim.guardian.domain.model.ScamCase
import com.ansim.guardian.domain.model.SignalCategory

// 검색 우선순위:
//   1. 벡터 유사도 (ONNX or CharNgram 임베딩) — 의미 기반
//   2. 키워드 LIKE 검색 — 정확 매칭 보완
// 두 결과를 병합해서 최종 반환
class LocalRagEngine(
    private val repository: ScamRepository,
    private val embeddingRepository: EmbeddingRepository
) : RagEngine {

    override suspend fun retrieveSimilarCases(inputText: String, limit: Int): List<ScamCase> {
        return retrieve(inputText, category = null, limit = limit)
    }

    suspend fun retrieveByCategoryAndText(
        category: SignalCategory?,
        inputText: String,
        limit: Int = 3
    ): List<ScamCase> {
        return retrieve(inputText, category = category?.displayName, limit = limit)
    }

    private suspend fun retrieve(
        inputText: String,
        category: String?,
        limit: Int
    ): List<ScamCase> {
        val results = mutableListOf<ScamCase>()

        // 1순위: 임베딩 벡터 유사도 검색 (의미 기반)
        val vectorResults = embeddingRepository.searchWithCategory(
            query = inputText,
            category = category,
            limit = limit
        )
        results.addAll(vectorResults)

        // 2순위: 부족하면 키워드 검색으로 보완
        if (results.size < limit) {
            val keywords = extractKeywords(inputText.lowercase())
            for (keyword in keywords) {
                if (results.size >= limit) break
                val found = repository.searchByKeyword(keyword, limit - results.size)
                results.addAll(found.filter { c -> results.none { it.id == c.id } })
            }
        }

        // 3순위: 그래도 부족하면 카테고리로 채움
        if (results.size < limit && category != null) {
            val categoryResults = repository.searchByCategory(category, limit - results.size)
            results.addAll(categoryResults.filter { c -> results.none { it.id == c.id } })
        }

        return results.take(limit)
    }

    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf("이", "그", "저", "것", "수", "있", "하", "않", "되", "도", "을", "를")
        return text
            .split(" ", "\n", ".", ",", "?", "!", "(", ")")
            .filter { it.length >= 2 && it !in stopWords }
            .take(5)
    }
}
