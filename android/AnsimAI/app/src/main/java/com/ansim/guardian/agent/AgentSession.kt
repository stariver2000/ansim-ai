package com.ansim.guardian.agent

import android.util.Log
import com.ansim.guardian.agent.action.ActionResult
import com.ansim.guardian.agent.action.ActionRunner
import com.ansim.guardian.agent.clarify.DialogueState
import com.ansim.guardian.agent.escalate.NasPlanner
import com.ansim.guardian.agent.nlu.AgentContext
import com.ansim.guardian.agent.nlu.IntentRouter
import com.ansim.guardian.agent.nlu.ToolCall
import com.ansim.guardian.agent.nlu.ToolCatalog
import com.ansim.guardian.agent.stt.SpeechRecognizer
import com.ansim.guardian.agent.stt.SttResult
import com.ansim.guardian.agent.tts.TextToSpeak
import com.ansim.guardian.agent.tts.TtsCopyProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 별돌봄 에이전트 세션 오케스트레이션. Phase 5 구현 — 최소 사이클.
 *
 *   1. TTS "네, 듣고 있어요"
 *   2. STT 시작 → 첫 final 결과 받음 (silence_timeout_session_ms 이내)
 *   3. NLU route → ToolCall
 *   4. (Phase 3에서 ActionRunner 통합 예정) — 현재는 도구 호출 자체를 TTS로 보고
 *   5. needs_confirm 도구면 confirm TTS + STT 응답 대기 (YES/NO)
 *   6. 결과 TTS
 *
 * Phase 3 통합 시 ActionRunner.run(toolCall)이 들어가고 결과 메시지를 TTS로.
 *
 * 자세한 설계:
 *  - docs/codex-design/11-product-elderly/BYULDOLBOM_V3_DESIGN_KO.md §2
 *  - docs/codex-design/11-product-elderly/CLARIFICATION_DIALOGUE_KO.md §8 상태머신
 */
class AgentSession(
    private val stt: SpeechRecognizer,
    private val nlu: IntentRouter,
    private val tts: TextToSpeak,
    private val ttsCopy: TtsCopyProvider,
    private val toolCatalog: ToolCatalog,
    private val action: ActionRunner,
    @Suppress("unused") private val nasPlanner: NasPlanner? = null,  // Phase 7+
    private val silenceTimeoutMs: Long = 7000,
    private val confirmTimeoutMs: Long = 3000,
) {
    /** 컴포넌트 초기화 (모델 로드 등). 무거우므로 앱 시작 시 한 번. */
    suspend fun initializeAll(): Result<Unit> = runCatching {
        stt.initialize().getOrThrow()
        nlu.initialize().getOrThrow()
        tts.initialize().getOrThrow()
    }

    /** 한 라운드의 어르신 요청 처리. 마이크 버튼 또는 Wake Word로 진입. */
    suspend fun handleOneTurn(context: AgentContext = AgentContext()): TurnResult {
        // 1) 시작 안내
        tts.speak(ttsCopy.get("session.wake_detected"))

        // 2) STT — 첫 final 결과까지
        val sttResult = withTimeoutOrNull(silenceTimeoutMs) {
            stt.startStreaming().first { it.isFinal && it.text.isNotBlank() }
        }
        if (sttResult == null) {
            tts.speak(ttsCopy.get("session.silence_5s"))
            return TurnResult.Canceled("silence_timeout")
        }
        Log.i(TAG, "STT: '${sttResult.text}' (conf=${sttResult.confidence})")

        // 3) NLU
        val call = nlu.route(sttResult.text, context)
        Log.i(TAG, "NLU: ${call.tool} ${call.args}")

        // 4) clarify면 질문 TTS 후 종료 (다음 turn에서 사용자가 다시 부름)
        if (call.tool == "clarify") {
            val q = call.argString("question") ?: ttsCopy.get("clarify.c3_unknown_first")
            tts.speak(q)
            return TurnResult.Clarified(DialogueState.Clarifying(
                kind = com.ansim.guardian.agent.clarify.ClarifyKind.C3_UNKNOWN,
                candidates = (call.args["candidates"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                turn = 1
            ))
        }

        // 5) 도구 정의 조회 + needs_confirm 확인 (ToolCatalog 일원화)
        val definition = toolCatalog.get(call.tool)
        if (definition == null) {
            tts.speak("그건 아직 못 도와드려요.")
            return TurnResult.Failed("unknown_tool", call.tool)
        }
        if (definition.needsConfirm) {
            val confirmText = ttsCopy.get("confirm.${call.tool}").let { copy ->
                if (copy.startsWith("(카피 없음")) "${call.tool} 진행할까요?" else copy
            }
            tts.speak(confirmText)
            val yes = waitYesNo()
            if (!yes) {
                tts.speak(ttsCopy.get("clarify.c5_cancel"))
                return TurnResult.Canceled("user_no")
            }
        }

        // 6) ActionRunner 실 실행 (Phase 3)
        val actionResult = action.run(call, definition)
        val ttsMessage = when (actionResult) {
            is ActionResult.Success -> actionResult.message.let { msg ->
                // result.* 카피 키면 사전 치환
                if (msg.startsWith("result_") || msg.startsWith("session.")) ttsCopy.get(msg) else msg
            }
            is ActionResult.Failed -> ttsCopy.get(actionResult.message).let { c ->
                if (c.startsWith("(카피 없음")) actionResult.message else c
            }
            is ActionResult.Canceled -> ttsCopy.get("clarify.c5_cancel")
            is ActionResult.Escalated -> "큰애한테 연결해드릴게요." // Phase 11에서 정식화
        }
        tts.speak(ttsMessage)

        return when (actionResult) {
            is ActionResult.Success -> TurnResult.Executed(call, ttsMessage)
            is ActionResult.Failed -> TurnResult.Failed(actionResult.errorCode, ttsMessage)
            is ActionResult.Canceled -> TurnResult.Canceled(actionResult.reason)
            is ActionResult.Escalated -> TurnResult.Executed(call, ttsMessage)
        }
    }

    private suspend fun waitYesNo(): Boolean {
        val response = withTimeoutOrNull(confirmTimeoutMs) {
            stt.startStreaming().first { it.isFinal && it.text.isNotBlank() }
        }
        val text = response?.text ?: return false  // 침묵 = NO
        return YES_PATTERN.containsMatchIn(text) && !NO_PATTERN.containsMatchIn(text)
    }

    fun release() {
        stt.release()
        nlu.release()
        tts.release()
    }

    companion object {
        private const val TAG = "AgentSession"
        // agent_constants.json dialogue.yes_pattern / no_pattern
        private val YES_PATTERN = Regex("(응|네|예|맞아|그래|좋아|맞아요)")
        private val NO_PATTERN = Regex("(아니|아니야|아니에요|취소|그만|싫어)")
    }
}

sealed class TurnResult {
    data class Executed(val toolCall: ToolCall, val message: String) : TurnResult()
    data class Clarified(val state: DialogueState) : TurnResult()
    data class Canceled(val reason: String) : TurnResult()
    data class Failed(val errorCode: String, val message: String) : TurnResult()
}
