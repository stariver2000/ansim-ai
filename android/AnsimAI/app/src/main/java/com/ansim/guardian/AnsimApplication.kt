package com.ansim.guardian

import android.app.Application
import com.ansim.guardian.domain.engine.HybridRiskEngine

class AnsimApplication : Application() {

    /** 앱 전체 공유 엔진 (싱글톤) */
    val hybridEngine: HybridRiskEngine by lazy { HybridRiskEngine(this) }

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
