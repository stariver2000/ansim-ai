package com.ansim.guardian.agent.action

import android.content.Context
import android.content.Intent

/**
 * L2 어댑터 — 큐레이션된 앱 화이트리스트 직접 실행.
 *
 * app_alias enum → 알려진 패키지명 매핑. 미설치 시 명확한 실패 메시지.
 *
 * 금융 앱(은행·증권·결제)은 의도적으로 *제외* — 어르신이 직접 열도록(L4).
 */
class L2Shortcut(private val context: Context) {

    private val knownPackages = mapOf(
        "kakao" to "com.kakao.talk",
        "naver_map" to "com.nhn.android.nmap",
        "kakao_t" to "com.kakao.taxi",
        "youtube" to "com.google.android.youtube",
        "gallery" to "com.sec.android.gallery3d",     // Samsung 갤러리
        "camera" to "com.sec.android.app.camera",     // Samsung 카메라
        "sms" to "com.samsung.android.messaging",
        "phone" to "com.samsung.android.dialer",
        "weather" to "com.samsung.android.app.weather",
        "calendar" to "com.samsung.android.calendar",
        "alarm" to "com.sec.android.app.clockpackage",
    )

    fun openApp(alias: String): ActionResult {
        val pkg = knownPackages[alias]
            ?: return ActionResult.Failed("app_not_in_catalog", "alias=$alias")

        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return ActionResult.Failed(
                errorCode = "app_not_installed",
                message = "result_fail.app_not_installed"
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            ActionResult.Success(message = "$alias 열어드릴게요.")
        } catch (t: Throwable) {
            ActionResult.Failed("launch_failed", t.message ?: "Launch failed")
        }
    }
}
