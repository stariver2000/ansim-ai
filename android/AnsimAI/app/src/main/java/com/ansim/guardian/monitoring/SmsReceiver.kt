package com.ansim.guardian.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.ansim.guardian.AnsimApplication
import com.ansim.guardian.domain.model.InputSource
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "SmsReceiver"

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "알 수 없는 번호"
        val body = messages.joinToString("") { it.messageBody ?: "" }

        if (body.isBlank()) return
        if (!MonitoringPrefs.isEnabled(context)) return

        Log.d(TAG, "SMS 수신: $sender")

        scope.launch {
            val input = RiskInput(
                text = body,
                source = InputSource.SMS,
                senderInfo = sender
            )

            // hybridEngine 사용 → CAUTION 이상이면 Gemini 서버 재검토
            val result = AnsimApplication.instance.hybridEngine.analyze(input)

            if (result.riskLevel.ordinal >= RiskLevel.CAUTION.ordinal) {
                Log.i(TAG, "SMS 위험 감지 [${result.riskLevel.label} / ${result.analyzedBy()}]: $sender")
                RiskEventBus.emit(RiskEvent(result, "문자 (${sender})"))
            }
        }
    }

    private fun com.ansim.guardian.domain.model.RiskResult.analyzedBy() =
        if (llmReason != null) "Gemini" else "규칙"
}
