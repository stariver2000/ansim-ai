package com.ansim.guardian.agent.pairing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * NAS 페어링 자격(특히 Device JWT)을 **암호화 저장**한다.
 *
 * [POLICY SUMMARY]
 * Device JWT는 Bearer 자격 — 평문 SharedPreferences에 두지 않는다(CLAUDE.md §6 민감 데이터).
 * AES256_GCM(EncryptedSharedPreferences) + Keystore MasterKey로 보관.
 * 페어링은 NAS 주소 + 토큰뿐(원문 PII 없음). wg 키/터널 설정은 여기 두지 않는다(P1/플랫폼 wg 레이어 소관).
 *
 * Keystore가 손상/리셋된 기기에서 복호화가 실패할 수 있으므로(드묾), 읽기 실패는 throw 대신
 * "미페어링"으로 강등(clear 후 null) — 앱은 폰 단독 모드로 안전하게 떨어진다.
 */
class NasPairingStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy { open() }

    private fun open(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** 현재 저장된 페어링. 없거나 복호화 실패면 null. */
    fun load(): NasPairing? = runCatching {
        val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        NasPairing(
            baseUrl = baseUrl,
            deviceToken = token,
            tokenExpEpochMs = prefs.getLong(KEY_TOKEN_EXP_MS, 0L),
        )
    }.getOrElse {
        Log.w(TAG, "페어링 복호화 실패 — 미페어링으로 강등: ${it.message}")
        runCatching { clear() }
        null
    }

    /** 페어링 저장(기존 값 덮어씀). */
    fun save(pairing: NasPairing) {
        prefs.edit()
            .putString(KEY_BASE_URL, pairing.baseUrl)
            .putString(KEY_TOKEN, pairing.deviceToken)
            .putLong(KEY_TOKEN_EXP_MS, pairing.tokenExpEpochMs)
            .apply()
    }

    fun isPaired(): Boolean = load() != null

    /** 페어링 해제(자녀가 NAS 연결 끊기, 또는 복호화 실패 복구). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "NasPairingStore"
        private const val PREFS_NAME = "byul_nas_pairing"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_TOKEN_EXP_MS = "token_exp_ms"
    }
}
