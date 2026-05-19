package com.ansim.guardian.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ansim.guardian.ui.financial.FinancialRiskScreen
import com.ansim.guardian.ui.guardian.GuardianSetupScreen
import com.ansim.guardian.ui.home.HomeScreen
import com.ansim.guardian.ui.risk.RiskResultScreen
import com.ansim.guardian.ui.setup.PermissionSetupScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object RiskResult : Screen("risk_result")
    object FinancialRisk : Screen("financial_risk")
    object GuardianSetup : Screen("guardian_setup")
    object PermissionSetup : Screen("permission_setup")
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
                isLoading = uiState.isAnalyzing,
                guardianName = uiState.guardianName,
                isGuardianConfigured = uiState.guardianPhone.isNotBlank()
            )
        }

        composable(Screen.RiskResult.route) {
            val result = uiState.currentResult
            if (result != null) {
                RiskResultScreen(
                    riskResult = result,
                    explanation = uiState.explanation,
                    isExplanationLoading = uiState.isExplanationLoading,
                    hasFinancialResults = uiState.financialResults.isNotEmpty(),
                    isFinancialLoading = uiState.isFinancialLoading,
                    onCallGuardian = { viewModel.notifyGuardian() },
                    onViewFinancial = { navController.navigate(Screen.FinancialRisk.route) },
                    onBack = {
                        viewModel.reset()
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Screen.FinancialRisk.route) {
            FinancialRiskScreen(
                financialResults = uiState.financialResults,
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
            PermissionSetupScreen(
                onComplete = {
                    viewModel.checkPermissions()
                    navController.popBackStack()
                },
                hasNotificationPermission = uiState.hasNotificationPermission,
                hasSmsPermission = uiState.hasSmsPermission,
                hasOverlayPermission = uiState.hasOverlayPermission
            )
        }
    }
}
