package com.sreevachan.attendance.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class DriveFileItem(
    val id: String,
    val name: String = "",
)

@Serializable
private data class DriveFileListResponse(
    val files: List<DriveFileItem> = emptyList(),
)

/**
 * Stores the backup directly inside the user's own hidden Google Drive appDataFolder.
 * Nothing is written to the developer's Firebase/Firestore account, so no user list exists.
 */
object DriveBackupClient {
    private const val BACKUP_FILENAME = "attendance-backup.json"
    private const val FILES_API = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findBackupFileId(accessToken: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode("name='$BACKUP_FILENAME' and trashed=false", "UTF-8")
            val url = URL("$FILES_API?spaces=appDataFolder&q=$q&fields=files(id,name)")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 8000
                readTimeout = 8000
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("Drive list failed ($code)")
                }
                val body = conn.inputStream.bufferedReader().readText()
                val parsed = json.decodeFromString<DriveFileListResponse>(body)
                parsed.files.firstOrNull()?.id
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun downloadFile(accessToken: String, fileId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$FILES_API/$fileId?alt=media")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 8000
                readTimeout = 10000
            }
            try {
                if (conn.responseCode !in 200..299) {
                    error("Drive download failed (${conn.responseCode})")
                }
                conn.inputStream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun uploadFile(
        accessToken: String,
        existingFileId: String?,
        jsonContent: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (existingFileId != null) {
                val url = URL("$UPLOAD_API/$existingFileId?uploadType=media")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PATCH"
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 8000
                    readTimeout = 10000
                }
                try {
                    conn.outputStream.use { it.write(jsonContent.toByteArray(Charsets.UTF_8)) }
                    if (conn.responseCode !in 200..299) {
                        error("Drive update failed (${conn.responseCode})")
                    }
                    existingFileId
                } finally {
                    conn.disconnect()
                }
            } else {
                val boundary = "attendance_boundary_${System.currentTimeMillis()}"
                val metadata = """{"name":"$BACKUP_FILENAME","parents":["appDataFolder"]}"""
                val body = buildString {
                    append("--$boundary\r\n")
                    append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(metadata)
                    append("\r\n--$boundary\r\n")
                    append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(jsonContent)
                    append("\r\n--$boundary--")
                }
                val url = URL("$UPLOAD_API?uploadType=multipart&fields=id")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                    connectTimeout = 8000
                    readTimeout = 10000
                }
                try {
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    if (conn.responseCode !in 200..299) {
                        error("Drive upload failed (${conn.responseCode})")
                    }
                    val resp = conn.inputStream.bufferedReader().readText()
                    json.decodeFromString<DriveFileItem>(resp).id
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    suspend fun deleteFile(accessToken: String, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$FILES_API/$fileId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 8000
                readTimeout = 8000
            }
            try {
                if (conn.responseCode !in 200..299 && conn.responseCode != 404) {
                    error("Drive delete failed (${conn.responseCode})")
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}
