package com.ansim.guardian.monitoring

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ansim.guardian.domain.engine.HybridRiskEngine
import com.ansim.guardian.domain.model.InputSource
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "NotificationMonitor"

class NotificationMonitorService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    // Context가 필요하므로 lazy로 초기화 (onCreate 이후 사용 가능)
    private val engine by lazy { HybridRiskEngine(applicationContext) }

    // 분석할 앱 목록
    private val monitoredPackages = setOf(
        "com.kakao.talk",           // 카카오톡
        "org.telegram.messenger",   // 텔레그램
        "org.telegram.messenger.web",
        "com.whatsapp",             // 왓츠앱
        "com.samsung.android.messaging", // 삼성 문자
        "com.android.mms",          // 기본 문자
        "com.google.android.apps.messaging" // Google 메시지
    )

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!MonitoringPrefs.isEnabled(applicationContext)) return  // 감시 OFF
        if (sbn.packageName !in monitoredPackages) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullText = listOf(title, bigText.ifBlank { text })
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (fullText.isBlank()) return

        val source = when (sbn.packageName) {
            "com.kakao.talk" -> InputSource.NOTIFICATION_KAKAO
            "org.telegram.messenger", "org.telegram.messenger.web" -> InputSource.NOTIFICATION_TELEGRAM
            else -> InputSource.NOTIFICATION_OTHER
        }

        scope.launch {
            analyze(fullText, source, senderInfo = title)
        }
    }

    private suspend fun analyze(text: String, source: InputSource, senderInfo: String) {
        val input = RiskInput(text = text, source = source, senderInfo = senderInfo)
        val result = engine.analyze(input)

        // 주의 이상일 때만 이벤트 발행
        if (result.riskLevel.ordinal >= RiskLevel.CAUTION.ordinal) {
            Log.i(TAG, "위험 감지 [${source.displayName}] ${result.riskLevel.label}: $text")
            RiskEventBus.emit(RiskEvent(result, source.displayName))
        }
    }

    override fun onListenerConnected() {
        Log.i(TAG, "알림 모니터링 시작")
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "알림 모니터링 중단")
    }
}
