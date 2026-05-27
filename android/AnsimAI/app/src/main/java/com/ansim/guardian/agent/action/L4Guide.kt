package com.ansim.guardian.agent.action

import android.util.Log

/**
 * L4 어댑터 (Phase 3 stub) — 위험 액션은 *자동 실행하지 않음*.
 *
 * Phase 9에서 정식 가이드 카드 시스템 구현:
 *  - assets/agent/guide_cards/ 큐레이션된 카드 (앱·작업별)
 *  - 오버레이로 화살표 + 단계별 TTS 안내
 *  - 어르신이 *직접* 누르도록 유도
 *
 * Phase 3 현재: 도구 호출은 받아들이되 TTS로 *왜 자동으로 안 하는지* 설명만.
 *
 * 자세한 설계: BYULDOLBOM_V3_DESIGN_KO.md D2, AGENT_TOOL_CATALOG_KO.md §1
 */
class L4Guide {

    fun appGuideStart(task: String, downgradedFrom: String? = null): ActionResult {
        Log.i(TAG, "L4 guide requested: task=$task, downgradedFrom=$downgradedFrom")
        val msg = when (task) {
            "bank_send_money" -> "송금은 어르신이 직접 눌러주셔야 안전해요. 이건 큰애와 같이 하시는 게 좋아요."
            "toss_otp_input", "otp_input" -> "OTP 비밀번호는 어르신만 입력하셔야 해요."
            "password_change" -> "비밀번호 바꾸는 건 직접 하셔야 안전해요. 큰애한테 같이 봐달라고 하세요."
            "coupang_card_register" -> "카드 등록은 직접 입력하셔야 해요. 큰애한테 부탁드리는 게 좋아요."
            "photo_send_to_family" -> "사진 보내기 가이드는 곧 만들어드릴게요."
            else -> "이건 어르신이 직접 누르셔야 해요. 가이드는 다음에 만들어드릴게요."
        }
        return ActionResult.Success(message = msg, data = mapOf("guide_task" to task))
    }

    fun guideCardShow(cardId: String?, targetApp: String?): ActionResult {
        // Phase 9에서 helper_card 큐레이션 + 오버레이 띄움
        return ActionResult.Success(
            message = "이 안내 카드는 곧 만들어드릴게요.",
            data = mapOf("card_id" to (cardId ?: ""), "target_app" to (targetApp ?: ""))
        )
    }

    companion object { private const val TAG = "L4Guide" }
}
