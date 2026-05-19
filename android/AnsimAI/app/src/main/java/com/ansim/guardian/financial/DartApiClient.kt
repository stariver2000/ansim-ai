package com.ansim.guardian.financial

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

private const val TAG = "DartApiClient"
private const val BASE_URL = "https://opendart.fss.or.kr/api"

// DART(전자공시) OpenAPI 클라이언트
// API 키 발급: https://opendart.fss.or.kr/intro/main.do
// 무료 발급 가능 (일 10,000건)
class DartApiClient(private val apiKey: String = "") {

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    // 기업 개황 조회
    suspend fun getCompanyInfo(corpCode: String): StockInfo? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        try {
            val url = "$BASE_URL/company.json?crtfc_key=$apiKey&corp_code=$corpCode"
            val json = fetchJson(url) ?: return@withContext null

            val status = json.optString("status")
            if (status != "000") return@withContext null

            StockInfo(
                ticker = corpCode,
                companyName = json.optString("corp_name"),
                market = json.optString("stock_mkt"),
                auditOpinion = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "기업 조회 실패: ${e.message}")
            null
        }
    }

    // 최근 공시 조회
    suspend fun getRecentDisclosures(corpCode: String, limit: Int = 5): List<String> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext emptyList()
            try {
                val url = "$BASE_URL/list.json?crtfc_key=$apiKey&corp_code=$corpCode&bgn_de=&end_de=&last_reprt_at=Y&pblntf_ty=A&sort=date&sort_mth=desc&page_no=1&page_count=$limit"
                val json = fetchJson(url) ?: return@withContext emptyList()

                if (json.optString("status") != "000") return@withContext emptyList()

                val list = json.optJSONArray("list") ?: return@withContext emptyList()
                (0 until list.length()).map { i ->
                    val item = list.getJSONObject(i)
                    "[${item.optString("rcept_dt")}] ${item.optString("report_nm")}"
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    // 관리종목·투자주의 여부는 KRX에서 확인 (별도 API 없으므로 캐시 데이터 사용)
    private fun fetchJson(urlStr: String): JSONObject? {
        return try {
            val content = URL(urlStr).readText()
            JSONObject(content)
        } catch (e: Exception) {
            Log.e(TAG, "HTTP 요청 실패: $urlStr — ${e.message}")
            null
        }
    }
}
