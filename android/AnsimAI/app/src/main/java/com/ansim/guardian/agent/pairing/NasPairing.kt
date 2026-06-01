package com.ansim.guardian.agent.pairing

import org.json.JSONObject
import java.util.Base64

/**
 * NAS 페어링 정보 — 별돌봄 폰이 NAS planner(8002)에 접속하기 위한 최소 자격.
 *
 * [POLICY SUMMARY / 아키텍처 경계]
 * 별서버 원격접속은 자체구축 WireGuard split-VPN "A안"(임베드 wg + central 릴레이).
 * → 메모리 byulserver-vpn-architecture. **wg 터널 자체(개인키 생성·VpnService·AllowedIPs)는
 *   P1/플랫폼(공통 android wg 임베드) 소관이다.** 별돌봄(elderly) 앱은 그 위에서
 *   **`baseUrl` + `deviceToken`(Device JWT)만** 소비한다("앱은 baseUrl만 영향").
 *
 * 즉 이 모델은 페어링 QR이 전달하는 *앱 계층 자격*만 담는다. wg 설정(공개키 교환·IP 배정)은
 * central Coordinator ↔ 플랫폼 wg 레이어가 처리하고, 그게 끝나면 [baseUrl]이 NAS의 wg IP로 도달 가능해진다.
 *
 * @param baseUrl        NAS planner 주소. 터널 기동 후 도달 가능 (예: "http://10.7.0.1:8002") 또는 같은 LAN.
 * @param deviceToken    Device JWT (Bearer). NAS가 페어링 시 발급, 매 /agent/plan 요청 헤더로.
 * @param tokenExpEpochMs JWT 만료(ms). 0이면 미상(만료 검사 생략). [parseJwtExpMs]로 토큰에서 추출.
 */
data class NasPairing(
    val baseUrl: String,
    val deviceToken: String,
    val tokenExpEpochMs: Long = 0L,
) {
    /**
     * nowMs 기준 토큰이 만료됐는가. tokenExpEpochMs=0(미상)이면 false(서버가 401로 거른다).
     * RFC 7519: 현재시각이 exp "이전"이어야 유효 → now >= exp면 만료(경계 포함).
     */
    fun isExpired(nowMs: Long): Boolean = tokenExpEpochMs in 1..nowMs

    companion object {
        const val SUPPORTED_VERSION = 1

        /**
         * 페어링 QR/딥링크 페이로드(JSON)를 파싱.
         *
         * 기대 형식(central Coordinator/NAS가 발급):
         * ```json
         * { "v": 1, "base_url": "http://10.7.0.1:8002", "device_token": "<JWT>" }
         * ```
         * `token_exp`(epoch초)가 있으면 우선 사용, 없으면 JWT의 exp 클레임에서 추출한다.
         *
         * @return 성공 시 [NasPairing]. 형식 오류/미지원 버전/필수 필드 누락이면 [Result.failure].
         */
        fun fromQrPayload(raw: String): Result<NasPairing> = runCatching {
            val o = JSONObject(raw)
            val v = o.optInt("v", 1)
            require(v == SUPPORTED_VERSION) { "지원하지 않는 페어링 버전: $v (지원=$SUPPORTED_VERSION)" }

            val baseUrl = o.optString("base_url").trim().trimEnd('/')
            require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
                "base_url이 http(s) URL이 아님: '$baseUrl'"
            }
            val token = o.optString("device_token").trim()
            require(token.isNotEmpty()) { "device_token 누락" }

            val expSec = o.optLong("token_exp", 0L)
            val expMs = if (expSec > 0) expSec * 1000L else parseJwtExpMs(token)

            NasPairing(baseUrl = baseUrl, deviceToken = token, tokenExpEpochMs = expMs)
        }

        /**
         * 수동/개발용 연결 — QR 없이 NAS LAN 주소 + Device JWT를 직접 입력해 페어링.
         * P1의 wg 임베드/QR 발급이 나오기 전, 같은 LAN에서 폰↔NAS planner 연결을 시험할 때 쓴다.
         *
         * @return 성공 시 [NasPairing]. baseUrl이 http(s)가 아니거나 토큰이 비면 [Result.failure].
         */
        fun fromManual(baseUrl: String, deviceToken: String): Result<NasPairing> = runCatching {
            val url = baseUrl.trim().trimEnd('/')
            require(url.startsWith("http://") || url.startsWith("https://")) {
                "주소가 http(s)로 시작해야 해요: '$url'"
            }
            val token = deviceToken.trim()
            require(token.isNotEmpty()) { "연결 토큰이 비어 있어요" }
            NasPairing(baseUrl = url, deviceToken = token, tokenExpEpochMs = parseJwtExpMs(token))
        }

        /**
         * JWT payload(가운데 세그먼트)의 `exp`(epoch초)를 ms로 반환. 서명 검증은 하지 않는다
         * (검증은 NAS 책임; 폰은 만료 UX·불필요 호출 회피용으로만 읽음). 실패 시 0.
         */
        fun parseJwtExpMs(jwt: String): Long = runCatching {
            val parts = jwt.split(".")
            if (parts.size < 2) return 0L
            val payloadJson = String(Base64.getUrlDecoder().decode(padBase64(parts[1])))
            JSONObject(payloadJson).optLong("exp", 0L) * 1000L
        }.getOrDefault(0L)

        /** base64url은 패딩이 없을 수 있어 4의 배수로 보정. */
        private fun padBase64(s: String): String = when (s.length % 4) {
            2 -> "$s=="
            3 -> "$s="
            else -> s
        }
    }
}
