package com.ansim.guardian.agent.pairing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

/**
 * NAS 페어링 페이로드 파서 단위 테스트 (순수 로직 — Android 프레임워크 비의존).
 * org.json 실제 구현은 testImplementation("org.json:json")으로 제공.
 */
class NasPairingTest {

    private fun jwtWithExp(expSec: Long): String {
        val header = b64url("""{"alg":"HS256","typ":"JWT"}""")
        val payload = b64url("""{"sub":"dev","exp":$expSec}""")
        return "$header.$payload.sig"  // 서명은 폰이 검증 안 함(NAS 책임)
    }

    private fun b64url(s: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    @Test
    fun `유효 페이로드 파싱`() {
        val token = jwtWithExp(2_000_000_000L)
        val r = NasPairing.fromQrPayload(
            """{"v":1,"base_url":"http://10.7.0.1:8002/","device_token":"$token"}"""
        )
        assertThat(r.isSuccess).isTrue()
        val p = r.getOrThrow()
        assertThat(p.baseUrl).isEqualTo("http://10.7.0.1:8002")  // trailing slash 제거
        assertThat(p.deviceToken).isEqualTo(token)
        assertThat(p.tokenExpEpochMs).isEqualTo(2_000_000_000L * 1000L)  // JWT exp에서 추출
    }

    @Test
    fun `token_exp 명시값이 JWT exp보다 우선`() {
        val token = jwtWithExp(2_000_000_000L)
        val p = NasPairing.fromQrPayload(
            """{"v":1,"base_url":"https://nas.example:8002","device_token":"$token","token_exp":1700000000}"""
        ).getOrThrow()
        assertThat(p.tokenExpEpochMs).isEqualTo(1_700_000_000L * 1000L)
    }

    @Test
    fun `device_token 누락이면 실패`() {
        assertThat(
            NasPairing.fromQrPayload("""{"v":1,"base_url":"http://10.7.0.1:8002"}""").isFailure
        ).isTrue()
    }

    @Test
    fun `base_url이 http가 아니면 실패`() {
        assertThat(
            NasPairing.fromQrPayload("""{"v":1,"base_url":"10.7.0.1:8002","device_token":"t"}""").isFailure
        ).isTrue()
    }

    @Test
    fun `미지원 버전이면 실패`() {
        assertThat(
            NasPairing.fromQrPayload("""{"v":2,"base_url":"http://x:8002","device_token":"t"}""").isFailure
        ).isTrue()
    }

    @Test
    fun `깨진 JSON이면 실패`() {
        assertThat(NasPairing.fromQrPayload("not-json").isFailure).isTrue()
    }

    @Test
    fun `exp 없는 토큰은 만료시각 0 - 만료검사 생략`() {
        val noExp = "${b64url("{}")}.${b64url("""{"sub":"dev"}""")}.sig"
        val p = NasPairing.fromQrPayload(
            """{"v":1,"base_url":"http://x:8002","device_token":"$noExp"}"""
        ).getOrThrow()
        assertThat(p.tokenExpEpochMs).isEqualTo(0L)
        assertThat(p.isExpired(System.currentTimeMillis())).isFalse()
    }

    @Test
    fun `fromManual - 유효 주소+토큰`() {
        val token = jwtWithExp(2_000_000_000L)
        val p = NasPairing.fromManual("http://192.168.0.10:8002/", token).getOrThrow()
        assertThat(p.baseUrl).isEqualTo("http://192.168.0.10:8002")
        assertThat(p.deviceToken).isEqualTo(token)
        assertThat(p.tokenExpEpochMs).isEqualTo(2_000_000_000L * 1000L)
    }

    @Test
    fun `fromManual - http 아니거나 토큰 비면 실패`() {
        assertThat(NasPairing.fromManual("192.168.0.10:8002", "t").isFailure).isTrue()
        assertThat(NasPairing.fromManual("http://x:8002", "  ").isFailure).isTrue()
    }

    @Test
    fun `isExpired 경계`() {
        val p = NasPairing("http://x:8002", "t", tokenExpEpochMs = 1_000L)
        assertThat(p.isExpired(999L)).isFalse()
        assertThat(p.isExpired(1_000L)).isTrue()
        assertThat(p.isExpired(1_001L)).isTrue()
    }
}
