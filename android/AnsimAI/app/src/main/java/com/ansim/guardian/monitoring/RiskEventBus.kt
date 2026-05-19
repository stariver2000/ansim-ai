package com.ansim.guardian.monitoring

import com.ansim.guardian.domain.model.RiskResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// 서비스 → ViewModel 이벤트 채널
// NotificationMonitorService, SmsReceiver 등이 위험을 감지하면 여기로 전달
object RiskEventBus {
    private val _events = MutableSharedFlow<RiskEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RiskEvent> = _events.asSharedFlow()

    suspend fun emit(event: RiskEvent) = _events.emit(event)
}

data class RiskEvent(
    val riskResult: RiskResult,
    val sourceLabel: String  // "카카오톡 알림", "문자", "앱 설치" 등
)
