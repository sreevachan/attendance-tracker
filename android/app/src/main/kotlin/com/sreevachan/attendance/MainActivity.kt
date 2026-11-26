package com.sreevachan.attendance

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreevachan.attendance.ui.AttendanceViewModel
import com.sreevachan.attendance.ui.screens.AuthGateScreen
import com.sreevachan.attendance.ui.screens.BackupChoiceOverlay
import com.sreevachan.attendance.ui.screens.EncryptionOverlay
import com.sreevachan.attendance.ui.screens.MainScreen
import com.sreevachan.attendance.ui.screens.SettingsScreen
import com.sreevachan.attendance.ui.screens.WelcomeScreen
import com.sreevachan.attendance.ui.theme.AttendanceTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AttendanceViewModel by viewModels()

    private val driveConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            viewModel.onDriveConsentResult(result.resultCode == Activity.RESULT_OK, result.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ui by viewModel.ui.collectAsStateWithLifecycle()

            LaunchedEffect(ui.pendingDriveConsent) {
                val pending = ui.pendingDriveConsent ?: return@LaunchedEffect
                driveConsentLauncher.launch(IntentSenderRequest.Builder(pending).build())
            }

            AttendanceTheme(themeSetting = ui.state.settings.theme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        state = ui.state,
                        currentMonth = ui.currentMonth,
                        selectedDayKey = ui.selectedDayKey,
                        updateInfo = ui.updateInfo,
                        updateProgress = ui.updateProgress,
                        updateError = ui.updateError,
                        celebrateTick = ui.celebrateTick,
                        onOpenSettings = viewModel::openSettings,
                        onInstallUpdate = { viewModel.installUpdate(this@MainActivity) },
                        onPrevMonth = viewModel::prevMonth,
                        onNextMonth = viewModel::nextMonth,
                        onOpenDay = viewModel::openDaySheet,
                        onCloseDay = viewModel::closeDaySheet,
                        onMarkDay = viewModel::markDay,
                        onMarkDaySlots = viewModel::markDaySlots,
                        onClearDay = viewModel::clearDay,
                        onMarkAllPresent = viewModel::markAllUnmarkedPresent,
                        onExamDateChange = viewModel::setExamDate,
                    )

                    if (ui.showSetupOrSettings) {
                        SettingsScreen(
                            state = ui.state,
                            isFirstSetup = ui.isFirstSetup,
                            authUser = ui.authUser,
                            syncStatus = ui.syncStatus,
                            cloudReady = ui.cloudReady,
                            errorMsg = ui.settingsError,
                            successMsg = ui.settingsSuccess,
                            acctError = ui.acctError,
                            busy = ui.settingsBusy,
                            onClose = viewModel::closeSettings,
                            onThemeChange = viewModel::updateTheme,
                            onSave = viewModel::saveSettings,
                            onStartNewSemester = viewModel::startNewSemester,
                            onConnectDrive = viewModel::authorizeAndStartDriveSync,
                            onTurnOnEncrypted = viewModel::turnOnEncryptedFromSettings,
                            onUnlockBackup = viewModel::openUnlockFromSettings,
                            onSwitchToStandard = viewModel::chooseStandardBackup,
                            onTurnOffBackup = viewModel::turnOffBackup,
                            onSignIn = { viewModel.signInWithGoogle(this@MainActivity) },
                            onSignOut = viewModel::signOut,
                            onDeleteAccount = viewModel::deleteAccountAndDriveBackup,
                        )
                    }

                    if (ui.showGate) {
                        AuthGateScreen(
                            error = ui.gateError,
                            busy = ui.gateBusy,
                            accountMismatch = ui.gateAccountMismatch,
                            allowGuest = ui.state.uid == null,
                            onSignIn = { viewModel.signInWithGoogle(this@MainActivity) },
                            onContinueGuest = viewModel::continueAsGuest,
                            onUseDifferentAccount = viewModel::signOut,
                            onEraseDeviceData = viewModel::eraseDeviceData,
                        )
                    }

                    if (ui.showBackupChoice) {
                        BackupChoiceOverlay(
                            onChooseEncrypted = viewModel::chooseEncryptedBackup,
                            onChooseStandard = viewModel::chooseStandardBackup,
                            onChooseOff = viewModel::chooseNoBackupNow,
                        )
                    }

                    val encMode = ui.encMode
                    if (encMode != null) {
                        EncryptionOverlay(
                            mode = encMode,
                            recoveryCode = ui.recoveryCodeDisplay,
                            error = ui.encError,
                            busy = ui.encBusy,
                            onSetupPassphrase = viewModel::setupPassphrase,
                            onConfirmRecovery = viewModel::confirmRecoverySaved,
                            onUnlock = viewModel::unlockEncrypted,
                            onToggleAlt = viewModel::toggleEncMode,
                        )
                    }

                    if (ui.showWelcome) {
                        WelcomeScreen(onContinue = viewModel::dismissWelcome)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
    }
}
