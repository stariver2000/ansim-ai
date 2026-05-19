package com.ansim.guardian.ai.llm

interface LlmEngine {
    fun isAvailable(): Boolean
    suspend fun generate(prompt: String, maxTokens: Int = 256): String
}
