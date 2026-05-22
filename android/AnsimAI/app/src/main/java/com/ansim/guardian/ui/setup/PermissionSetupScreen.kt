package com.ansim.guardian.ui.setup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansim.guardian.ui.theme.*

data class PermissionItem(
    val title: String,
    val description: String,
    val emoji: String,
    val isGranted: Boolean,
    val onRequest: () -> Unit
)

@Composable
fun PermissionSetupScreen(
    onComplete: () -> Unit,
    onRequestSmsPermission: () -> Unit = {},
    hasNotificationPermission: Boolean,
    hasSmsPermission: Boolean,
    hasOverlayPermission: Boolean
) {
    val context = LocalContext.current

    val permissions = listOf(
        PermissionItem(
            title = "알림 읽기 권한",
            description = "카카오톡·텔레그램 알림을 분석해서 사기를 탐지해요",
            emoji = "🔔",
            isGranted = hasNotificationPermission,
            onRequest = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        ),
        PermissionItem(
            title = "문자 읽기 권한",
            description = "수신된 문자 메시지에서 스미싱·사기를 탐지해요",
            emoji = "💬",
            isGranted = hasSmsPermission,
            onRequest = onRequestSmsPermission
        ),
        PermissionItem(
            title = "다른 앱 위에 표시",
            description = "위험이 감지되면 화면 위에 즉시 경고를 띄워요",
            emoji = "🛡️",
            isGranted = hasOverlayPermission,
            onRequest = {
                val packageUri = Uri.parse("package:com.ansim.guardian")
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // fallback: 앱 정보 페이지로 이동
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    } catch (e2: Exception) {
                        context.startActivity(
                            Intent(Settings.ACTION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                }
            }
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("🛡️", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("보호 권한 설정", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Text("아래 권한을 허용해야 노인폰을 보호할 수 있어요", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        permissions.forEach { perm ->
            PermissionCard(perm)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("완료", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }

        Text(
            "권한을 허용하지 않아도 앱을 직접 열어서 텍스트를 붙여넣는 방식으로 사용할 수 있어요.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
        )
    }
}

@Composable
private fun PermissionCard(perm: PermissionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (perm.isGranted) SafeGreenLight else Color(0xFFF3F4F6)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(perm.emoji, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(perm.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(perm.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (perm.isGranted) {
                Text("✅", fontSize = 24.sp)
            } else {
                OutlinedButton(onClick = perm.onRequest, shape = RoundedCornerShape(8.dp)) {
                    Text("허용", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
