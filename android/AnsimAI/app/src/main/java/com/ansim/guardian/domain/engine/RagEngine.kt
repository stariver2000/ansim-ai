package com.ansim.guardian.domain.engine

import com.ansim.guardian.domain.model.ScamCase

interface RagEngine {
    suspend fun retrieveSimilarCases(inputText: String, limit: Int = 3): List<ScamCase>
}
