package com.ansim.guardian.ai.embedding

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import kotlin.math.sqrt

// paraphrase-multilingual-MiniLM-L12-v2 ONNX 모델 기반 임베딩
//
// 필요한 파일 (app/src/main/assets/ 에 넣어야 함):
//   - embedding_model.onnx  (~45MB, 양자화 버전)
//   - vocab.txt             (~1MB, 토크나이저 어휘)
//
// 다운로드 방법:
//   from transformers import AutoTokenizer, AutoModel
//   model.save_pretrained("./model_onnx")
//   tokenizer.save_pretrained("./model_onnx")
//   python -m optimum.exporters.onnx --model sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2 ./model_onnx
//
// 또는 HuggingFace Hub에서 직접:
//   https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2

private const val TAG = "OnnxEmbedding"
private const val MODEL_FILE = "embedding_model.onnx"
private const val VOCAB_FILE = "vocab.txt"
private const val MAX_SEQ_LEN = 128

class OnnxEmbeddingEngine(private val context: Context) : EmbeddingEngine {

    override val dimension: Int = 384  // MiniLM-L12 출력 차원

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: WordPieceTokenizer? = null
    private var ready = false

    init {
        try {
            load()
        } catch (e: Exception) {
            Log.w(TAG, "ONNX 모델 로드 실패 (assets에 모델 파일이 없을 수 있음): ${e.message}")
        }
    }

    private fun load() {
        // assets 폴더에 모델 파일이 없으면 조용히 실패
        if (!hasAsset(MODEL_FILE) || !hasAsset(VOCAB_FILE)) {
            Log.i(TAG, "$MODEL_FILE 또는 $VOCAB_FILE 없음 → CharNgramEngine으로 폴백")
            return
        }

        val modelBytes = context.assets.open(MODEL_FILE).readBytes()
        ortEnv = OrtEnvironment.getEnvironment()
        ortSession = ortEnv!!.createSession(modelBytes)
        tokenizer = WordPieceTokenizer.fromAssets(context, VOCAB_FILE)
        ready = true
        Log.i(TAG, "ONNX 임베딩 모델 로드 완료 (차원: $dimension)")
    }

    override fun isReady(): Boolean = ready

    override fun embed(text: String): FloatArray {
        if (!ready) throw IllegalStateException("모델이 로드되지 않았습니다")

        val tokens = tokenizer!!.tokenize(text, MAX_SEQ_LEN)
        val inputIds = tokens.inputIds
        val attentionMask = tokens.attentionMask
        val tokenTypeIds = LongArray(inputIds.size) { 0L }

        val env = ortEnv!!
        val session = ortSession!!

        val inputIdsTensor = OnnxTensor.createTensor(
            env, LongBuffer.wrap(inputIds), longArrayOf(1, inputIds.size.toLong())
        )
        val attentionMaskTensor = OnnxTensor.createTensor(
            env, LongBuffer.wrap(attentionMask), longArrayOf(1, attentionMask.size.toLong())
        )
        val tokenTypeIdsTensor = OnnxTensor.createTensor(
            env, LongBuffer.wrap(tokenTypeIds), longArrayOf(1, tokenTypeIds.size.toLong())
        )

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor,
            "token_type_ids" to tokenTypeIdsTensor
        )

        val output = session.run(inputs)

        // last_hidden_state shape: [1, seq_len, 384]
        // mean pooling: 어텐션 마스크를 적용해서 평균
        val lastHiddenState = (output[0].value as Array<*>)[0] as Array<*>
        val pooled = meanPool(lastHiddenState, attentionMask)

        inputIdsTensor.close()
        attentionMaskTensor.close()
        tokenTypeIdsTensor.close()
        output.close()

        return l2Normalize(pooled)
    }

    private fun meanPool(hiddenState: Array<*>, attentionMask: LongArray): FloatArray {
        val seqLen = hiddenState.size
        val result = FloatArray(dimension)
        var count = 0

        for (i in 0 until seqLen) {
            if (attentionMask[i] == 0L) continue
            val tokenVec = hiddenState[i] as FloatArray
            for (j in 0 until dimension) {
                result[j] += tokenVec[j]
            }
            count++
        }

        if (count > 0) {
            for (j in 0 until dimension) result[j] /= count
        }
        return result
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.fold(0f) { acc, f -> acc + f * f })
        return if (norm == 0f) v else FloatArray(v.size) { v[it] / norm }
    }

    private fun hasAsset(filename: String): Boolean = try {
        context.assets.open(filename).close(); true
    } catch (e: Exception) { false }

    fun close() {
        ortSession?.close()
        ortEnv?.close()
    }
}
