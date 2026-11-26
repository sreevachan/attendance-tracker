package com.sreevachan.attendance.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sreevachan.attendance.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface GoogleAuthResult {
    data class Success(val user: AuthUser) : GoogleAuthResult
    data object Cancelled : GoogleAuthResult
    data class Failure(val message: String) : GoogleAuthResult
}

sealed interface DriveAuthResult {
    data class Ready(val accessToken: String) : DriveAuthResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthResult
    data class Failure(val message: String) : DriveAuthResult
}

@Serializable
private data class DriveEnvelope(
    val v: Int = 1,
    val mode: String = "std",
    val data: String? = null,
    val salt: String? = null,
    val iters: Int? = null,
    val wrapPass: String? = null,
    val recSalt: String? = null,
    val wrapRec: String? = null,
    val blob: String? = null,
    val updatedAt: Long = 0L,
)

class AttendanceRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val widgetPrefs = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
    private val credentialManager by lazy { CredentialManager.create(context) }

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var cachedDriveFileId: String? = null
    private var cachedAccessToken: String? = null

    fun loadState(): AttendanceState {
        val raw = prefs.getString(KEY_STATE, null) ?: return AttendanceState()
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val v = obj["v"]?.jsonPrimitive?.content?.toIntOrNull() ?: 3
            if (v >= 4) {
                val parsed = json.decodeFromString<AttendanceState>(raw)
                if (parsed.pw.size == 7) parsed else AttendanceState()
            } else {
                val legacy = json.decodeFromString<LegacyV3State>(raw)
                AttendanceCalculator.migrateFromV3(legacy).also { saveState(it) }
            }
        }.getOrElse { AttendanceState() }
    }

    fun saveState(state: AttendanceState) {
        prefs.edit().putString(KEY_STATE, json.encodeToString(state)).apply()
    }

    fun loadAuthUser(): AuthUser? {
        val raw = prefs.getString(KEY_AUTH_USER, null) ?: return null
        return runCatching { json.decodeFromString<AuthUser>(raw) }.getOrNull()
    }

    fun saveAuthUser(user: AuthUser?) {
        if (user == null) {
            prefs.edit().remove(KEY_AUTH_USER).apply()
        } else {
            prefs.edit().putString(KEY_AUTH_USER, json.encodeToString(user)).apply()
        }
    }

    fun wipeLocalData() {
        cachedDriveFileId = null
        cachedAccessToken = null
        prefs.edit()
            .remove(KEY_STATE)
            .remove(KEY_DATA_KEY)
            .remove(KEY_AUTH_USER)
            .apply()
        clearWidgetData()
    }

    fun isWelcomed(): Boolean = prefs.getBoolean(KEY_WELCOMED, false)

    fun setWelcomed() {
        prefs.edit().putBoolean(KEY_WELCOMED, true).apply()
    }

    fun loadDataKeyB64(): String? = prefs.getString(KEY_DATA_KEY, null)

    fun saveDataKeyB64(b64: String) {
        prefs.edit().putString(KEY_DATA_KEY, b64).apply()
    }

    fun forgetDataKey() {
        prefs.edit().remove(KEY_DATA_KEY).apply()
    }

    fun saveWidgetSummary(pct: Double, attended: Int, conducted: Int, minPct: Int) {
        widgetPrefs.edit()
            .putBoolean("hasData", true)
            .putFloat("pct", pct.toFloat())
            .putInt("a", attended)
            .putInt("c", conducted)
            .putInt("min", minPct)
            .apply()
    }

    fun clearWidgetData() {
        widgetPrefs.edit().clear().apply()
    }

    suspend fun signInWithGoogle(activityContext: Context): GoogleAuthResult {
        val webClientId = context.getString(R.string.google_web_client_id)
        suspend fun fetch(authorizedOnly: Boolean): GoogleIdTokenCredential {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(authorizedOnly)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(authorizedOnly)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = credentialManager.getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return GoogleIdTokenCredential.createFrom(credential.data)
            }
            error("Unexpected credential type")
        }

        return try {
            val cred = try {
                fetch(authorizedOnly = true)
            } catch (_: NoCredentialException) {
                fetch(authorizedOnly = false)
            }
            val user = parseJwtUser(cred.idToken, cred.id, cred.displayName)
            saveAuthUser(user)
            GoogleAuthResult.Success(user)
        } catch (_: GetCredentialCancellationException) {
            GoogleAuthResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleAuthResult.Failure("No Google account is signed in on this phone. Add one in Android Settings and try again.")
        } catch (_: Exception) {
            GoogleAuthResult.Failure("Sign-in failed. Check your connection and try again.")
        }
    }

    private fun parseJwtUser(idToken: String, fallbackEmail: String, fallbackName: String?): AuthUser {
        val parts = idToken.split(".")
        if (parts.size >= 2) {
            runCatching {
                val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
                val obj = json.parseToJsonElement(payloadJson).jsonObject
                val sub = obj["sub"]?.jsonPrimitive?.content.orEmpty()
                val email = obj["email"]?.jsonPrimitive?.content ?: fallbackEmail
                val name = obj["name"]?.jsonPrimitive?.content ?: fallbackName.orEmpty()
                if (sub.isNotBlank()) {
                    return AuthUser(uid = sub, name = name, email = email)
                }
            }
        }
        return AuthUser(uid = fallbackEmail, name = fallbackName.orEmpty(), email = fallbackEmail)
    }

    suspend fun authorizeDrive(): DriveAuthResult {
        cachedAccessToken?.let { return DriveAuthResult.Ready(it) }
        return try {
            val req = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()
            val res = Identity.getAuthorizationClient(context).authorize(req).await()
            if (res.hasResolution()) {
                val pi = res.pendingIntent ?: return DriveAuthResult.Failure("Drive permission unavailable.")
                DriveAuthResult.NeedsConsent(pi)
            } else {
                val token = res.accessToken ?: return DriveAuthResult.Failure("Could not get Drive token.")
                cachedAccessToken = token
                DriveAuthResult.Ready(token)
            }
        } catch (_: Exception) {
            DriveAuthResult.Failure("Could not connect to Google Drive.")
        }
    }

    fun handleDriveConsentIntent(data: Intent?): String? {
        return runCatching {
            val res = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            val token = res.accessToken
            if (!token.isNullOrBlank()) {
                cachedAccessToken = token
            }
            token
        }.getOrNull()
    }

    fun invalidateDriveToken() {
        cachedAccessToken = null
    }

    suspend fun signOut() {
        cachedAccessToken = null
        cachedDriveFileId = null
        saveAuthUser(null)
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    suspend fun cloudGet(accessToken: String): CloudGetResult {
        val fileIdRes = DriveBackupClient.findBackupFileId(accessToken)
        if (fileIdRes.isFailure) return CloudGetResult.NetworkError
        val fileId = fileIdRes.getOrNull() ?: return CloudGetResult.NotFound
        cachedDriveFileId = fileId

        val textRes = DriveBackupClient.downloadFile(accessToken, fileId)
        val body = textRes.getOrNull() ?: return CloudGetResult.NetworkError
        return runCatching {
            val env = json.decodeFromString<DriveEnvelope>(body)
            when (env.mode) {
                "std" -> {
                    val dataJson = env.data ?: return@runCatching CloudGetResult.NetworkError
                    CloudGetResult.Found(CloudDoc.Standard(dataJson, env.updatedAt))
                }
                "enc" -> {
                    val meta = CryptoMeta(
                        salt = env.salt ?: return@runCatching CloudGetResult.NetworkError,
                        iters = env.iters ?: CryptoHelper.PASS_ITERS,
                        wrapPass = env.wrapPass ?: return@runCatching CloudGetResult.NetworkError,
                        recSalt = env.recSalt ?: return@runCatching CloudGetResult.NetworkError,
                        wrapRec = env.wrapRec ?: return@runCatching CloudGetResult.NetworkError,
                    )
                    val blob = env.blob ?: return@runCatching CloudGetResult.NetworkError
                    CloudGetResult.Found(CloudDoc.Encrypted(meta, blob, env.updatedAt))
                }
                else -> CloudGetResult.NotFound
            }
        }.getOrElse { CloudGetResult.NetworkError }
    }

    suspend fun cloudPutStandard(
        accessToken: String,
        payload: AttendancePayload,
        updatedAt: Long,
    ): Boolean {
        val env = DriveEnvelope(
            v = 1,
            mode = "std",
            data = json.encodeToString(payload),
            updatedAt = updatedAt,
        )
        return uploadEnvelope(accessToken, env)
    }

    suspend fun cloudPutEncrypted(
        accessToken: String,
        meta: CryptoMeta,
        blob: String,
        updatedAt: Long,
    ): Boolean {
        val env = DriveEnvelope(
            v = 1,
            mode = "enc",
            salt = meta.salt,
            iters = meta.iters,
            wrapPass = meta.wrapPass,
            recSalt = meta.recSalt,
            wrapRec = meta.wrapRec,
            blob = blob,
            updatedAt = updatedAt,
        )
        return uploadEnvelope(accessToken, env)
    }

    private suspend fun uploadEnvelope(accessToken: String, env: DriveEnvelope): Boolean {
        val id = cachedDriveFileId ?: DriveBackupClient.findBackupFileId(accessToken).getOrNull()
        val res = DriveBackupClient.uploadFile(accessToken, id, json.encodeToString(env))
        if (res.isSuccess) {
            cachedDriveFileId = res.getOrNull()
            return true
        }
        return false
    }

    suspend fun cloudDelete(accessToken: String): Boolean {
        val id = cachedDriveFileId ?: DriveBackupClient.findBackupFileId(accessToken).getOrNull() ?: return true
        val ok = DriveBackupClient.deleteFile(accessToken, id).isSuccess
        if (ok) cachedDriveFileId = null
        return ok
    }

    suspend fun checkForUpdate(currentBuild: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.useCaches = false
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().readText()
                val info = json.decodeFromString<UpdateInfo>(body)
                if (info.build > currentBuild) info else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> if (cont.isActive) cont.resume(res) }
        addOnFailureListener { err -> if (cont.isActive) cont.resumeWithException(err) }
        addOnCanceledListener { cont.cancel() }
    }

    companion object {
        private const val PREFS_NAME = "attendance_app_prefs"
        const val WIDGET_PREFS_NAME = "attendance_widget_prefs"
        private const val KEY_STATE = "attendance-v3"
        private const val KEY_DATA_KEY = "attendance-key"
        private const val KEY_WELCOMED = "attendance-welcomed"
        private const val KEY_AUTH_USER = "attendance-auth-user"
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val GITHUB_BASE = "https://github.com/sreevachan/"
        private const val VERSION_URL =
            "https://raw.githubusercontent.com/sreevachan/attendance-tracker/main/version.json"
    }
}
