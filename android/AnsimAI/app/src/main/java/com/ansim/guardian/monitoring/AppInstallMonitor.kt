package com.ansim.guardian.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.InputSource
import com.ansim.guardian.domain.model.RiskInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AppInstallMonitor"

// 원격제어 앱 설치 감지
class AppInstallMonitor : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val ruleEngine = RuleBasedRiskEngine()

    private val dangerousPackages = mapOf(
        "com.anydesk.anydeskandroid" to "AnyDesk",
        "com.teamviewer.teamviewer" to "TeamViewer",
        "net.rustdesk" to "RustDesk",
        "com.rsupport.mobizen.sec" to "원격 지원 앱",
        "com.google.android.projection.gearhead" to "알 수 없는 화면공유 앱",
        "com.fanfou.app" to "알 수 없는 원격앱"
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        val appName = dangerousPackages[packageName] ?: return

        Log.w(TAG, "원격제어 앱 설치 감지: $appName ($packageName)")

        scope.launch {
            val warningText = "$appName 원격제어 앱을 설치해 주세요 화면 공유"
            val input = RiskInput(
                text = warningText,
                source = InputSource.APP_INSTALL,
                senderInfo = appName
            )
            val result = ruleEngine.analyze(input)
            RiskEventBus.emit(RiskEvent(result, "앱 설치 감지: $appName"))
        }
    }
}
