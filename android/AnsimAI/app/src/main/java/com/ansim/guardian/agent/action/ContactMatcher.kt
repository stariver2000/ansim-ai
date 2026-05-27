package com.ansim.guardian.agent.action

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import org.json.JSONObject

/**
 * 어르신이 부른 호칭(person_ref)을 실제 연락처로 매칭.
 *
 * 매칭 순서 (CLARIFICATION_DIALOGUE_KO.md C2):
 *  1. assets/agent/contact_aliases.json 정규화 (큰애/큰아들/장남 → "큰애")
 *  2. 안드로이드 연락처에서 이름 부분 매칭
 *  3. 호칭과 동의어 모두 시도
 *  4. 후보 ≥2면 caller가 C2 clarify 실행
 *
 * READ_CONTACTS 권한 필요 (AndroidManifest).
 */
class ContactMatcher(private val context: Context) {

    private val aliasMap: Map<String, List<String>> by lazy { loadAliases() }

    data class Contact(
        val displayName: String,
        val phone: String,
        val matchedAlias: String?,
        val lastContactedAt: Long = 0L,  // 향후 통화 빈도 정렬용
    )

    /**
     * @return 후보 ≤3개 (사용 빈도 1순위부터). 없으면 빈 리스트.
     */
    fun resolve(personRef: String, max: Int = 3): List<Contact> {
        val normalized = personRef.trim()
        val candidates = mutableSetOf<String>()
        candidates += normalized

        // alias 정규화 — 직접 매칭되는 그룹의 동의어 모두 추가
        aliasMap.forEach { (canonical, synonyms) ->
            if (normalized == canonical || normalized in synonyms) {
                candidates += canonical
                candidates += synonyms
            }
        }

        val results = mutableListOf<Contact>()
        candidates.forEach { name ->
            results += queryContacts(name)
        }
        return results.distinctBy { it.phone }.take(max)
    }

    private fun queryContacts(name: String): List<Contact> {
        if (name.isBlank()) return emptyList()
        return try {
            val resolver = context.contentResolver
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED,
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            val out = mutableListOf<Contact>()
            cursor?.use {
                val idxName = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val idxNum = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idxLast = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED)
                while (it.moveToNext()) {
                    out += Contact(
                        displayName = it.getString(idxName) ?: "",
                        phone = it.getString(idxNum) ?: "",
                        matchedAlias = name,
                        lastContactedAt = if (idxLast >= 0) it.getLong(idxLast) else 0L,
                    )
                }
            }
            out.sortedByDescending { it.lastContactedAt }
        } catch (t: Throwable) {
            Log.w(TAG, "Contacts query failed for '$name': ${t.message}")
            emptyList()
        }
    }

    private fun loadAliases(): Map<String, List<String>> {
        return try {
            val json = context.assets.open("agent/contact_aliases.json")
                .bufferedReader().use { it.readText() }
            val obj = JSONObject(json).getJSONObject("aliases")
            buildMap {
                obj.keys().forEach { canonical ->
                    val arr = obj.getJSONArray(canonical)
                    put(canonical, (0 until arr.length()).map { arr.getString(it) })
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load aliases: ${t.message}")
            emptyMap()
        }
    }

    companion object { private const val TAG = "ContactMatcher" }
}
