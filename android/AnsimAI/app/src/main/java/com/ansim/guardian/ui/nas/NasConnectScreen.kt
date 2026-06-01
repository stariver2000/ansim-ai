package com.ansim.guardian.ui.nas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "NAS 연결" 화면 — 집의 별서버(NAS)에 연결하는 페어링 진입점.
 *
 * 동작: NAS 화면에 뜬 연결 QR을 [📷 연결 QR 스캔]으로 찍으면, 읽은 원문을 그대로
 * [onPayloadScanned]로 넘긴다(파싱·저장은 NasPairingManager). QR이 담는 형식은 P1
 * Coordinator가 확정하므로(메모리 byulserver-vpn-architecture) 이 화면은 형식에 무관하다.
 *
 * wg 터널 자체(키 생성·VpnService)는 플랫폼 wg 레이어가 맡고, 여기선 앱 자격
 * (baseUrl + Device JWT) 표시·연결/해제만 한다.
 */
@Composable
fun NasConnectScreen(
    isPaired: Boolean,
    baseUrl: String,
    tokenExpEpochMs: Long,
    message: String,
    onPayloadScanned: (String) -> Unit,
    onUnpair: () -> Unit,
    onScanError: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("🔗", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text("NAS 연결", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Text("집의 별서버에 연결하면 더 똑똑하게 도와드려요", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        // 현재 연결 상태
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPaired) Color(0xFFE8F5E9) else Color(0xFFF3F4F6)
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isPaired) "✅ 연결됨" else "⚪ 아직 연결 안 됨",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaired) Color(0xFF2E7D32) else TextSecondary,
                )
                if (isPaired) {
                    Text("주소: $baseUrl", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    if (tokenExpEpochMs > 0) {
                        val exp = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(tokenExpEpochMs))
                        Text("연결 유효기간: $exp 까지", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    }
                } else {
                    Text(
                        "별서버 안 사도 폰 혼자서도 동작해요. 연결하면 가족 기능이 켜집니다.",
                        style = MaterialTheme.typography.bodyLarge, color = TextPrimary,
                    )
                }
            }
        }

        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = PrimaryBlue, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.weight(1f))

        // QR 스캔으로 연결 — Google Play 제공 스캐너 UI(별도 카메라 권한 불필요)
        Button(
            onClick = {
                GmsBarcodeScanning.getClient(context).startScan()
                    .addOnSuccessListener { barcode -> barcode.rawValue?.let(onPayloadScanned) }
                    .addOnCanceledListener { /* 사용자가 스캔 취소 — 조용히 */ }
                    .addOnFailureListener { onScanError() }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
        ) {
            Text(
                if (isPaired) "📷 다시 연결 QR 스캔" else "📷 연결 QR 스캔",
                style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold,
            )
        }

        if (isPaired) {
            OutlinedButton(
                onClick = onUnpair,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("연결 해제", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("뒤로", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }
    }
}
