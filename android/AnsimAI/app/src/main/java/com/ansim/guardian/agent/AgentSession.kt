package com.ansim.guardian.agent

import com.ansim.guardian.agent.action.ActionRunner
import com.ansim.guardian.agent.clarify.DialogueState
import com.ansim.guardian.agent.escalate.NasPlanner
import com.ansim.guardian.agent.nlu.IntentRouter
import com.ansim.guardian.agent.nlu.ToolCall
import com.ansim.guardian.agent.stt.SpeechRecognizer
import com.ansim.guardian.agent.tts.TtsCopyProvider

/**
 * 별돌봄 에이전트 세션 오케스트레이션.
 *
 * 5단 파이프라인 진입점:
 *   wake → stt → nlu → action(L1~L4 분기) → tts
 *
 * 각 컴포넌트는 interface로 주입받아 Phase별로 구현 교체 가능.
 *
 * 자세한 설계: docs/codex-design/11-product-elderly/BYULDOLBOM_V3_DESIGN_KO.md
 */
class AgentSession(
    private val stt: SpeechRecognizer,
    private val nlu: IntentRouter,
    private val action: ActionRunner,
    private val nasPlanner: NasPlanner,
    private val ttsCopy: TtsCopyProvider
) {
    /**
     * 한 라운드의 어르신 요청 처리.
     * 호출: 마이크 버튼(Phase 5)이나 Wake Word(Phase 10)로 시작.
     *
     * Phase 0 PoC: 구현 미정. Phase 1~7에서 채움.
     */
    suspend fun handleOneTurn(): TurnResult {
        TODO("Phase 1~7에서 구현")
    }
}

sealed class TurnResult {
    data class Executed(val toolCall: ToolCall, val message: String) : TurnResult()
    data class Clarified(val state: DialogueState) : TurnResult()
    data class Canceled(val reason: String) : TurnResult()
    data class Failed(val errorCode: String, val message: String) : TurnResult()
}
