package com.ansim.guardian.ai.embedding

interface EmbeddingEngine {
    // 텍스트를 고정 크기 벡터로 변환
    fun embed(text: String): FloatArray

    // 모델이 로드되어 있는지 여부
    fun isReady(): Boolean

    // 벡터 차원 수
    val dimension: Int
}

// 두 벡터의 코사인 유사도 계산 (0~1, 1에 가까울수록 유사)
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) return 0f
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())
    return if (denom == 0.0) 0f else (dot / denom).toFloat()
}

// FloatArray를 ByteArray로 직렬화 (SQLite BLOB 저장용)
fun FloatArray.toBytes(): ByteArray {
    val buf = java.nio.ByteBuffer.allocate(size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    forEach { buf.putFloat(it) }
    return buf.array()
}

// ByteArray를 FloatArray로 역직렬화
fun ByteArray.toFloatArray(): FloatArray {
    val buf = java.nio.ByteBuffer.wrap(this).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buf.float }
}
