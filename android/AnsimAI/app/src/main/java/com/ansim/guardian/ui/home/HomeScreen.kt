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
import com.ansim.guardian.ui.theme.*

@Composable
fun HomeScreen(
    onAnalyze: (String) -> Unit,
    onOpenGuardianSetup: () -> Unit = {},
    onOpenPermissionSetup: () -> Unit = {},
    isLoading: Boolean = false,
    guardianName: String = "",
    isGuardianConfigured: Boolean = false
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

        // 보호자 / 권한 설정 버튼
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenGuardianSetup, modifier = Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                Text(if (isGuardianConfigured) "👨‍👩‍👧 $guardianName" else "👨‍👩‍👧 보호자 설정", style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = onOpenPermissionSetup, modifier = Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                Text("🔔 권한 설정", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // 상태 표시
        StatusCard()

        // 텍스트 입력
        InputSection(
            text = inputText,
            onTextChange = { inputText = it },
            onAnalyze = { if (inputText.isNotBlank()) onAnalyze(inputText) },
            isLoading = isLoading
        )

        // 안내 문구
        GuideSection()
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
private fun StatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SafeGreenLight)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "✅", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "지금은 안전해요",
                    style = MaterialTheme.typography.titleMedium,
                    color = SafeGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "수상한 내용이 있으면 아래에서 확인하세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeGreen
                )
            }
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
