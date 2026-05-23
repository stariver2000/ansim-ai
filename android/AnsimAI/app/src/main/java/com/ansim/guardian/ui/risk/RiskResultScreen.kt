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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansim.guardian.domain.model.RiskLevel
import com.ansim.guardian.domain.model.RiskResult
import com.ansim.guardian.ui.theme.*

@Composable
fun RiskResultScreen(
    riskResult: RiskResult,
    isAnalyzing: Boolean = false,
    onCallGuardian: () -> Unit,
    onBack: () -> Unit
) {
    val (bgColor, accentColor, emoji) = riskResult.riskLevel.toColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
    ) {
        // 상단 배너
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
                    text = riskResult.riskLevel.label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (riskResult.isDangerous) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "돈을 보내기 전에\n보호자에게 먼저 확인하세요",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI 분석 카드
            AIAnalysisCard(riskResult = riskResult, isAnalyzing = isAnalyzing)

            // 감지 신호
            if (riskResult.detectedSignals.isNotEmpty()) {
                DetectedSignalsCard(riskResult)
            }

            // 보호자 버튼
            val (btnColor, btnLabel) = when (riskResult.riskLevel) {
                RiskLevel.CRITICAL, RiskLevel.DANGER ->
                    Pair(CriticalRed, "🆘  지금 바로 보호자에게 알리기")
                else -> Pair(PrimaryBlue, "📞  보호자에게 알리기")
            }
            Button(
                onClick = onCallGuardian,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = btnColor)
            ) {
                Text(
                    text = btnLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("처음으로", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AIAnalysisCard(riskResult: RiskResult, isAnalyzing: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (riskResult.isLlmDetected) "🤖 AI 분석 결과" else "📋 분석 결과",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (isAnalyzing) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Gemini 분석 중...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (riskResult.detectedSignals.isNotEmpty()) {
                riskResult.detectedSignals.forEach { signal ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("❗ ", fontSize = 16.sp)
                        Text(
                            text = signal.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            } else if (!isAnalyzing) {
                Text(
                    text = "분석 중 특이사항이 발견되지 않았습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }

            // Gemini recommended_action (llmReason)
            riskResult.llmReason?.let { reason ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🤖 AI 판단 이유",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyLarge,
                            color = PrimaryBlue
                        )
                    }
                }
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
            riskResult.detectedSignals.take(5).forEach { signal ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️ ", fontSize = 16.sp)
                    Text(
                        text = signal.matchedKeyword,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private fun RiskLevel.toColors(): Triple<Color, Color, String> = when (this) {
    RiskLevel.SAFE -> Triple(SafeGreenLight, SafeGreen, "✅")
    RiskLevel.CAUTION -> Triple(CautionYellowLight, CautionYellow, "⚠️")
    RiskLevel.DANGER -> Triple(DangerOrangeLight, DangerOrange, "🚨")
    RiskLevel.CRITICAL -> Triple(CriticalRedLight, CriticalRed, "🆘")
}
