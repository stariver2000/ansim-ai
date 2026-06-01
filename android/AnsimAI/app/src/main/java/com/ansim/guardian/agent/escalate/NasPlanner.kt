package com.ansim.guardian.agent.escalate

import com.ansim.guardian.agent.nlu.AgentContext
import com.ansim.guardian.agent.nlu.ToolCall

/**
 * 폰 sLLM이 처리 못한 의도를 NAS planner(EXAONE-2.4B)에 escalate.
 *
 * Phase 7: Stub (항상 null 반환 → 폰이 "잘 모르겠어요" 폴백)
 * Phase 11: 실제 HTTP 구현 (NasPlannerHttp — 자체구축 WireGuard split VPN + Device JWT)
 *
 * 자세한 설계: docs/codex-design/11-product-elderly/NAS_PLANNER_DESIGN_KO.md
 */
interface NasPlanner {
    /**
     * @return ToolCall — NAS가 도구 계획을 반환하면 폰이 그대로 실행
     *         null — NAS 미연결·시간 초과·범위 밖이면 폰 단독 fallback
     */
    suspend fun plan(utterance: String, context: AgentContext): ToolCall?
}

/** Phase 7 PoC: 항상 null. */
class NasPlannerStub : NasPlanner {
    override suspend fun plan(utterance: String, context: AgentContext): ToolCall? = null
}
