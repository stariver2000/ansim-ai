package com.ansim.guardian.ui.risk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansim.guardian.domain.model.Explanation
import com.ansim.guardian.domain.model.RiskLevel
import com.ansim.guardian.domain.model.RiskResult
import com.ansim.guardian.ui.theme.*

@Composable
fun RiskResultScreen(
    riskResult: RiskResult,
    explanation: Explanation?,
    isExplanationLoading: Boolean,
    hasFinancialResults: Boolean = false,
    isFinancialLoading: Boolean = false,
    onCallGuardian: () -> Unit,
    onViewFinancial: () -> Unit = {},
    onBack: () -> Unit
) {
    val (bgColor, accentColor, emoji) = riskResult.riskLevel.toColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
    ) {
        // 즉시 경고 배너 (LLM 기다리지 않고 바로 표시)
        ImmediateWarningBanner(
            riskLevel = riskResult.riskLevel,
            emoji = emoji,
            accentColor = accentColor
        )

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 위험 신호 목록
            if (riskResult.detectedSignals.isNotEmpty()) {
                DetectedSignalsCard(riskResult)
            }

            // AI 설명 (비동기로 채워짐)
            ExplanationCard(
                explanation = explanation,
                isLoading = isExplanationLoading
            )

            // 금융 위험 분석 버튼 (MVP 4)
            if (isFinancialLoading || hasFinancialResults) {
                OutlinedButton(
                    onClick = onViewFinancial,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isFinancialLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isFinancialLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("투자 위험 분석 중...", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text("📊  투자 위험 분석 보기", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // 보호자 연락 버튼 (항상 표시)
            GuardianContactButton(
                riskLevel = riskResult.riskLevel,
                onClick = onCallGuardian
            )

            // 돌아가기 버튼
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "처음으로",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ImmediateWarningBanner(
    riskLevel: RiskLevel,
    emoji: String,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(accentColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = emoji, fontSize = 56.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = riskLevel.label,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (riskLevel == RiskLevel.CRITICAL || riskLevel == RiskLevel.DANGER) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "돈을 보내기 전에\n보호자에게 먼저 확인하세요",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DetectedSignalsCard(riskResult: RiskResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "발견된 위험 신호",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            riskResult.detectedSignals.take(4).forEach { signal ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚠️ ", fontSize = 18.sp)
                    Text(
                        text = "${signal.description}이(가) 감지되었어요",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplanationCard(
    explanation: Explanation?,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AI 설명",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (explanation == null && isLoading) {
                Text(
                    text = "자세한 설명을 준비하고 있어요...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            } else if (explanation != null) {
                ExplanationContent(explanation)
            }
        }
    }
}

@Composable
private fun ExplanationContent(explanation: Explanation) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 요약
        Text(
            text = explanation.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )

        // 왜 위험한지
        if (explanation.whyDangerous.isNotEmpty()) {
            ExplanationSection(
                title = "왜 조심해야 하나요",
                items = explanation.whyDangerous,
                bulletEmoji = "❗"
            )
        }

        // 하지 말 것
        if (explanation.doNotDo.isNotEmpty()) {
            ExplanationSection(
                title = "하지 말아야 할 행동",
                items = explanation.doNotDo,
                bulletEmoji = "🚫"
            )
        }

        // 지금 할 것
        if (explanation.doNow.isNotEmpty()) {
            ExplanationSection(
                title = "지금 해야 할 행동",
                items = explanation.doNow,
                bulletEmoji = "✅"
            )
        }

        // 보호자에게 할 말
        if (explanation.askGuardian.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "보호자에게 이렇게 말해보세요",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = explanation.askGuardian,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PrimaryBlue
                    )
                }
            }
        }

        // 유사 사례
        explanation.similarCase?.let { case ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "비슷한 사례",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CautionYellow
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = case.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = case.recommendedAction,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplanationSection(
    title: String,
    items: List<String>,
    bulletEmoji: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        items.forEach { item ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(text = "$bulletEmoji ", fontSize = 18.sp)
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun GuardianContactButton(
    riskLevel: RiskLevel,
    onClick: () -> Unit
) {
    val (bgColor, label) = when (riskLevel) {
        RiskLevel.CRITICAL, RiskLevel.DANGER ->
            Pair(CriticalRed, "🆘  지금 바로 보호자에게 알리기")
        else ->
            Pair(PrimaryBlue, "📞  보호자에게 알리기")
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun RiskLevel.toColors(): Triple<Color, Color, String> = when (this) {
    RiskLevel.SAFE -> Triple(SafeGreenLight, SafeGreen, "✅")
    RiskLevel.CAUTION -> Triple(CautionYellowLight, CautionYellow, "⚠️")
    RiskLevel.DANGER -> Triple(DangerOrangeLight, DangerOrange, "🚨")
    RiskLevel.CRITICAL -> Triple(CriticalRedLight, CriticalRed, "🆘")
}
