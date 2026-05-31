package com.ansim.guardian

import android.app.Application
import com.ansim.guardian.agent.AgentSession
import com.ansim.guardian.agent.AgentSessionFactory
import com.ansim.guardian.domain.engine.HybridRiskEngine

class AnsimApplication : Application() {

    /** 앱 전체 공유 엔진 (싱글톤) */
    val hybridEngine: HybridRiskEngine by lazy { HybridRiskEngine(this) }

    /** [별돌봄 Phase 3+] AgentSession factory. STT/NLU/TTS/Action 모든 의존성 조립. */
    val agentSessionFactory: AgentSessionFactory by lazy {
        AgentSessionFactory(this, hybridEngine)
    }

    /** lazy 단일 세션. 첫 사용 시 initializeAll()을 호출자가 책임. */
    val agentSession: AgentSession by lazy { agentSessionFactory.build() }

    companion object {
        lateinit var instance: AnsimApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        hybridEngine  // 초기화 (서버 방식은 즉시 READY)
    }
}
