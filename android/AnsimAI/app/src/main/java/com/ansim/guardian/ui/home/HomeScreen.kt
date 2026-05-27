package com.ansim.guardian.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansim.guardian.domain.engine.LlmStatus
import com.ansim.guardian.ui.theme.*

@Composable
fun HomeScreen(
    onAnalyze: (String) -> Unit,
    onOpenGuardianSetup: () -> Unit = {},
    onOpenPermissionSetup: () -> Unit = {},
    onOpenAlertLog: () -> Unit = {},
    onSimulateNotification: (String) -> Unit = {},
    onToggleMonitoring: () -> Unit = {},
    onStartAgent: () -> Unit = {},
    isLoading: Boolean = false,
    guardianName: String = "",
    isGuardianConfigured: Boolean = false,
    isMonitoringEnabled: Boolean = true,
    alertLogCount: Int = 0,
    llmStatus: LlmStatus = LlmStatus.UNAVAILABLE,
    isAgentBusy: Boolean = false,
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 헤더
        HeaderSection()

        // [별돌봄 Phase 5] 음성 에이전트 진입
        AgentEntryCard(onStart = onStartAgent, isBusy = isAgentBusy)

        // 보호자 / 권한 설정 버튼
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenGuardianSetup, modifier = Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                Text(if (isGuardianConfigured) "👨‍👩‍👧 $guardianName" else "👨‍👩‍👧 보호자 설정", style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = onOpenPermissionSetup, modifier = Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                Text("🔔 권한 설정", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // 위험 기록 버튼
        OutlinedButton(
            onClick = onOpenAlertLog,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (alertLogCount > 0) "⚠️ 위험 감지 기록  (${alertLogCount}건)" else "⚠️ 위험 감지 기록",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // 감시 On/Off + 상태 카드
        MonitoringStatusCard(
            isEnabled = isMonitoringEnabled,
            onToggle = onToggleMonitoring,
            llmStatus = llmStatus
        )

        // 텍스트 입력
        InputSection(
            text = inputText,
            onTextChange = { inputText = it },
            onAnalyze = { if (inputText.isNotBlank()) onAnalyze(inputText) },
            isLoading = isLoading
        )

        // 배경 감지 테스트
        TestSection(onSimulate = onSimulateNotification)

        // 안내 문구
        GuideSection()
    }
}

@Composable
private fun AgentEntryCard(onStart: () -> Unit, isBusy: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🎙️",
                fontSize = 56.sp
            )
            Text(
                text = "별돌봄에게 말씀하세요",
                style = MaterialTheme.typography.titleLarge,
                color = PrimaryBlue,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "예) \"큰애한테 전화해줘\"\n     \"이 문자 안전해?\"",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                enabled = !isBusy,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Text(
                        text = "🎙️  말씀 시작하기",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🛡️",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "안심동행 AI",
            style = MaterialTheme.typography.headlineLarge,
            color = PrimaryBlue,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "수상한 내용을 확인해드려요",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MonitoringStatusCard(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    llmStatus: LlmStatus = LlmStatus.UNAVAILABLE
) {
    val bgColor = if (isEnabled) SafeGreenLight else Color(0xFFF5F5F5)
    val textColor = if (isEnabled) SafeGreen else TextSecondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = if (isEnabled) "🛡️" else "😴", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnabled) "감시 중이에요" else "감시가 꺼져 있어요",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled) "문자·알림을 실시간으로 확인하고 있어요"
                           else "켜면 문자와 알림을 자동으로 확인해요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                // LLM 상태 배지
                if (isEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (llmStatus) {
                            LlmStatus.LOADING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = textColor
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("AI 준비 중...", style = MaterialTheme.typography.labelSmall, color = textColor)
                            }
                            LlmStatus.READY -> {
                                Text("🤖 Gemini AI 분석 사용 중", style = MaterialTheme.typography.labelSmall, color = textColor)
                            }
                            LlmStatus.UNAVAILABLE -> {
                                Text("📋 규칙 기반 감지 (오프라인)", style = MaterialTheme.typography.labelSmall, color = textColor)
                            }
                        }
                    }
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SafeGreen
                )
            )
        }
    }
}

@Composable
private fun InputSection(
    text: String,
    onTextChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    isLoading: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "수상한 문자나 대화를 붙여넣으세요",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            placeholder = {
                Text(
                    text = "예시:\n\"엄마 나야. 폰 고장났어. 급하게 50만원 보내줘.\"\n\n받은 문자나 카카오톡 내용을 여기에 붙여넣으세요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = onAnalyze,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            enabled = text.isNotBlank() && !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = "🔍  확인하기",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TestSection(onSimulate: (String) -> Unit) {
    val testCases = listOf(
        "🏛️ 보이스피싱" to "금감원입니다. 고객님 계좌가 범죄에 연루되었습니다. 자산을 보호하려면 안전계좌로 즉시 이체해 주세요.",
        "👨‍👩‍👧 가족 사칭" to "엄마 나야. 폰 고장났어. 급하니까 아빠한테 말하지 말고 이 계좌로 50만원 보내줘.",
        "💰 투자 사기" to "오늘만 가능합니다. 내부정보로 내일 상한가 확실합니다. 원금보장에 수익보장. VIP방 초대해드릴게요.",
        "🪙 코인 사기" to "수익이 났어요! 출금하려면 세금 먼저 입금하셔야 합니다. 보증금 입금 후 전액 출금 가능합니다."
    )

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🧪 배경 감지 테스트",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF795548)
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "접기" else "펼치기", color = Color(0xFF795548))
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "버튼을 누르면 카카오톡 알림이 수신된 것처럼 시뮬레이션합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9E9E9E)
                )
                Spacer(Modifier.height(12.dp))
                testCases.forEach { (label, message) ->
                    OutlinedButton(
                        onClick = { onSimulate(message) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF795548))
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "이런 내용은 꼭 확인하세요",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                "📱 새 번호로 가족이 돈을 달라는 문자",
                "💰 원금보장, 고수익 투자 권유",
                "🏛️ 검찰·경찰·금감원을 사칭하는 전화",
                "🔗 택배나 청첩장 링크",
                "🖥️ 원격제어 앱 설치 요청"
            ).forEach { item ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }
    }
}
