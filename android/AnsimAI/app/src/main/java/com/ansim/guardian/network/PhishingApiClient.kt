package com.ansim.guardian.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "PhishingApiClient"

const val SERVER_URL = "https://ansim-ai-production.up.railway.app"

data class AnalyzeRequest(
    val message: String,
    @SerializedName("source_type") val sourceType: String = "unknown"
)

data class PhishingResult(
    val label: String,           // "safe" | "suspicious" | "phishing"
    @SerializedName("risk_score") val riskScore: Int,
    val reasons: List<String>,
    @SerializedName("recommended_action") val recommendedAction: String,
    @SerializedName("detected_signals") val detectedSignals: List<String>,
    @SerializedName("analyzed_by") val analyzedBy: String
)

object PhishingApiClient {

    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json".toMediaType()

    suspend fun analyze(message: String, sourceType: String = "unknown"): PhishingResult? =
        withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(AnalyzeRequest(message, sourceType))
                    .toRequestBody(JSON)

                val request = Request.Builder()
                    .url("$SERVER_URL/api/v1/analyze")
                    .post(body)
                    .build()

                val response = http.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "서버 오류: ${response.code}")
                    return@withContext null
                }

                val json = response.body?.string() ?: return@withContext null
                Log.d(TAG, "서버 응답: $json")
                gson.fromJson(json, PhishingResult::class.java)

            } catch (e: Exception) {
                Log.w(TAG, "서버 연결 실패 (오프라인?): ${e.message}")
                null
            }
        }
}
