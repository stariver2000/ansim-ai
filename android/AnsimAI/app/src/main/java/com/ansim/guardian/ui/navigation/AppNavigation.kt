package com.ansim.guardian.ui.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ansim.guardian.ui.alertlog.AlertLogScreen
import com.ansim.guardian.ui.guardian.GuardianSetupScreen
import com.ansim.guardian.ui.home.HomeScreen
import com.ansim.guardian.ui.nas.NasConnectScreen
import com.ansim.guardian.ui.risk.RiskResultScreen
import com.ansim.guardian.ui.setup.PermissionSetupScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object RiskResult : Screen("risk_result")
    object GuardianSetup : Screen("guardian_setup")
    object PermissionSetup : Screen("permission_setup")
    object AlertLog : Screen("alert_log")
    object NasConnect : Screen("nas_connect")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: GuardianViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onAnalyze = { text ->
                    viewModel.analyze(text)
                    navController.navigate(Screen.RiskResult.route)
                },
                onOpenGuardianSetup = { navController.navigate(Screen.GuardianSetup.route) },
                onOpenPermissionSetup = { navController.navigate(Screen.PermissionSetup.route) },
                onOpenAlertLog = { navController.navigate(Screen.AlertLog.route) },
                onOpenNasConnect = {
                    viewModel.refreshNasPairing()
                    navController.navigate(Screen.NasConnect.route)
                },
                onSimulateNotification = { viewModel.simulateNotification(it) },
                onToggleMonitoring = { viewModel.toggleMonitoring() },
                onStartAgent = { viewModel.startAgentSession() },
                isLoading = uiState.isAnalyzing,
                guardianName = uiState.guardianName,
                isGuardianConfigured = uiState.guardianPhone.isNotBlank(),
                isMonitoringEnabled = uiState.isMonitoringEnabled,
                alertLogCount = uiState.alertLogs.size,
                llmStatus = uiState.llmStatus,
                isAgentBusy = uiState.isAgentBusy,
            )
        }

        composable(Screen.RiskResult.route) {
            val result = uiState.currentResult
            if (result != null) {
                RiskResultScreen(
                    riskResult = result,
                    isAnalyzing = uiState.isExplanationLoading,
                    onCallGuardian = { viewModel.notifyGuardian() },
                    onBack = {
                        viewModel.reset()
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Screen.NasConnect.route) {
            NasConnectScreen(
                isPaired = uiState.isNasPaired,
                baseUrl = uiState.nasBaseUrl,
                tokenExpEpochMs = uiState.nasTokenExpEpochMs,
                message = uiState.nasPairMessage,
                onPayloadScanned = { viewModel.onNasQrScanned(it) },
                onManualConnect = { url, token -> viewModel.onNasManualConnect(url, token) },
                onUnpair = { viewModel.unpairNas() },
                onScanError = { viewModel.onNasQrScanned("") },  // 빈 페이로드 → 실패 메시지
                onBack = {
                    viewModel.clearNasPairMessage()
                    navController.popBackStack()
                },
            )
        }

        composable(Screen.AlertLog.route) {
            AlertLogScreen(
                logs = uiState.alertLogs,
                onDeleteLog = { viewModel.deleteAlertLog(it) },
                onClearAll = { viewModel.clearAllAlertLogs() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.GuardianSetup.route) {
            GuardianSetupScreen(
                currentPhone = uiState.guardianPhone,
                currentName = uiState.guardianName,
                onSave = { name, phone ->
                    viewModel.saveGuardian(name, phone)
                    navController.popBackStack()
                },
                onSkip = { navController.popBackStack() }
            )
        }

        composable(Screen.PermissionSetup.route) {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) viewModel.checkPermissions()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val smsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { viewModel.checkPermissions() }

            val micLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { viewModel.checkPermissions() }

            PermissionSetupScreen(
                onComplete = {
                    viewModel.checkPermissions()
                    navController.popBackStack()
                },
                onRequestSmsPermission = {
                    smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                },
                onRequestMicPermission = {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                hasNotificationPermission = uiState.hasNotificationPermission,
                hasSmsPermission = uiState.hasSmsPermission,
                hasOverlayPermission = uiState.hasOverlayPermission,
                hasMicPermission = uiState.hasMicPermission,
            )
        }
    }
}
