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
import com.ansim.guardian.data.local.AppDatabase
import com.ansim.guardian.data.repository.ScamRepository
import com.ansim.guardian.domain.engine.LocalRagEngine
import com.ansim.guardian.domain.engine.RuleBasedRiskEngine
import com.ansim.guardian.domain.model.*
import com.ansim.guardian.financial.DartApiClient
import com.ansim.guardian.financial.FinancialRiskResult
import com.ansim.guardian.financial.StockRiskAnalyzer
import com.ansim.guardian.monitoring.GuardianNotificationManager
import com.ansim.guardian.monitoring.RiskEvent
import com.ansim.guardian.monitoring.RiskEventBus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    val backgroundRiskEvent: RiskEvent? = null
)

class GuardianViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GuardianUiState())
    val uiState: StateFlow<GuardianUiState> = _uiState.asStateFlow()

    private val db = AppDatabase.getInstance(application)
    private val repository = ScamRepository(db.scamCaseDao())
    private val embeddingRepository = EmbeddingRepository(application, repository)
    private val ruleEngine = RuleBasedRiskEngine()
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
            guardianPhone = guardianManager.guardianPhone
        )
    }

    fun checkPermissions() {
        val app = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            hasOverlayPermission = Settings.canDrawOverlays(app),
            hasSmsPermission = ContextCompat.checkSelfPermission(
                app, android.Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
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

            // 1단계: 규칙 엔진 즉시 실행
            val riskResult = ruleEngine.analyze(input)
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                currentResult = riskResult,
                isExplanationLoading = true,
                isFinancialLoading = true
            )

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

            // 4단계: 금융 데이터 분석 (비동기, MVP 4)
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
            val result = ruleEngine.analyze(input)
            RiskEventBus.emit(RiskEvent(result, label))
        }
    }

    fun reset() {
        _uiState.value = GuardianUiState(
            guardianName = guardianManager.guardianName,
            guardianPhone = guardianManager.guardianPhone,
            hasOverlayPermission = Settings.canDrawOverlays(getApplication())
        )
    }
}
