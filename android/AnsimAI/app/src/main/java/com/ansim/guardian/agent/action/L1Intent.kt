package com.ansim.guardian.agent.action

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.ansim.guardian.agent.nlu.ToolCall

/**
 * L1 어댑터 — 안드로이드 *표준 Intent*로 처리 가능한 안전 도구.
 *
 * - call_contact      → ACTION_CALL (권한 있을 때) / ACTION_DIAL (fallback)
 * - call_emergency    → ACTION_CALL ("tel:119" 등)
 * - web_search        → ACTION_VIEW + naver.com
 * - map_navigate      → ACTION_VIEW + nmap:// 또는 geo:
 */
class L1Intent(
    private val context: Context,
    private val contactMatcher: ContactMatcher,
) {

    fun callContact(personRef: String): ActionResult {
        val candidates = contactMatcher.resolve(personRef)
        when {
            candidates.isEmpty() -> return ActionResult.Failed(
                errorCode = "contact_not_found",
                message = "result_fail.contact_not_found"
            )
            candidates.size > 1 -> return ActionResult.Escalated(
                toNas = false,
                toFamily = false,
            )  // 호출자가 C2 clarify로 분기
        }
        val chosen = candidates.first()
        return placeCall(chosen.phone, chosen.displayName)
    }

    fun callEmergency(kind: String): ActionResult {
        val number = when (kind) {
            "119" -> "119"
            "112" -> "112"
            else -> return ActionResult.Failed("invalid_emergency", "kind=$kind")
        }
        return placeCall(number, displayHint = kind)
    }

    fun webSearch(query: String): ActionResult {
        val url = "https://m.naver.com/search.naver?query=" + Uri.encode(query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(intent, success = "'$query' 검색 결과를 보여드릴게요.")
    }

    fun mapNavigate(destination: String, mode: String): ActionResult {
        // 카카오맵·네이버지도 deep link가 가장 확실하지만 미설치 폰 fallback이 필요해 geo: 사용
        val uri = "geo:0,0?q=" + Uri.encode(destination)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(intent, success = "$destination 까지 가는 길 보여드릴게요. 잘 다녀오세요.")
    }

    private fun placeCall(phone: String, displayHint: String): ActionResult {
        val hasCallPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:" + Uri.encode(phone)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(intent, success = "$displayHint 에게 전화 걸어드릴게요.")
    }

    private fun launch(intent: Intent, success: String): ActionResult {
        return try {
            context.startActivity(intent)
            ActionResult.Success(message = success)
        } catch (t: Throwable) {
            ActionResult.Failed("launch_failed", t.message ?: "Intent launch failed")
        }
    }
}
