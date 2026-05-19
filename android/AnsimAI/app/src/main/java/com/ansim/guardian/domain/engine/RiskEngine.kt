package com.ansim.guardian.domain.engine

import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskResult

interface RiskEngine {
    suspend fun analyze(input: RiskInput): RiskResult
}
