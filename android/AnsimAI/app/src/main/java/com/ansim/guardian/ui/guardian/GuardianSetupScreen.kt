package com.ansim.guardian.ui.guardian

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansim.guardian.ui.theme.*

@Composable
fun GuardianSetupScreen(
    currentPhone: String,
    currentName: String,
    onSave: (name: String, phone: String) -> Unit,
    onSkip: () -> Unit
) {
    var name by remember { mutableStateOf(currentName.ifBlank { "" }) }
    var phone by remember { mutableStateOf(currentPhone) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("👨‍👩‍👧", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("보호자 설정", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Text("위험이 감지되면 보호자에게 문자로 알려드려요", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("보호자 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("보호자 이름 (예: 아들, 딸)", style = MaterialTheme.typography.bodyLarge) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("보호자 전화번호 (예: 010-1234-5678)", style = MaterialTheme.typography.bodyLarge) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("어떻게 알려드리나요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    "⚠️ 주의: 앱 알림으로 알려드려요",
                    "🚨 위험·매우 위험: 문자 메시지로 즉시 알려드려요"
                ).forEach {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onSave(name, phone) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            enabled = name.isNotBlank() && phone.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("저장하기", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }

        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("나중에 설정할게요", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }
    }
}
