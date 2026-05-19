package com.ansim.guardian.ai.embedding

import android.content.Context
import android.util.Log
import com.ansim.guardian.data.repository.ScamRepository
import com.ansim.guardian.domain.model.ScamCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EmbeddingRepository"
private const val PREFS_KEY = "embeddings_built"
private const val PREFS_NAME = "ansim_prefs"

// 사기 사례 임베딩을 빌드하고 메모리에 캐싱하는 레포지토리
// SQLite에 BLOB으로 저장하지 않고, 앱 시작 시 in-memory로 빌드
// (사기 사례는 ~50개이므로 메모리 부담 없음)
class EmbeddingRepository(
    private val context: Context,
    private val scamRepository: ScamRepository
) {
    // 엔진 선택: ONNX 우선, 없으면 CharNgram 폴백
    val engine: EmbeddingEngine by lazy {
        val onnx = OnnxEmbeddingEngine(context)
        if (onnx.isReady()) {
            Log.i(TAG, "ONNX 임베딩 엔진 사용")
            onnx
        } else {
            Log.i(TAG, "CharNgram 폴백 엔진 사용")
            CharNgramEmbeddingEngine()
        }
    }

    // 사기 사례 + 임베딩 캐시 (메모리)
    private var indexedCases: List<IndexedCase> = emptyList()
    private var isIndexed = false

    data class IndexedCase(
        val case: ScamCase,
        val embedding: FloatArray
    )

    suspend fun buildIndex() = withContext(Dispatchers.Default) {
        if (isIndexed) return@withContext
        val cases = scamRepository.getAll()
        indexedCases = cases.map { case ->
            val text = "${case.title} ${case.pattern} ${case.exampleText} ${case.keywords.joinToString(" ")}"
            IndexedCase(case, engine.embed(text))
        }
        isIndexed = true
        Log.i(TAG, "임베딩 인덱스 빌드 완료: ${indexedCases.size}개 사례")
    }

    // 쿼리 텍스트와 가장 유사한 사기 사례 반환
    suspend fun search(query: String, limit: Int = 3): List<ScamCase> = withContext(Dispatchers.Default) {
        if (!isIndexed) buildIndex()
        if (indexedCases.isEmpty()) return@withContext emptyList()

        val queryVec = engine.embed(query)

        indexedCases
            .map { it.case to cosineSimilarity(queryVec, it.embedding) }
            .sortedByDescending { it.second }
            .take(limit)
            .filter { it.second > 0.1f }  // 최소 유사도 임계값
            .map { it.first }
    }

    // 카테고리 + 벡터 검색 결합
    suspend fun searchWithCategory(
        query: String,
        category: String?,
        limit: Int = 3
    ): List<ScamCase> = withContext(Dispatchers.Default) {
        if (!isIndexed) buildIndex()
        if (indexedCases.isEmpty()) return@withContext emptyList()

        val queryVec = engine.embed(query)

        val scored = indexedCases.map { indexed ->
            val similarity = cosineSimilarity(queryVec, indexed.embedding)
            // 카테고리 일치 시 가중치 부여
            val categoryBonus = if (category != null && indexed.case.category == category) 0.2f else 0f
            indexed.case to (similarity + categoryBonus)
        }

        scored
            .sortedByDescending { it.second }
            .take(limit)
            .filter { it.second > 0.1f }
            .map { it.first }
    }
}
