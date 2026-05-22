package com.ansim.guardian.ui.navigation

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ansim.guardian.ai.DeviceCapabilityChecker
import com.ansim.guardian.ai.LocalLlmManager
import com.ansim.guardian.ai.TemplateExplanationGenerator
import com.ansim.guardian.ai.embedding.EmbeddingRepository
import com.ansim.guardian.ai.llm.DartApiKeyStore
import com.ansim.guardian.data.local.AlertLogEntity
import com.ansim.guardian.data.local.AppDatabase
import com.ansim.guardian.data.repository.ScamRepository
import com.ansim.guardian.domain.engine.HybridRiskEngine
import com.ansim.guardian.domain.engine.LlmStatus
import com.ansim.guardian.domain.engine.LocalRagEngine
import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.*
import com.ansim.guardian.financial.DartApiClient
import com.ansim.guardian.financial.FinancialRiskResult
import com.ansim.guardian.financial.StockRiskAnalyzer
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
    val explanation: Explanation? = null,
    val isExplanationLoading: Boolean = false,
    val financialResults: List<FinancialRiskResult> = emptyList(),
    val isFinancialLoading: Boolean = false,
    val guardianNotified: Boolean = false,
    // 권한 상태
    val hasNotificationPermission: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    // 보호자 설정
    val guardianName: String = "",
    val guardianPhone: String = "",
    // 백그라운드 이벤트 (서비스에서 감지)
    val backgroundRiskEvent: RiskEvent? = null,
    // 감시 On/Off
    val isMonitoringEnabled: Boolean = true,
    // 위험 기록 목록
    val alertLogs: List<AlertLogEntity> = emptyList(),
    // LLM 상태
    val llmStatus: LlmStatus = LlmStatus.LOADING
)

class GuardianViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GuardianUiState())
    val uiState: StateFlow<GuardianUiState> = _uiState.asStateFlow()

    private val db = AppDatabase.getInstance(application)
    private val repository = ScamRepository(db.scamCaseDao())
    private val alertLogDao = db.alertLogDao()
    private val embeddingRepository = EmbeddingRepository(application, repository)
    private val ruleEngine = RuleBasedRiskEngine()
    // 앱 전역 싱글톤 사용 — NotificationMonitorService와 동일한 인스턴스 공유
    private val hybridEngine = com.ansim.guardian.AnsimApplication.instance.hybridEngine
    private val ragEngine = LocalRagEngine(repository, embeddingRepository)
    private val templateGenerator = TemplateExplanationGenerator()
    private val deviceChecker = DeviceCapabilityChecker(application)
    private val explanationEngine = LocalLlmManager(deviceChecker, templateGenerator)
    private val guardianManager = GuardianNotificationManager(application)
    private val dartClient = DartApiClient(DartApiKeyStore.getKey(application))
    private val stockAnalyzer = StockRiskAnalyzer(dartClient)

    init {
        // 임베딩 인덱스 백그라운드 빌드
        viewModelScope.launch {
            embeddingRepository.buildIndex()
        }

        // 백그라운드 서비스 이벤트 수신
        viewModelScope.launch {
            RiskEventBus.events.collect { event ->
                _uiState.value = _uiState.value.copy(backgroundRiskEvent = event)
                guardianManager.notifyAll(event.riskResult, event.sourceLabel)
                // 위험 기록 자동 저장
                saveAlertLog(event.riskResult, event.sourceLabel)
            }
        }

        // 위험 기록 DB 실시간 구독
        viewModelScope.launch {
            alertLogDao.getAllFlow().collect { logs ->
                _uiState.value = _uiState.value.copy(alertLogs = logs)
            }
        }

        // LLM 로딩 상태 구독
        viewModelScope.launch {
            hybridEngine.llmStatus.collect { status ->
                _uiState.value = _uiState.value.copy(llmStatus = status)
            }
        }

        // 초기 상태 로드
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
    }

    fun checkPermissions() {
        val app = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            hasOverlayPermission = Settings.canDrawOverlays(app),
            hasSmsPermission = ContextCompat.checkSelfPermission(
                app, android.Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED,
            hasNotificationPermission = isNotificationListenerEnabled(app)
        )
    }

    private fun isNotificationListenerEnabled(app: Application): Boolean {
        return androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(app)
            .contains(app.packageName)
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
                currentResult = null,
                explanation = null,
                financialResults = emptyList()
            )

            val input = RiskInput(text = text, source = InputSource.MANUAL)

            // 1단계: 규칙 엔진 즉시 실행 (항상 빠름)
            val ruleResult = ruleEngine.analyze(input)
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                currentResult = ruleResult,
                isExplanationLoading = true,
                isFinancialLoading = true
            )

            // 1-2단계: CAUTION 이상이면 서버(Gemini) 재검토 (IO 스레드 — ANR 방지)
            // SAFE는 서버 호출 생략 (API 한도 절약)
            val riskResult = if (ruleResult.riskLevel != RiskLevel.SAFE) {
                val serverResult = withContext(Dispatchers.IO) { hybridEngine.analyze(input) }
                if (serverResult.riskLevel != RiskLevel.SAFE) {
                    _uiState.value = _uiState.value.copy(currentResult = serverResult)
                    serverResult
                } else ruleResult
            } else ruleResult

            // 위험 기록 저장 (주의 이상)
            if (riskResult.riskLevel.ordinal >= RiskLevel.CAUTION.ordinal) {
                saveAlertLog(riskResult, "직접 입력")
            }

            // 2단계: RAG 유사 사례 검색
            val similarCases = ragEngine.retrieveByCategoryAndText(
                category = riskResult.primaryCategory,
                inputText = text
            )

            // 3단계: 설명 생성 (비동기)
            val explanation = explanationEngine.generateExplanation(input, riskResult, similarCases)
            _uiState.value = _uiState.value.copy(
                explanation = explanation,
                isExplanationLoading = false
            )

            // 4단계: 금융 데이터 분석 (비동기)
            val financialResults = stockAnalyzer.analyze(text, riskResult)
            _uiState.value = _uiState.value.copy(
                financialResults = financialResults,
                isFinancialLoading = false
            )

            // 위험하면 보호자 알림
            if (riskResult.isDangerous) {
                guardianManager.notifyAll(riskResult, "직접 입력")
            }
        }
    }

    /** 위험 기록을 DB에 저장 */
    private suspend fun saveAlertLog(result: RiskResult, sourceLabel: String) {
        if (result.riskLevel == RiskLevel.SAFE) return

        val category = result.primaryCategory?.displayName ?: "알 수 없음"
        val reason = when {
            result.llmReason != null -> result.llmReason
            result.detectedSignals.isNotEmpty() ->
                result.detectedSignals.first().description
            else -> "위험 신호 감지됨"
        }
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

    fun deleteAlertLog(id: Long) {
        viewModelScope.launch { alertLogDao.deleteById(id) }
    }

    fun clearAllAlertLogs() {
        viewModelScope.launch { alertLogDao.deleteAll() }
    }

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
            // IO 스레드 — LLM JNI 블로킹 콜 ANR 방지
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
