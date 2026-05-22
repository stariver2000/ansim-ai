package com.ansim.guardian.monitoring

import android.content.Context

/** 감시 On/Off 설정을 SharedPreferences에 저장 */
object MonitoringPrefs {
    private const val PREFS_NAME = "ansim_prefs"
    private const val KEY_MONITORING = "monitoring_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MONITORING, true)  // 기본값: 켜짐

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MONITORING, enabled).apply()
    }
}
