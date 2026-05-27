package com.ansim.guardian.agent.action

import com.ansim.guardian.domain.engine.RiskEngine
import com.ansim.guardian.domain.model.InputSource
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskLevel

/**
 * 안심동행AI [HybridRiskEngine]을 별돌봄 에이전트 도구 `scam_check_text`로 노출.
 *
 * 룰 247줄 + (옵트인 시) Gemini 외부 LLM 백엔드가 그대로 활용됨.
 * 정책: byulserver-safety-skill 옵트인 예외 — 어르신이 토글 ON일 때만 외부 호출.
 *
 * 결과 메시지는 [TtsCopyProvider] `result_scam.{level}` 키를 참조해야 풀 카피화 가능.
 * Phase 3 PoC는 간단 인라인 매핑.
 */
class ScamCheckTool(private val engine: RiskEngine) {

    suspend fun checkText(text: String, senderHint: String? = null): ActionResult {
        val input = RiskInput(
            text = text,
            source = InputSource.MANUAL,
            senderInfo = senderHint ?: "",
        )
        val result = engine.analyze(input)
        val reason = result.detectedSignals.firstOrNull()?.description
            ?: result.primaryCategory?.toString()
            ?: "위험 신호"

        val msg = when (result.riskLevel) {
            RiskLevel.SAFE ->
                "이 메시지는 안전해 보여요. 그래도 모르는 곳에서 온 거면 조심하세요."
            RiskLevel.CAUTION ->
                "이 메시지는 조심하셔야 해요. ${reason}. 답장하기 전에 한 번 더 생각해 보세요."
            RiskLevel.DANGER ->
                "이 메시지는 위험해요. ${reason}. 답장이나 링크를 누르지 마세요."
            RiskLevel.CRITICAL ->
                "이 메시지는 사기예요. ${reason}. 절대로 답장이나 송금하지 마세요. 큰애에게 보여드릴까요?"
        }
        return ActionResult.Success(
            message = msg,
            data = mapOf(
                "level" to result.riskLevel.name,
                "score" to result.totalScore,
                "category" to (result.primaryCategory?.name ?: ""),
            )
        )
    }
}
