package com.ansim.guardian.agent.pairing

import android.content.Context
import android.util.Log
import com.ansim.guardian.agent.escalate.NasConnection

/**
 * NAS 페어링 수명주기 — QR 수령 → 저장 → [NasConnection] 발급(NAS planner escalate 활성).
 *
 * 흐름:
 *  1. 자녀/어르신이 NAS 화면의 페어링 QR을 폰으로 스캔 → 원문 문자열을 [pair]에 전달.
 *  2. [NasPairing.fromQrPayload]가 base_url + Device JWT 파싱, [NasPairingStore]가 암호화 저장.
 *  3. AgentSessionFactory가 [currentConnection]을 읽어 NasPlannerHttp를 조립 → escalate 동작.
 *
 * wg 터널(개인키 생성·VpnService·AllowedIPs)은 P1/플랫폼 wg 레이어가 별도로 기동한다 — 여기선
 * 그 위에서 도달 가능한 baseUrl + 토큰만 다룬다(메모리 byulserver-vpn-architecture, A안).
 */
class NasPairingManager(context: Context) {

    private val store = NasPairingStore(context)

    /**
     * 페어링 QR/딥링크 페이로드를 받아 검증·저장.
     * @return 저장된 [NasPairing] 또는 파싱 실패 사유.
     */
    fun pair(qrPayload: String): Result<NasPairing> =
        NasPairing.fromQrPayload(qrPayload).onSuccess { pairing ->
            store.save(pairing)
            Log.i(TAG, "NAS 페어링 저장 완료: ${pairing.baseUrl} (exp=${pairing.tokenExpEpochMs})")
        }.onFailure {
            Log.w(TAG, "NAS 페어링 실패: ${it.message}")
        }

    fun isPaired(): Boolean = store.isPaired()

    /** 현재 페어링(표시용 — baseUrl/만료 등). 미페어링이면 null. */
    fun currentPairing(): NasPairing? = store.load()

    /** 페어링 해제. */
    fun unpair() {
        store.clear()
        Log.i(TAG, "NAS 페어링 해제")
    }

    /**
     * 현재 페어링으로 [NasConnection] 생성. 미페어링이면 null → AgentSession은 폰 단독 모드.
     *
     * 토큰 공급자는 **호출 시점마다** 스토어를 다시 읽어 만료를 검사한다 → 만료/해제 시 즉시 null을
     * 돌려 NasPlannerHttp가 escalate를 조용히 건너뛴다(재페어링 안내는 상위 UX가 담당).
     */
    fun currentConnection(nowProvider: () -> Long = System::currentTimeMillis): NasConnection? {
        val pairing = store.load() ?: return null
        return NasConnection(
            baseUrl = pairing.baseUrl,
            deviceToken = {
                val current = store.load()
                when {
                    current == null -> null
                    current.isExpired(nowProvider()) -> {
                        Log.i(TAG, "Device JWT 만료 — escalate 건너뜀, 재페어링 필요")
                        null
                    }
                    else -> current.deviceToken
                }
            },
        )
    }

    companion object {
        private const val TAG = "NasPairingManager"
    }
}
