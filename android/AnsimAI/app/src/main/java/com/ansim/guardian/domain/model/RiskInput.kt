package com.ansim.guardian.domain.model

data class RiskInput(
    val text: String,
    val source: InputSource = InputSource.MANUAL,
    val senderInfo: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class InputSource(val displayName: String) {
    MANUAL("직접 입력"),
    SMS("문자"),
    NOTIFICATION_KAKAO("카카오톡"),
    NOTIFICATION_TELEGRAM("텔레그램"),
    NOTIFICATION_OTHER("알림"),
    CALL_STT("통화"),
    URL("링크"),
    APP_INSTALL("앱 설치")
}
