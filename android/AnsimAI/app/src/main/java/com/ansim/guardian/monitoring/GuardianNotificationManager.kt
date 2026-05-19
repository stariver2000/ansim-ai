package com.ansim.guardian.monitoring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.ansim.guardian.MainActivity
import com.ansim.guardian.domain.model.RiskLevel
import com.ansim.guardian.domain.model.RiskResult

private const val CHANNEL_ID = "ansim_guardian"
private const val PREFS_NAME = "ansim_prefs"
private const val KEY_GUARDIAN_PHONE = "guardian_phone"
private const val KEY_GUARDIAN_NAME = "guardian_name"

class GuardianNotificationManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 보호자 전화번호 저장/조회
    var guardianPhone: String
        get() = prefs.getString(KEY_GUARDIAN_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GUARDIAN_PHONE, value).apply()

    var guardianName: String
        get() = prefs.getString(KEY_GUARDIAN_NAME, "보호자") ?: "보호자"
        set(value) = prefs.edit().putString(KEY_GUARDIAN_NAME, value).apply()

    fun isGuardianConfigured(): Boolean = guardianPhone.isNotBlank()

    // 기기 내 알림 (앱 내 사용자에게)
    fun showLocalNotification(riskResult: RiskResult, sourceLabel: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // minSdk=26(O) 이므로 항상 NotificationChannel 생성
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "안심동행 AI 경고", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "사기 위험 감지 알림" }
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("${riskResult.riskLevel.emoji} ${riskResult.riskLevel.label} 감지됨")
            .setContentText("[$sourceLabel] ${riskResult.detectedSignals.firstOrNull()?.description ?: "위험 신호"} 탐지")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(buildNotificationBody(riskResult, sourceLabel)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    // 보호자에게 SMS 발송
    fun sendGuardianSms(riskResult: RiskResult, sourceLabel: String) {
        val phone = guardianPhone
        if (phone.isBlank()) return

        val message = buildSmsMessage(riskResult, sourceLabel)
        try {
            // Android 12(S) 이상은 getSystemService, 이하는 getDefault (minSdk=26이므로 양쪽 필요)
            @Suppress("DEPRECATION")
            val smsManager: SmsManager? =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    context.getSystemService(SmsManager::class.java)
                else
                    SmsManager.getDefault()

            smsManager?.sendTextMessage(phone, null, message, null, null)
        } catch (e: Exception) {
            // SMS 발송 실패 시 조용히 실패 (권한 없는 경우 등)
        }
    }

    fun notifyAll(riskResult: RiskResult, sourceLabel: String) {
        // 주의 이상이면 기기 알림
        if (riskResult.riskLevel.ordinal >= RiskLevel.CAUTION.ordinal) {
            showLocalNotification(riskResult, sourceLabel)
        }
        // 위험 이상이면 보호자 SMS
        if (riskResult.riskLevel.ordinal >= RiskLevel.DANGER.ordinal && isGuardianConfigured()) {
            sendGuardianSms(riskResult, sourceLabel)
        }
    }

    private fun buildNotificationBody(riskResult: RiskResult, sourceLabel: String): String {
        val signals = riskResult.detectedSignals.take(3)
            .joinToString(", ") { it.description }
        return "[$sourceLabel] $signals"
    }

    private fun buildSmsMessage(riskResult: RiskResult, sourceLabel: String): String {
        val signals = riskResult.detectedSignals.take(3)
            .joinToString("\n- ") { it.description }
        return """
[안심동행 AI 경고]
${riskResult.riskLevel.emoji} ${riskResult.riskLevel.label}

출처: $sourceLabel
감지된 신호:
- $signals

앱을 열어서 자세히 확인하세요.
        """.trimIndent()
    }
}
