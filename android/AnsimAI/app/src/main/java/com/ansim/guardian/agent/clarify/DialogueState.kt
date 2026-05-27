package com.ansim.guardian.agent.clarify

/**
 * 다이얼로그 상태머신.
 * 자세한 설계: docs/codex-design/11-product-elderly/CLARIFICATION_DIALOGUE_KO.md §8
 */
sealed class DialogueState {
    object Idle : DialogueState()
    object Listening : DialogueState()
    data class Stt(val partial: String) : DialogueState()
    data class Nlu(val utterance: String) : DialogueState()
    data class Confirming(val prompt: String, val toolCallId: String) : DialogueState()
    data class Clarifying(val kind: ClarifyKind, val candidates: List<String>, val turn: Int) : DialogueState()
    data class Executing(val toolCallId: String) : DialogueState()
    data class Responding(val ttsText: String) : DialogueState()
    data class Done(val resultSummary: String) : DialogueState()
    data class Canceled(val reason: String) : DialogueState()
}

/**
 * Clarification 종류 (CLARIFICATION_DIALOGUE_KO.md §1):
 *  C1 — 위험 액션 confirm
 *  C2 — 모호한 person_ref
 *  C3 — NLU unknown
 *  C4 — STT 신뢰도 낮음
 *  C5 — 어르신 중단
 */
enum class ClarifyKind { C1_CONFIRM, C2_PERSON, C3_UNKNOWN, C4_STT_LOW, C5_CANCEL }
