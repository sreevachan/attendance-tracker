package com.sreevachan.attendance.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sreevachan.attendance.BuildConfig
import com.sreevachan.attendance.data.ApkUpdater
import com.sreevachan.attendance.data.AppSettings
import com.sreevachan.attendance.data.ArchivedSubject
import com.sreevachan.attendance.data.AttendanceCalculator
import com.sreevachan.attendance.data.AttendancePayload
import com.sreevachan.attendance.data.AttendanceRepository
import com.sreevachan.attendance.data.AttendanceState
import com.sreevachan.attendance.data.AuthUser
import com.sreevachan.attendance.data.BaseAttendance
import com.sreevachan.attendance.data.CloudDoc
import com.sreevachan.attendance.data.CloudGetResult
import com.sreevachan.attendance.data.CryptoHelper
import com.sreevachan.attendance.data.CryptoMeta
import com.sreevachan.attendance.data.DayMark
import com.sreevachan.attendance.data.DaySlot
import com.sreevachan.attendance.data.DriveAuthResult
import com.sreevachan.attendance.data.GoogleAuthResult
import com.sreevachan.attendance.data.KeySetupOutput
import com.sreevachan.attendance.data.SemesterArchive
import com.sreevachan.attendance.data.SubjectItem
import com.sreevachan.attendance.data.TimetableVersion
import com.sreevachan.attendance.data.UpdateInfo
import com.sreevachan.attendance.data.UserProfile
import com.sreevachan.attendance.widget.AttendanceWidget
import java.time.YearMonth
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EncModalMode {
    SET_PASSPHRASE,
    SHOW_RECOVERY,
    UNLOCK_PASSPHRASE,
    UNLOCK_RECOVERY,
}

data class AttendanceUiState(
    val state: AttendanceState = AttendanceState(),
    val showWelcome: Boolean = false,
    val showGate: Boolean = false,
    val gateError: String = "",
    val gateBusy: Boolean = false,
    val gateAccountMismatch: Boolean = false,
    val authUser: AuthUser? = null,
    val showSetupOrSettings: Boolean = false,
    val isFirstSetup: Boolean = false,
    val settingsError: String = "",
    val settingsSuccess: String = "",
    val settingsBusy: Boolean = false,
    val syncStatus: String = "",
    val acctError: String = "",
    val showBackupChoice: Boolean = false,
    val encMode: EncModalMode? = null,
    val encError: String = "",
    val encBusy: Boolean = false,
    val recoveryCodeDisplay: String = "",
    val cloudReady: Boolean = false,
    val lastBackupTimeText: String = "",
    val backupInProgress: Boolean = false,
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDayKey: String? = null,
    val updateInfo: UpdateInfo? = null,
    val updateProgress: Float? = null,
    val updateError: String = "",
    val celebrateTick: Int = 0,
    val pendingDriveConsent: PendingIntent? = null,
)

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AttendanceRepository(application)
    private val _ui = MutableStateFlow(AttendanceUiState())
    val ui: StateFlow<AttendanceUiState> = _ui.asStateFlow()

    private var dataKey: SecretKey? = null
    private var cryptoMeta: CryptoMeta? = null
    private var pendingCloudEnc: CloudDoc.Encrypted? = null
    private var pendingKeySetup: KeySetupOutput? = null
    private var saveJob: Job? = null
    private var lastWasOk: Boolean? = null
    private var driveAccessToken: String? = null

    init {
        val loaded = repo.loadState()
        val welcomed = repo.isWelcomed()
        val user = repo.loadAuthUser()
        val firstSetup = loaded.profile == null

        _ui.update {
            it.copy(
                state = loaded,
                showWelcome = !welcomed,
                showGate = !loaded.guest && (user == null || (loaded.uid != null && loaded.uid != user.uid)),
                gateAccountMismatch = user != null && loaded.uid != null && loaded.uid != user.uid,
                gateError = if (user != null && loaded.uid != null && loaded.uid != user.uid) {
                    "This device holds data for a different account. Use that account, or erase this device's data."
                } else {
                    ""
                },
                authUser = user,
                showSetupOrSettings = firstSetup,
                isFirstSetup = firstSetup,
            )
        }
        syncWidgetAndCelebration(loaded)
        if (user != null && (loaded.uid == null || loaded.uid == user.uid)) {
            onAuthenticated(user)
        }
        checkUpdates()
    }

    fun refreshToday() {
        checkUpdates()
    }

    private fun checkUpdates() {
        viewModelScope.launch {
            val info = repo.checkForUpdate(BuildConfig.VERSION_CODE)
            _ui.update { it.copy(updateInfo = info) }
        }
    }

    fun installUpdate(context: Context) {
        val info = _ui.value.updateInfo ?: return
        val apkUrl = info.apkUrl?.takeIf { it.startsWith(AttendanceRepository.GITHUB_BASE) } ?: return
        if (_ui.value.updateProgress != null) return
        viewModelScope.launch {
            _ui.update { it.copy(updateProgress = 0f, updateError = "") }
            val res = ApkUpdater.downloadAndInstall(context, apkUrl) { p ->
                _ui.update { it.copy(updateProgress = p) }
            }
            _ui.update {
                it.copy(
                    updateProgress = null,
                    updateError = res.exceptionOrNull()?.message ?: "",
                )
            }
        }
    }

    fun dismissWelcome() {
        repo.setWelcomed()
        _ui.update { it.copy(showWelcome = false) }
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _ui.update { it.copy(gateBusy = true, gateError = "", acctError = "") }
            when (val res = repo.signInWithGoogle(activityContext)) {
                is GoogleAuthResult.Success -> {
                    _ui.update { it.copy(gateBusy = false) }
                    onAuthenticated(res.user)
                }
                is GoogleAuthResult.Cancelled -> {
                    _ui.update { it.copy(gateBusy = false) }
                }
                is GoogleAuthResult.Failure -> {
                    _ui.update {
                        if (it.authUser != null || it.state.guest) {
                            it.copy(gateBusy = false, acctError = res.message)
                        } else {
                            it.copy(gateBusy = false, gateError = res.message)
                        }
                    }
                }
            }
        }
    }

    private fun onAuthenticated(user: AuthUser) {
        val cur = _ui.value.state
        if (cur.uid != null && cur.uid != user.uid) {
            _ui.update {
                it.copy(
                    authUser = user,
                    showGate = true,
                    gateAccountMismatch = true,
                    gateError = "This device holds data for a different account. Use that account, or erase this device's data.",
                )
            }
            return
        }
        val updated = cur.copy(uid = user.uid, guest = false)
        repo.saveState(updated)
        _ui.update {
            it.copy(
                state = updated,
                authUser = user,
                showGate = false,
                gateAccountMismatch = false,
                gateError = "",
                acctError = "",
                syncStatus = syncSummaryText(updated.backup, cloudReady = false),
            )
        }
        if (updated.backup != "off") {
            authorizeAndStartDriveSync()
        }
    }

    fun authorizeAndStartDriveSync() {
        viewModelScope.launch {
            when (val authRes = repo.authorizeDrive()) {
                is DriveAuthResult.Ready -> {
                    driveAccessToken = authRes.accessToken
                    startCloudSync(authRes.accessToken)
                }
                is DriveAuthResult.NeedsConsent -> {
                    _ui.update {
                        it.copy(
                            pendingDriveConsent = authRes.pendingIntent,
                            syncStatus = "Connect Google Drive to sync your backup.",
                        )
                    }
                }
                is DriveAuthResult.Failure -> {
                    _ui.update { it.copy(syncStatus = authRes.message) }
                }
            }
        }
    }

    fun onDriveConsentResult(resultOk: Boolean, data: Intent?) {
        _ui.update { it.copy(pendingDriveConsent = null) }
        if (!resultOk) {
            _ui.update { it.copy(syncStatus = "Google Drive backup permission was not granted.") }
            return
        }
        val token = repo.handleDriveConsentIntent(data)
        if (token.isNullOrBlank()) {
            _ui.update { it.copy(syncStatus = "Could not connect Google Drive.") }
            return
        }
        driveAccessToken = token
        startCloudSync(token)
    }

    fun continueAsGuest() {
        val updated = _ui.value.state.copy(guest = true)
        repo.saveState(updated)
        _ui.update { it.copy(state = updated, showGate = false, gateError = "") }
    }

    fun signOut() {
        viewModelScope.launch {
            forgetKey()
            driveAccessToken = null
            repo.signOut()
            val cur = _ui.value.state
            _ui.update {
                it.copy(
                    authUser = null,
                    cloudReady = false,
                    syncStatus = "",
                    showSetupOrSettings = false,
                    showGate = !cur.guest,
                    gateAccountMismatch = false,
                    gateError = "",
                )
            }
        }
    }

    fun eraseDeviceData() {
        viewModelScope.launch {
            forgetKey()
            driveAccessToken = null
            repo.signOut()
            repo.wipeLocalData()
            runCatching { AttendanceWidget().updateAll(getApplication()) }
            val fresh = AttendanceState()
            lastWasOk = null
            _ui.update {
                AttendanceUiState(
                    state = fresh,
                    showWelcome = false,
                    showGate = true,
                    showSetupOrSettings = true,
                    isFirstSetup = true,
                )
            }
        }
    }

    fun deleteAccountAndDriveBackup() {
        viewModelScope.launch {
            _ui.update { it.copy(settingsBusy = true, acctError = "") }
            val token = driveAccessToken ?: when (val r = repo.authorizeDrive()) {
                is DriveAuthResult.Ready -> r.accessToken
                else -> null
            }
            if (token != null) {
                repo.cloudDelete(token)
            }
            forgetKey()
            driveAccessToken = null
            repo.signOut()
            repo.wipeLocalData()
            runCatching { AttendanceWidget().updateAll(getApplication()) }
            val fresh = AttendanceState()
            lastWasOk = null
            _ui.update {
                AttendanceUiState(
                    state = fresh,
                    showWelcome = false,
                    showGate = true,
                    showSetupOrSettings = true,
                    isFirstSetup = true,
                )
            }
        }
    }

    private fun startCloudSync(accessToken: String) {
        viewModelScope.launch {
            _ui.update { it.copy(cloudReady = false) }
            when (val res = repo.cloudGet(accessToken)) {
                is CloudGetResult.NetworkError -> {
                    _ui.update { it.copy(syncStatus = "Could not reach your Google Drive backup. Working offline.") }
                }
                is CloudGetResult.NotFound -> {
                    cryptoMeta = null
                    val backup = _ui.value.state.backup
                    when (backup) {
                        "off" -> _ui.update { it.copy(syncStatus = "Backup is off.") }
                        "std" -> {
                            _ui.update { it.copy(cloudReady = true, syncStatus = "Standard backup in Google Drive is on.") }
                            pushNow()
                        }
                        "enc" -> {
                            if (dataKey != null && cryptoMeta != null) {
                                _ui.update { it.copy(cloudReady = true, syncStatus = "End-to-end encrypted backup in Google Drive is on.") }
                                pushNow()
                            } else {
                                _ui.update { it.copy(showBackupChoice = true) }
                            }
                        }
                        else -> _ui.update { it.copy(showBackupChoice = true) }
                    }
                }
                is CloudGetResult.Found -> when (val doc = res.doc) {
                    is CloudDoc.Standard -> applyStandardCloud(doc)
                    is CloudDoc.Encrypted -> handleEncryptedCloud(doc)
                }
            }
        }
    }

    private fun applyStandardCloud(doc: CloudDoc.Standard) {
        val cur = _ui.value.state
        val next = cur.copy(backup = "std")
        repo.saveState(next)
        val remote = runCatching { repo.json.decodeFromString<AttendancePayload>(doc.dataJson) }.getOrNull()
        if (remote == null) {
            _ui.update { it.copy(state = next, syncStatus = "Your backup could not be read.") }
            return
        }
        if (doc.updatedAt > next.updatedAt) {
            applyRemotePayload(next, remote, doc.updatedAt)
        } else if (next.updatedAt > doc.updatedAt) {
            _ui.update { it.copy(state = next, cloudReady = true, syncStatus = "Standard backup in Google Drive is on.") }
            pushNow()
        } else {
            _ui.update { it.copy(state = next, cloudReady = true, syncStatus = "Standard backup in Google Drive is on.") }
        }
    }

    private fun handleEncryptedCloud(doc: CloudDoc.Encrypted) {
        val next = _ui.value.state.copy(backup = "enc")
        repo.saveState(next)
        pendingCloudEnc = doc
        cryptoMeta = doc.meta
        _ui.update {
            it.copy(
                state = next,
                syncStatus = "Your encrypted backup is locked.",
            )
        }

        val savedKeyB64 = repo.loadDataKeyB64()
        if (savedKeyB64 == null) {
            _ui.update { it.copy(encMode = EncModalMode.UNLOCK_PASSPHRASE, encError = "") }
            return
        }

        viewModelScope.launch {
            val unlocked = withContext(Dispatchers.Default) {
                runCatching {
                    val dk = CryptoHelper.keyFromRaw(CryptoHelper.unb64(savedKeyB64))
                    val plain = CryptoHelper.unseal(dk, doc.blob)
                    val payload = repo.json.decodeFromString<AttendancePayload>(plain.decodeToString())
                    dk to payload
                }.getOrNull()
            }
            if (unlocked != null) {
                dataKey = unlocked.first
                if (doc.updatedAt > next.updatedAt) {
                    applyRemotePayload(next, unlocked.second, doc.updatedAt)
                } else if (next.updatedAt > doc.updatedAt) {
                    _ui.update {
                        it.copy(
                            cloudReady = true,
                            encMode = null,
                            syncStatus = "End-to-end encrypted backup in Google Drive is on.",
                        )
                    }
                    pushNow()
                } else {
                    _ui.update {
                        it.copy(
                            cloudReady = true,
                            encMode = null,
                            syncStatus = "End-to-end encrypted backup in Google Drive is on.",
                        )
                    }
                }
            } else {
                dataKey = null
                repo.forgetDataKey()
                _ui.update { it.copy(encMode = EncModalMode.UNLOCK_PASSPHRASE, encError = "") }
            }
        }
    }

    private fun applyRemotePayload(baseState: AttendanceState, remote: AttendancePayload, ts: Long) {
        val merged = baseState.withPayload(remote, ts)
        repo.saveState(merged)
        syncWidgetAndCelebration(merged)
        val firstSetup = merged.profile == null
        _ui.update {
            it.copy(
                state = merged,
                cloudReady = true,
                encMode = null,
                showSetupOrSettings = firstSetup,
                isFirstSetup = firstSetup,
                syncStatus = syncSummaryText(merged.backup, cloudReady = true),
            )
        }
    }

    fun chooseEncryptedBackup() {
        _ui.update { it.copy(showBackupChoice = false, encMode = EncModalMode.SET_PASSPHRASE, encError = "") }
    }

    fun chooseStandardBackup() {
        val next = _ui.value.state.copy(
            backup = "std",
            updatedAt = _ui.value.state.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        forgetKey()
        repo.saveState(next)
        _ui.update {
            it.copy(
                state = next,
                showBackupChoice = false,
                cloudReady = true,
                syncStatus = "Standard backup in Google Drive is on.",
            )
        }
        ensureDriveAndPush()
    }

    fun chooseNoBackupNow() {
        val next = _ui.value.state.copy(backup = "off")
        repo.saveState(next)
        _ui.update {
            it.copy(
                state = next,
                showBackupChoice = false,
                cloudReady = false,
                syncStatus = "Backup is off.",
            )
        }
    }

    fun turnOnEncryptedFromSettings() {
        _ui.update { it.copy(encMode = EncModalMode.SET_PASSPHRASE, encError = "") }
    }

    fun openUnlockFromSettings() {
        _ui.update { it.copy(encMode = EncModalMode.UNLOCK_PASSPHRASE, encError = "") }
    }

    fun turnOffBackup() {
        viewModelScope.launch {
            val token = ensureToken()
            val ok = if (token != null) repo.cloudDelete(token) else false
            if (ok) {
                forgetKey()
                val next = _ui.value.state.copy(backup = "off")
                repo.saveState(next)
                _ui.update {
                    it.copy(
                        state = next,
                        cloudReady = false,
                        syncStatus = "Backup is off.",
                    )
                }
            } else {
                _ui.update { it.copy(syncStatus = "Could not delete the Google Drive backup. Check your connection.") }
            }
        }
    }

    fun setupPassphrase(passphrase: String, confirm: String) {
        if (passphrase.length < 8) {
            _ui.update { it.copy(encError = "Use at least 8 characters.") }
            return
        }
        if (passphrase != confirm) {
            _ui.update { it.copy(encError = "The passphrases don't match.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(encBusy = true, encError = "") }
            val setup = withContext(Dispatchers.Default) {
                runCatching { CryptoHelper.setupKeys(passphrase) }.getOrNull()
            }
            if (setup == null) {
                _ui.update { it.copy(encBusy = false, encError = "Could not create keys on this device.") }
                return@launch
            }
            pendingKeySetup = setup
            _ui.update {
                it.copy(
                    encBusy = false,
                    encMode = EncModalMode.SHOW_RECOVERY,
                    recoveryCodeDisplay = CryptoHelper.formatRecoveryCode(setup.recoveryCode),
                    encError = "",
                )
            }
        }
    }

    fun confirmRecoverySaved(checked: Boolean) {
        if (!checked) {
            _ui.update { it.copy(encError = "Tick the box once you have saved the code.") }
            return
        }
        val setup = pendingKeySetup ?: return
        dataKey = setup.dataKey
        cryptoMeta = setup.meta
        repo.saveDataKeyB64(CryptoHelper.b64(setup.rawDataKey))
        pendingKeySetup = null

        val cur = _ui.value.state
        val ts = if (cur.updatedAt > 0L) cur.updatedAt else System.currentTimeMillis()
        val next = cur.copy(backup = "enc", updatedAt = ts)
        repo.saveState(next)
        _ui.update {
            it.copy(
                state = next,
                encMode = null,
                recoveryCodeDisplay = "",
                encError = "",
                cloudReady = true,
                syncStatus = "End-to-end encrypted backup in Google Drive is on.",
            )
        }
        ensureDriveAndPush()
    }

    fun toggleEncMode() {
        val current = _ui.value.encMode ?: return
        when (current) {
            EncModalMode.SET_PASSPHRASE -> {
                _ui.update {
                    it.copy(
                        encMode = null,
                        encError = "",
                        showBackupChoice = it.state.backup.isEmpty(),
                    )
                }
            }
            EncModalMode.UNLOCK_PASSPHRASE -> {
                _ui.update { it.copy(encMode = EncModalMode.UNLOCK_RECOVERY, encError = "") }
            }
            EncModalMode.UNLOCK_RECOVERY -> {
                _ui.update { it.copy(encMode = EncModalMode.UNLOCK_PASSPHRASE, encError = "") }
            }
            EncModalMode.SHOW_RECOVERY -> Unit
        }
    }

    fun unlockEncrypted(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val doc = pendingCloudEnc ?: return
        val meta = cryptoMeta ?: return
        val isRecovery = _ui.value.encMode == EncModalMode.UNLOCK_RECOVERY
        val secret = if (isRecovery) CryptoHelper.normalizeRecoveryInput(trimmed) else trimmed

        viewModelScope.launch {
            _ui.update { it.copy(encBusy = true, encError = "") }
            val unlocked = withContext(Dispatchers.Default) {
                runCatching {
                    val (dk, raw) = CryptoHelper.unlockWith(secret, meta, isRecovery)
                    val plain = CryptoHelper.unseal(dk, doc.blob)
                    val payload = repo.json.decodeFromString<AttendancePayload>(plain.decodeToString())
                    Triple(dk, raw, payload)
                }.getOrNull()
            }
            if (unlocked == null) {
                _ui.update {
                    it.copy(
                        encBusy = false,
                        encError = if (isRecovery) "That recovery code doesn't match." else "Wrong passphrase.",
                    )
                }
                return@launch
            }
            dataKey = unlocked.first
            repo.saveDataKeyB64(CryptoHelper.b64(unlocked.second))
            val cur = _ui.value.state
            _ui.update { it.copy(encBusy = false, encMode = null, encError = "") }
            if (doc.updatedAt > cur.updatedAt) {
                applyRemotePayload(cur, unlocked.third, doc.updatedAt)
            } else if (cur.updatedAt > doc.updatedAt) {
                _ui.update {
                    it.copy(
                        cloudReady = true,
                        syncStatus = "End-to-end encrypted backup in Google Drive is on.",
                    )
                }
                pushNow()
            } else {
                _ui.update {
                    it.copy(
                        cloudReady = true,
                        syncStatus = "End-to-end encrypted backup in Google Drive is on.",
                    )
                }
            }
        }
    }

    private fun forgetKey() {
        dataKey = null
        cryptoMeta = null
        pendingCloudEnc = null
        pendingKeySetup = null
        repo.forgetDataKey()
    }

    private suspend fun ensureToken(): String? {
        driveAccessToken?.let { return it }
        return when (val r = repo.authorizeDrive()) {
            is DriveAuthResult.Ready -> {
                driveAccessToken = r.accessToken
                r.accessToken
            }
            is DriveAuthResult.NeedsConsent -> {
                _ui.update { it.copy(pendingDriveConsent = r.pendingIntent) }
                null
            }
            is DriveAuthResult.Failure -> null
        }
    }

    private fun ensureDriveAndPush() {
        viewModelScope.launch {
            if (ensureToken() != null) {
                pushNow()
            }
        }
    }

    private fun commitStateChange(transform: (AttendanceState) -> AttendanceState) {
        val prev = _ui.value.state
        val modified = transform(prev).copy(updatedAt = System.currentTimeMillis())
        repo.saveState(modified)
        syncWidgetAndCelebration(modified)
        _ui.update { it.copy(state = modified) }
        if (_ui.value.cloudReady) {
            saveJob?.cancel()
            saveJob = viewModelScope.launch {
                delay(1500)
                pushNow()
            }
        }
    }

    private fun pushNow() {
        val snap = _ui.value
        if (!snap.cloudReady) return
        val st = snap.state
        val ts = if (st.updatedAt > 0L) st.updatedAt else System.currentTimeMillis()
        viewModelScope.launch {
            val token = ensureToken() ?: return@launch
            _ui.update { it.copy(backupInProgress = true, syncStatus = "Backing up to Google Drive...") }
            val ok = when (st.backup) {
                "std" -> repo.cloudPutStandard(token, st.toPayload(), ts)
                "enc" -> {
                    val dk = dataKey ?: run {
                        _ui.update { it.copy(backupInProgress = false) }
                        return@launch
                    }
                    val meta = cryptoMeta ?: run {
                        _ui.update { it.copy(backupInProgress = false) }
                        return@launch
                    }
                    val blob = withContext(Dispatchers.Default) {
                        val plain = repo.json.encodeToString(st.toPayload()).encodeToByteArray()
                        CryptoHelper.seal(dk, plain)
                    }
                    repo.cloudPutEncrypted(token, meta, blob, ts)
                }
                else -> {
                    _ui.update { it.copy(backupInProgress = false) }
                    return@launch
                }
            }
            if (!ok) {
                repo.invalidateDriveToken()
                driveAccessToken = null
                _ui.update {
                    it.copy(
                        backupInProgress = false,
                        syncStatus = "Backup could not be saved to Google Drive. It will retry when you are online.",
                    )
                }
            } else {
                val timeStr = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                _ui.update {
                    it.copy(
                        backupInProgress = false,
                        lastBackupTimeText = timeStr,
                        syncStatus = "${syncSummaryText(st.backup, cloudReady = true)} Last backed up at $timeStr.",
                    )
                }
            }
        }
    }

    fun backupNow() {
        if (!_ui.value.cloudReady) {
            authorizeAndStartDriveSync()
        } else {
            saveJob?.cancel()
            pushNow()
        }
    }

    fun prevMonth() {
        _ui.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
    }

    fun nextMonth() {
        _ui.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
    }

    fun openDaySheet(dateKey: String) {
        _ui.update { it.copy(selectedDayKey = dateKey) }
    }

    fun closeDaySheet() {
        _ui.update { it.copy(selectedDayKey = null) }
    }

    fun markDay(dateKey: String, status: String, attendedPartial: Int? = null) {
        val scheduled = AttendanceCalculator.scheduledSubjectsForDate(_ui.value.state, dateKey)
        commitStateChange { st ->
            val days = st.days.toMutableMap()
            if (scheduled.isNotEmpty() && status in listOf("P", "A", "H")) {
                val slots = scheduled.map { subId -> DaySlot(sub = subId, st = status) }
                val n = if (status == "H") 0 else slots.size
                days[dateKey] = DayMark(
                    s = status,
                    n = n,
                    a = if (status == "P") n else 0,
                    slots = slots,
                )
            } else {
                val n = AttendanceCalculator.dayPeriods(st, dateKey)
                days[dateKey] = DayMark(
                    s = status,
                    n = n,
                    a = if (status == "X") attendedPartial else null,
                )
            }
            st.copy(days = days)
        }
        closeDaySheet()
    }

    fun markDaySlots(dateKey: String, slots: List<DaySlot>) {
        val active = slots.count { it.st != "H" }
        val present = slots.count { it.st == "P" }
        val summaryStatus = when {
            active == 0 -> "H"
            present == active -> "P"
            present == 0 -> "A"
            else -> "X"
        }
        commitStateChange { st ->
            val days = st.days.toMutableMap()
            days[dateKey] = DayMark(
                s = summaryStatus,
                n = active,
                a = if (summaryStatus == "X") present else null,
                slots = slots,
            )
            st.copy(days = days)
        }
        closeDaySheet()
    }

    fun clearDay(dateKey: String) {
        commitStateChange { st ->
            val days = st.days.toMutableMap()
            days.remove(dateKey)
            st.copy(days = days)
        }
        closeDaySheet()
    }

    fun markAllUnmarkedPresent() {
        val unmarked = AttendanceCalculator.unmarkedPastDays(_ui.value.state)
        if (unmarked.isEmpty()) return
        commitStateChange { st ->
            val days = st.days.toMutableMap()
            for (k in unmarked) {
                val scheduled = AttendanceCalculator.scheduledSubjectsForDate(st, k)
                if (scheduled.isNotEmpty()) {
                    val slots = scheduled.map { DaySlot(sub = it, st = "P") }
                    days[k] = DayMark(s = "P", n = slots.size, slots = slots)
                } else {
                    val d = AttendanceCalculator.parseDate(k) ?: continue
                    days[k] = DayMark(s = "P", n = AttendanceCalculator.periodsFor(st, d))
                }
            }
            st.copy(days = days)
        }
    }

    fun setExamDate(dateKey: String) {
        commitStateChange { st -> st.copy(exam = dateKey) }
    }

    fun openSettings() {
        _ui.update {
            it.copy(
                showSetupOrSettings = true,
                isFirstSetup = it.state.profile == null,
                settingsError = "",
                settingsSuccess = "",
                acctError = "",
            )
        }
    }

    fun closeSettings() {
        if (_ui.value.state.profile == null) return
        _ui.update { it.copy(showSetupOrSettings = false, settingsError = "", settingsSuccess = "") }
    }

    fun updateTheme(theme: String) {
        commitStateChange { st ->
            st.copy(settings = st.settings.copy(theme = theme))
        }
    }

    fun saveSettings(
        name: String,
        college: String,
        roll: String,
        year: String,
        sem: String,
        mid: String,
        startFromDayOne: Boolean,
        startDate: String,
        asOfInput: String,
        overallAttendedText: String,
        overallConductedText: String,
        minPct: Int,
        subjects: List<SubjectItem>,
        timetableDays: List<List<String>>,
        timetableEffectiveFrom: String,
        fallbackWeekdayCounts: List<Int>,
    ) {
        val nm = name.trim()
        val cl = college.trim()
        val rl = roll.trim()
        val yr = year.trim()
        val sm = sem.trim()
        val md = mid.trim()
        val today = AttendanceCalculator.keyOf(AttendanceCalculator.today())
        val isoRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")

        val startParsed = AttendanceCalculator.parseDate(startDate)
        val effectiveAsOf = if (startFromDayOne) {
            startParsed?.minusDays(1)?.let(AttendanceCalculator::keyOf) ?: ""
        } else {
            asOfInput
        }

        val hasSubjects = subjects.isNotEmpty()
        val hasTimetableSlots = hasSubjects && timetableDays.any { it.isNotEmpty() }

        val overallA = if (startFromDayOne) 0 else overallAttendedText.toIntOrNull()
        val overallC = if (startFromDayOne) 0 else overallConductedText.toIntOrNull()

        val err = when {
            nm.isEmpty() || cl.isEmpty() || rl.isEmpty() -> "Enter your name, college and roll number."
            !isoRegex.matches(startDate) || startParsed == null -> "Pick a valid college start date."
            !startFromDayOne && (!isoRegex.matches(asOfInput) || asOfInput > today) ->
                "Pick the date your numbers are up to (today or earlier)."
            !startFromDayOne && !hasSubjects && (overallA == null || overallC == null || overallA < 0 || overallC < 0) ->
                "Enter attended and conducted as whole numbers."
            !startFromDayOne && !hasSubjects && overallA!! > overallC!! ->
                "Attended can't be more than conducted."
            !startFromDayOne && hasSubjects && subjects.any { it.baseA < 0 || it.baseC < 0 || it.baseA > it.baseC } ->
                "Subject attended classes can't exceed conducted classes."
            minPct !in 1..100 -> "Minimum attendance must be between 1 and 100."
            !hasTimetableSlots && (fallbackWeekdayCounts.size != 6 || fallbackWeekdayCounts.any { it !in 0..12 }) ->
                "Periods per day must be from 0 to 12."
            hasTimetableSlots && !isoRegex.matches(timetableEffectiveFrom) ->
                "Pick a valid date for applying timetable changes."
            else -> ""
        }

        if (err.isNotEmpty()) {
            _ui.update { it.copy(settingsError = err, settingsSuccess = "") }
            return
        }

        val wasFirst = _ui.value.state.profile == null
        commitStateChange { st ->
            val cleanedSubjects = if (startFromDayOne) {
                subjects.map { it.copy(baseA = 0, baseC = 0) }
            } else {
                subjects
            }

            val nextBase = if (startFromDayOne) {
                BaseAttendance(a = 0, c = 0, asOf = effectiveAsOf)
            } else if (hasSubjects) {
                // Subject baselines live on each SubjectItem; keep unassigned base at 0
                BaseAttendance(a = 0, c = 0, asOf = effectiveAsOf)
            } else {
                BaseAttendance(a = overallA ?: 0, c = overallC ?: 0, asOf = effectiveAsOf)
            }

            val nextPw = if (hasTimetableSlots) {
                AttendanceCalculator.deriveWeekdayCounts(timetableDays)
            } else {
                listOf(0) + fallbackWeekdayCounts
            }

            val nextTimetable = if (hasTimetableSlots) {
                val eff = timetableEffectiveFrom.ifBlank { startDate }
                val updatedList = st.timetable.filterNot { it.from == eff }.toMutableList()
                updatedList.add(TimetableVersion(from = eff, days = timetableDays))
                updatedList.sortedBy { it.from }
            } else {
                st.timetable
            }

            val newDays = if (effectiveAsOf != st.base.asOf) {
                st.days.filterKeys { k -> k > effectiveAsOf }
            } else {
                st.days
            }

            st.copy(
                profile = UserProfile(
                    name = nm,
                    college = cl,
                    roll = rl,
                    year = yr,
                    sem = sm,
                    mid = md,
                ),
                startDate = startDate,
                base = nextBase,
                settings = AppSettings(minPct = minPct, theme = st.settings.theme),
                subjects = cleanedSubjects,
                timetable = nextTimetable,
                pw = nextPw,
                days = newDays,
            )
        }

        _ui.update {
            it.copy(
                isFirstSetup = false,
                showSetupOrSettings = if (wasFirst) false else it.showSetupOrSettings,
                settingsError = "",
                settingsSuccess = if (wasFirst) "" else "Saved.",
            )
        }
    }

    fun startNewSemester(
        newYear: String,
        newSem: String,
        newMid: String,
        newStartDate: String,
        keepSubjectsAndTimetable: Boolean,
    ) {
        val startParsed = AttendanceCalculator.parseDate(newStartDate) ?: return
        val newAsOf = AttendanceCalculator.keyOf(startParsed.minusDays(1))

        commitStateChange { st ->
            val (baseA, baseC) = AttendanceCalculator.overallBase(st)
            val totals = AttendanceCalculator.totals(st)
            val subStats = AttendanceCalculator.subjectStats(st)
            val archived = SemesterArchive(
                year = st.profile?.year.orEmpty(),
                sem = st.profile?.sem.orEmpty(),
                mid = st.profile?.mid.orEmpty(),
                startDate = st.startDate,
                endDate = AttendanceCalculator.keyOf(AttendanceCalculator.today()),
                attended = baseA + totals.attended,
                conducted = baseC + totals.conducted,
                subjects = subStats.map {
                    ArchivedSubject(
                        name = it.subject.name,
                        attended = it.attended,
                        conducted = it.conducted,
                    )
                },
            )

            val resetSubjects = if (keepSubjectsAndTimetable) {
                st.subjects.map { it.copy(baseA = 0, baseC = 0) }
            } else {
                emptyList()
            }
            val resetTimetable = if (keepSubjectsAndTimetable && st.timetable.isNotEmpty()) {
                listOf(TimetableVersion(from = newStartDate, days = st.timetable.last().days))
            } else {
                emptyList()
            }

            st.copy(
                profile = st.profile?.copy(
                    year = newYear.trim(),
                    sem = newSem.trim(),
                    mid = newMid.trim(),
                ),
                startDate = newStartDate,
                base = BaseAttendance(a = 0, c = 0, asOf = newAsOf),
                days = emptyMap(),
                subjects = resetSubjects,
                timetable = resetTimetable,
                archives = listOf(archived) + st.archives,
            )
        }
        _ui.update { it.copy(settingsSuccess = "Started new semester. Previous semester archived.") }
    }

    private fun syncWidgetAndCelebration(st: AttendanceState) {
        val (baseA, baseC) = AttendanceCalculator.overallBase(st)
        val all = AttendanceCalculator.totals(st)
        val a = baseA + all.attended
        val c = baseC + all.conducted
        val pct = if (c > 0) a * 100.0 / c else 0.0
        val ok = c > 0 && a * 100 >= st.settings.minPct * c

        if (lastWasOk == false && ok) {
            _ui.update { it.copy(celebrateTick = it.celebrateTick + 1) }
        }
        lastWasOk = ok

        repo.saveWidgetSummary(pct, a, c, st.settings.minPct)
        viewModelScope.launch {
            runCatching { AttendanceWidget().updateAll(getApplication()) }
        }
    }

    private fun syncSummaryText(backup: String, cloudReady: Boolean): String = when (backup) {
        "std" -> if (cloudReady) "Standard backup in Google Drive is on." else "Connect Google Drive to sync."
        "enc" -> if (cloudReady) "End-to-end encrypted backup in Google Drive is on." else "Your encrypted backup is locked."
        "off" -> "Backup is off."
        else -> ""
    }
}
