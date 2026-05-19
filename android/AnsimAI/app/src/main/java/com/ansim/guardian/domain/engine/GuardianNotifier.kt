package com.ansim.guardian.domain.engine

import com.ansim.guardian.domain.model.RiskResult

interface GuardianNotifier {
    suspend fun notifyIfNeeded(result: RiskResult)
}
