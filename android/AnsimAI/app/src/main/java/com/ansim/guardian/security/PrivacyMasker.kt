package com.ansim.guardian.security

object PrivacyMasker {

    // 주민등록번호: 6자리-7자리 (앞뒤 숫자 없음)
    private val juminRegex = Regex("""(?<!\d)\d{6}-\d{7}(?!\d)""")

    // 전화번호: 0으로 시작하는 한국 번호 또는 +82
    private val phoneRegex = Regex("""(?<!\d)(0\d{1,2}[-\s]?\d{3,4}[-\s]?\d{4}|\+82[-\s]?\d{1,2}[-\s]?\d{3,4}[-\s]?\d{4})(?!\d)""")

    // 계좌번호: 세 부분이 하이픈으로 연결된 숫자 (전화번호와 구분: 0으로 시작하지 않음)
    private val accountRegex = Regex("""(?<![0\d])(\d{3,6}-\d{2,6}-\d{4,8})(?!\d)""")

    // 이메일
    private val emailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")

    // 인증번호: 인증/OTP/코드 등 맥락과 함께 나오는 숫자
    private val otpContextRegex = Regex("""(?:인증번호|인증 번호|OTP|otp|코드|code)[\s:는은이가]*(\d{4,8})""")

    fun mask(text: String): String {
        var result = text
        result = result.replace(juminRegex, "[주민등록번호]")
        result = result.replace(emailRegex, "[이메일]")
        result = result.replace(phoneRegex, "[전화번호]")
        result = result.replace(accountRegex, "[계좌번호]")
        result = result.replace(otpContextRegex) { match ->
            match.value.replace(match.groupValues[1], "[인증번호]")
        }
        return result
    }

    fun containsSensitiveInfo(text: String): Boolean =
        juminRegex.containsMatchIn(text) ||
        phoneRegex.containsMatchIn(text) ||
        accountRegex.containsMatchIn(text) ||
        emailRegex.containsMatchIn(text) ||
        otpContextRegex.containsMatchIn(text)
}
