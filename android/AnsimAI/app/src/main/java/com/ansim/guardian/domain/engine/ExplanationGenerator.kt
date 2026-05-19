package com.ansim.guardian.domain.engine

import com.ansim.guardian.domain.model.Explanation
import com.ansim.guardian.domain.model.RiskInput
import com.ansim.guardian.domain.model.RiskResult
import com.ansim.guardian.domain.model.ScamCase

interface ExplanationGenerator {
    suspend fun generateExplanation(
        input: RiskInput,
        riskResult: RiskResult,
        cases: List<ScamCase>
    ): Explanation
}
