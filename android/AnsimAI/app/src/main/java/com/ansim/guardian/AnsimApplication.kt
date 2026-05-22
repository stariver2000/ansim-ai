package com.ansim.guardian

import android.app.Application
import com.ansim.guardian.domain.engine.HybridRiskEngine

/**
 * 앱 전역 싱글톤
 * HybridRiskEngine(LLM 포함)을 하나만 생성 — 두 번 로드 방지
 */
class AnsimApplication : Application() {

    /** 앱 전체에서 공유하는 하이브리드 엔진 (LLM 포함) */
    val hybridEngine: HybridRiskEngine by lazy {
        HybridRiskEngine(this)
    }

    companion object {
        lateinit var instance: AnsimApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 앱 시작과 함께 LLM 백그라운드 로딩 시작 (lazy 초기화 트리거)
        hybridEngine
    }
}
