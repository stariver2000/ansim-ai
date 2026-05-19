package com.ansim.guardian.ai.embedding

import kotlin.math.sqrt

// 모델 파일 없이 즉시 동작하는 폴백 엔진
// 문자 바이그램 기반 해시 임베딩
// 한국어 형태소 특성상 바이그램이 의미 단위를 잘 포착함
class CharNgramEmbeddingEngine : EmbeddingEngine {

    override val dimension: Int = 512

    override fun isReady(): Boolean = true

    override fun embed(text: String): FloatArray {
        val vector = FloatArray(dimension)
        val cleaned = text.replace(Regex("\\s+"), " ").trim()

        // 유니그램 + 바이그램 + 트라이그램 추출
        val ngrams = mutableListOf<String>()
        for (i in cleaned.indices) {
            ngrams.add(cleaned[i].toString())                    // 유니그램
            if (i + 1 < cleaned.length) {
                ngrams.add(cleaned.substring(i, i + 2))          // 바이그램
            }
            if (i + 2 < cleaned.length) {
                ngrams.add(cleaned.substring(i, i + 3))          // 트라이그램
            }
        }

        // 해시 트릭: n-gram을 고정 차원 벡터에 매핑
        for (ngram in ngrams) {
            val h1 = (ngram.hashCode() and Int.MAX_VALUE) % dimension
            val h2 = (ngram.hashCode() * 2654435761.toInt() and Int.MAX_VALUE) % dimension
            vector[h1] += 1f
            vector[h2] += 0.5f
        }

        return normalize(vector)
    }

    private fun normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.fold(0f) { acc, f -> acc + f * f })
        return if (norm == 0f) v else FloatArray(v.size) { v[it] / norm }
    }
}
