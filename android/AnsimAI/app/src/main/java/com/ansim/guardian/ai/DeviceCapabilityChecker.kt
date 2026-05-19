package com.ansim.guardian.ai

import android.app.ActivityManager
import android.content.Context

enum class DeviceTier {
    LOW,    // 3GB 이하: LLM 없음, 템플릿 + RAG만
    MID,    // 4GB: Qwen2.5-1.5B Q4 가능
    HIGH    // 6GB 이상: Qwen2.5-3B Q4 가능
}

class DeviceCapabilityChecker(private val context: Context) {

    fun getDeviceTier(): DeviceTier {
        val totalRamMb = getTotalRamMb()
        return when {
            totalRamMb >= 6000 -> DeviceTier.HIGH
            totalRamMb >= 4000 -> DeviceTier.MID
            else -> DeviceTier.LOW
        }
    }

    fun canRunLlm(): Boolean = getDeviceTier() != DeviceTier.LOW

    private fun getTotalRamMb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }
}
