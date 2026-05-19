package com.ansim.guardian.monitoring

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ansim.guardian.domain.model.RiskLevel
import com.ansim.guardian.domain.model.RiskResult

// 다른 앱 위에 경고 팝업을 띄우는 오버레이 매니저
// AndroidManifest에 SYSTEM_ALERT_WINDOW 권한 필요
// 사용자가 설정에서 "다른 앱 위에 표시" 권한을 직접 허용해야 함
class OverlayWarningManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null

    // minSdk=26(O) 이상이므로 항상 canDrawOverlays 체크만 하면 됨
    fun canShowOverlay(): Boolean = Settings.canDrawOverlays(context)

    fun show(riskResult: RiskResult, onGuardian: () -> Unit, onDismiss: () -> Unit) {
        if (!canShowOverlay()) return
        if (overlayView != null) dismiss()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = buildOverlayView(riskResult, onGuardian) {
            dismiss()
            onDismiss()
        }
        overlayView = view

        // minSdk=26(O) 이므로 TYPE_APPLICATION_OVERLAY 항상 사용 가능
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
        }

        wm.addView(view, params)
    }

    fun dismiss() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
    }

    private fun buildOverlayView(
        riskResult: RiskResult,
        onGuardian: () -> Unit,
        onDismiss: () -> Unit
    ): LinearLayout {
        val bgColor = when (riskResult.riskLevel) {
            RiskLevel.CRITICAL -> Color.parseColor("#B71C1C")
            RiskLevel.DANGER   -> Color.parseColor("#E65100")
            RiskLevel.CAUTION  -> Color.parseColor("#F57F17")
            RiskLevel.SAFE     -> Color.parseColor("#2E7D32")
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(48, 48, 48, 48)

            // 경고 이모지 + 제목
            addView(TextView(context).apply {
                text = "${riskResult.riskLevel.emoji}  ${riskResult.riskLevel.label}"
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            // 메시지
            addView(TextView(context).apply {
                text = "수상한 내용이 감지되었어요.\n돈을 보내거나 앱을 설치하기 전에\n보호자에게 먼저 확인하세요."
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(0, 16, 0, 16)
            })

            // 감지된 신호 (최대 2개)
            riskResult.detectedSignals.take(2).forEach { signal ->
                addView(TextView(context).apply {
                    text = "⚠ ${signal.description}"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                })
            }

            // 버튼 행
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 24, 0, 0)

                addView(Button(context).apply {
                    text = "보호자에게 알리기"
                    textSize = 16f
                    setOnClickListener { onGuardian() }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 8
                })

                addView(Button(context).apply {
                    text = "닫기"
                    textSize = 16f
                    setOnClickListener { onDismiss() }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 8
                })
            })
        }
    }
}
