package com.ansim.guardian.ai.embedding

import android.content.Context

// paraphrase-multilingual-MiniLM-L12-v2 용 WordPiece 토크나이저
// vocab.txt 파일을 assets에서 로드해서 사용

data class TokenizerOutput(
    val inputIds: LongArray,
    val attentionMask: LongArray
)

class WordPieceTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val unkId: Int = 100,
    private val clsId: Int = 101,
    private val sepId: Int = 102
) {

    fun tokenize(text: String, maxLength: Int = 128): TokenizerOutput {
        val cleaned = text.lowercase().replace(Regex("[\\p{Cntrl}]"), " ")
        val tokens = mutableListOf<Int>()
        tokens.add(clsId.toLong().toInt())  // [CLS]

        // 공백 분리 후 각 단어를 WordPiece로 분리
        for (word in cleaned.split(" ").filter { it.isNotBlank() }) {
            val subwords = wordPieceSegment(word)
            for (sw in subwords) {
                tokens.add(vocab[sw] ?: unkId)
                if (tokens.size >= maxLength - 1) break
            }
            if (tokens.size >= maxLength - 1) break
        }

        tokens.add(sepId)  // [SEP]

        // 패딩
        val padded = LongArray(maxLength) { if (it < tokens.size) tokens[it].toLong() else 0L }
        val mask = LongArray(maxLength) { if (it < tokens.size) 1L else 0L }

        return TokenizerOutput(padded, mask)
    }

    private fun wordPieceSegment(word: String): List<String> {
        if (vocab.containsKey(word)) return listOf(word)

        val result = mutableListOf<String>()
        var remaining = word
        var isFirst = true

        while (remaining.isNotEmpty()) {
            var found = false
            for (end in remaining.length downTo 1) {
                val candidate = if (isFirst) remaining.substring(0, end)
                                else "##" + remaining.substring(0, end)
                if (vocab.containsKey(candidate)) {
                    result.add(candidate)
                    remaining = remaining.substring(end)
                    isFirst = false
                    found = true
                    break
                }
            }
            if (!found) {
                // 알 수 없는 토큰
                result.add("[UNK]")
                break
            }
        }
        return result
    }

    companion object {
        fun fromAssets(context: Context, vocabFile: String): WordPieceTokenizer {
            val vocab = mutableMapOf<String, Int>()
            context.assets.open(vocabFile).bufferedReader().useLines { lines ->
                lines.forEachIndexed { idx, line ->
                    vocab[line.trim()] = idx
                }
            }
            return WordPieceTokenizer(vocab)
        }
    }
}
