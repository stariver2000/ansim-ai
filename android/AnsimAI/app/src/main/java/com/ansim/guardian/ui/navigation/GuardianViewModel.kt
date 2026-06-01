package com.ansim.guardian.ui.navigation

import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ansim.guardian.AnsimApplication
import com.ansim.guardian.data.local.AlertLogEntity
import com.ansim.guardian.data.local.AppDatabase
import com.ansim.guardian.domain.engine.HybridRiskEngine
import com.ansim.guardian.domain.engine.LlmStatus
import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.*
import com.ansim.guardian.monitoring.GuardianNotificationManager
import com.ansim.guardian.monitoring.MonitoringPrefs
import com.ansim.guardian.monitoring.RiskEvent
import com.ansim.guardian.monitoring.RiskEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GuardianUiState(
    val isAnalyzing: Boolean = false,
    val currentResult: RiskResult? = null,
    val isExplanationLoading: Boolean = false,
    val guardianNotified: Boolean = false,
    // 권한 상태
    val hasNotificationPermission: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasMicPermission: Boolean = false,
    // 보호자 설정
    val guardianName: String = "",
    val guardianPhone: String = "",
    // 백그라운드 이벤트
    val backgroundRiskEvent: RiskEvent? = null,
    // 감시 On/Off
    val isMonitoringEnabled: Boolean = true,
    // 위험 기록
    val alertLogs: List<AlertLogEntity> = emptyList(),
    // 서버 AI 상태
    val llmStatus: LlmStatus = LlmStatus.READY,
    // [별돌봄 Phase 5] 음성 에이전트 상태
    val isAgentBusy: Boolean = false,
    val lastAgentMessage: String = "",
    // [별돌봄 Phase 11] NAS 연결(페어링) 상태
    val isNasPaired: Boolean = false,
    val nasBaseUrl: String = "",
    val nasTokenExpEpochMs: Long = 0L,
    val nasPairMessage: String = "",
)

class GuardianViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GuardianUiState())
    val uiState: StateFlow<GuardianUiState> = _uiState.asStateFlow()

    private val db = AppDatabase.getInstance(application)
    private val alertLogDao = db.alertLogDao()
    private val ruleEngine = RuleBasedRiskEngine()
    private val hybridEngine = AnsimApplication.instance.hybridEngine
    private val guardianManager = GuardianNotificationManager(application)
    private val nasPairing = AnsimApplication.instance.nasPairingManager

    init {
        // 백그라운드 이벤트 수신
        viewModelScope.launch {
            RiskEventBus.events.collect { event ->
                _uiState.value = _uiState.value.copy(backgroundRiskEvent = event)
                guardianManager.notifyAll(event.riskResult, event.sourceLabel)
                saveAlertLog(event.riskResult, event.sourceLabel)
            }
        }

        // 위험 기록 실시간 구독
        viewModelScope.launch {
            alertLogDao.getAllFlow().collect { logs ->
                _uiState.value = _uiState.value.copy(alertLogs = logs)
            }
        }

        // 서버 상태 구독
        viewModelScope.launch {
            hybridEngine.llmStatus.collect { status ->
                _uiState.value = _uiState.value.copy(llmStatus = status)
            }
        }

        loadInitialState()
    }

    private fun loadInitialState() {
        val app = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            hasOverlayPermission = Settings.canDrawOverlays(app),
            guardianName = guardianManager.guardianName,
            guardianPhone = guardianManager.guardianPhone,
            isMonitoringEnabled = MonitoringPrefs.isEnabled(app)
        )
        checkPermissions()
        refreshNasPairing()
    }

    // ───────────────────────────────────────────────────────────
    // [별돌봄 Phase 11] NAS 연결(페어링)
    // ───────────────────────────────────────────────────────────

    /** 저장된 페어링을 UI 상태로 반영. */
    fun refreshNasPairing() {
        val p = nasPairing.currentPairing()
        _uiState.value = _uiState.value.copy(
            isNasPaired = p != null,
            nasBaseUrl = p?.baseUrl ?: "",
            nasTokenExpEpochMs = p?.tokenExpEpochMs ?: 0L,
        )
    }

    /** 페어링 QR에서 읽은 원문 페이로드를 검증·저장. 성공/실패 메시지를 상태로. */
    fun onNasQrScanned(payload: String) {
        nasPairing.pair(payload)
            .onSuccess {
                _uiState.value = _uiState.value.copy(nasPairMessage = "NAS에 연결됐어요.")
                refreshNasPairing()
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    nasPairMessage = "연결 QR을 읽지 못했어요. 다시 시도해 주세요."
                )
            }
    }

    /** 수동/개발용 연결 — NAS 주소 + 연결 토큰 직접 입력(같은 LAN 시험용). */
    fun onNasManualConnect(baseUrl: String, token: String) {
        nasPairing.pairManual(baseUrl, token)
            .onSuccess {
                _uiState.value = _uiState.value.copy(nasPairMessage = "NAS에 연결됐어요.")
                refreshNasPairing()
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    nasPairMessage = it.message ?: "연결 정보가 올바르지 않아요."
                )
            }
    }

    /** NAS 연결 해제. */
    fun unpairNas() {
        nasPairing.unpair()
        _uiState.value = _uiState.value.copy(nasPairMessage = "NAS 연결을 끊었어요.")
        refreshNasPairing()
    }

    fun clearNasPairMessage() {
        _uiState.value = _uiState.value.copy(nasPairMessage = "")
    }

    fun checkPermissions() {
        val app = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            hasOverlayPermission = Settings.canDrawOverlays(app),
            hasSmsPermission = ContextCompat.checkSelfPermission(
                app, android.Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED,
            hasNotificationPermission = androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(app).contains(app.packageName),
            hasMicPermission = ContextCompat.checkSelfPermission(
                app, android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // ───────────────────────────────────────────────────────────
    // [별돌봄 Phase 5] 음성 에이전트 한 라운드 실행
    // ───────────────────────────────────────────────────────────

    private var agentInitialized = false

    fun startAgentSession() {
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(
                app, android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _uiState.value = _uiState.value.copy(
                lastAgentMessage = "마이크 권한을 먼저 허용해 주세요."
            )
            return
        }
        if (_uiState.value.isAgentBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAgentBusy = true, lastAgentMessage = "")
            try {
                val session = AnsimApplication.instance.agentSession
                if (!agentInitialized) {
                    val r = session.initializeAll()
                    if (r.isFailure) {
                        _uiState.value = _uiState.value.copy(
                            lastAgentMessage = "준비가 안 됐어요: ${r.exceptionOrNull()?.message ?: "알 수 없음"}",
                            isAgentBusy = false,
                        )
                        return@launch
                    }
                    agentInitialized = true
                }
                val result = session.handleOneTurn()
                _uiState.value = _uiState.value.copy(
                    lastAgentMessage = result.toShortString(),
                    isAgentBusy = false,
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    lastAgentMessage = "문제가 생겼어요: ${t.message ?: t.javaClass.simpleName}",
                    isAgentBusy = false,
                )
            }
        }
    }

    private fun com.ansim.guardian.agent.TurnResult.toShortString(): String = when (this) {
        is com.ansim.guardian.agent.TurnResult.Executed -> message
        is com.ansim.guardian.agent.TurnResult.Clarified -> "다시 한 번 말씀해 주세요."
        is com.ansim.guardian.agent.TurnResult.Canceled -> "취소했어요."
        is com.ansim.guardian.agent.TurnResult.Failed -> message
    }

    fun toggleMonitoring() {
        val app = getApplication<Application>()
        val newValue = !_uiState.value.isMonitoringEnabled
        MonitoringPrefs.setEnabled(app, newValue)
        _uiState.value = _uiState.value.copy(isMonitoringEnabled = newValue)
    }

    fun analyze(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                currentResult = null
            )

            val input = RiskInput(text = text, source = InputSource.MANUAL)

            // 1단계: 규칙 엔진 즉시 표시
            val ruleResult = ruleEngine.analyze(input)
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                currentResult = ruleResult,
                isExplanationLoading = ruleResult.riskLevel != RiskLevel.SAFE
            )

            // 2단계: CAUTION 이상이면 서버(Gemini) 재검토
            if (ruleResult.riskLevel != RiskLevel.SAFE) {
                val serverResult = withContext(Dispatchers.IO) { hybridEngine.analyze(input) }
                if (serverResult.riskLevel != RiskLevel.SAFE) {
                    _uiState.value = _uiState.value.copy(currentResult = serverResult)
                    saveAlertLog(serverResult, "직접 입력")
                } else {
                    saveAlertLog(ruleResult, "직접 입력")
                }
                _uiState.value = _uiState.value.copy(isExplanationLoading = false)

                if (ruleResult.isDangerous) {
                    guardianManager.notifyAll(
                        _uiState.value.currentResult ?: ruleResult, "직접 입력"
                    )
                }
            }
        }
    }

    private suspend fun saveAlertLog(result: RiskResult, sourceLabel: String) {
        if (result.riskLevel == RiskLevel.SAFE) return
        val category = result.primaryCategory?.displayName ?: "알 수 없음"
        val reason = result.llmReason
            ?: result.detectedSignals.firstOrNull()?.description
            ?: "위험 신호 감지됨"
        val method = if (result.isLlmDetected) "AI 분석" else "규칙 기반"

        alertLogDao.insert(
            AlertLogEntity(
                timestamp = result.timestamp,
                riskLevel = result.riskLevel.name,
                riskEmoji = result.riskLevel.emoji,
                category = category,
                sourceLabel = sourceLabel,
                contentPreview = result.input.text.take(120),
                detectionMethod = method,
                reason = reason
            )
        )
    }

    fun deleteAlertLog(id: Long) = viewModelScope.launch { alertLogDao.deleteById(id) }
    fun clearAllAlertLogs() = viewModelScope.launch { alertLogDao.deleteAll() }

    fun saveGuardian(name: String, phone: String) {
        guardianManager.guardianName = name
        guardianManager.guardianPhone = phone
        _uiState.value = _uiState.value.copy(guardianName = name, guardianPhone = phone)
    }

    fun notifyGuardian() {
        val result = _uiState.value.currentResult ?: return
        viewModelScope.launch {
            guardianManager.notifyAll(result, "사용자 요청")
            _uiState.value = _uiState.value.copy(guardianNotified = true)
        }
    }

    fun clearBackgroundEvent() {
        _uiState.value = _uiState.value.copy(backgroundRiskEvent = null)
    }

    fun simulateNotification(message: String, label: String = "카카오톡 알림 (테스트)") {
        viewModelScope.launch {
            val input = RiskInput(text = message, source = InputSource.NOTIFICATION_KAKAO, senderInfo = "테스트")
            val result = withContext(Dispatchers.IO) { hybridEngine.analyze(input) }
            RiskEventBus.emit(RiskEvent(result, label))
        }
    }

    fun reset() {
        _uiState.value = GuardianUiState(
            guardianName = guardianManager.guardianName,
            guardianPhone = guardianManager.guardianPhone,
            hasOverlayPermission = Settings.canDrawOverlays(getApplication()),
            isMonitoringEnabled = _uiState.value.isMonitoringEnabled,
            alertLogs = _uiState.value.alertLogs
        )
    }
}
