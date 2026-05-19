package com.ansim.guardian.ui.financial

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
import com.ansim.guardian.financial.FinancialRiskResult
import com.ansim.guardian.financial.TickerExtractor
import com.ansim.guardian.ui.theme.*

@Composable
fun FinancialRiskScreen(
    financialResults: List<FinancialRiskResult>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📊", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "투자 위험 분석",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
        }

        if (financialResults.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeGreenLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("✅ 특별한 투자 위험이 감지되지 않았어요", style = MaterialTheme.typography.titleMedium, color = SafeGreen)
                }
            }
        } else {
            financialResults.forEach { result ->
                FinancialRiskCard(result)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("💡 투자 전 꼭 확인하세요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    "📋 금융감독원 전자공시(DART): dart.fss.or.kr",
                    "🏛️ 금융소비자 정보포털 파인: fine.fss.or.kr",
                    "📞 금융감독원 민원: 1332",
                    "🚨 보이스피싱 신고: 112"
                ).forEach {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("돌아가기", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FinancialRiskCard(result: FinancialRiskResult) {
    val bgColor = when {
        result.riskScore >= 80 -> CriticalRedLight
        result.riskScore >= 60 -> DangerOrangeLight
        result.riskScore >= 30 -> CautionYellowLight
        else -> SafeGreenLight
    }
    val accentColor = when {
        result.riskScore >= 80 -> CriticalRed
        result.riskScore >= 60 -> DangerOrange
        result.riskScore >= 30 -> CautionYellow
        else -> SafeGreen
    }

    val typeLabel = when (result.entity.type) {
        TickerExtractor.EntityType.STOCK -> "주식"
        TickerExtractor.EntityType.COIN -> "코인"
        TickerExtractor.EntityType.UNLISTED_STOCK -> "비상장주식"
        TickerExtractor.EntityType.UNKNOWN_INVESTMENT -> "투자"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[$typeLabel] ${result.entity.text}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "위험 ${result.riskScore}점",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            // 종목 정보
            result.stockInfo?.let { info ->
                if (info.isManaged || info.isSuspended) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = CriticalRed.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            if (info.isManaged) Text("⚠️ 관리종목 ", style = MaterialTheme.typography.bodyLarge, color = CriticalRed, fontWeight = FontWeight.Bold)
                            if (info.isSuspended) Text("🚫 거래정지", style = MaterialTheme.typography.bodyLarge, color = CriticalRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (info.recentDisclosures.isNotEmpty()) {
                    Text("최근 공시:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    info.recentDisclosures.take(3).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            // 위험 이유
            if (result.riskReasons.isNotEmpty()) {
                Text("위험한 이유:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                result.riskReasons.forEach {
                    Row {
                        Text("❗ ", fontSize = 16.sp)
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                }
            }

            // 권장 행동
            HorizontalDivider(color = accentColor.copy(alpha = 0.3f))
            Text(
                text = result.recommendation,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
