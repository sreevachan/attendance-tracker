package com.sreevachan.attendance.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ApkUpdater {
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(apkUrl.startsWith(AttendanceRepository.GITHUB_BASE)) {
                "Untrusted update link."
            }
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(dir, "attendance-update.apk")

            var currentUrl = apkUrl
            var conn: HttpURLConnection? = null
            // Follow redirects manually across github.com -> objects.githubusercontent.com
            for (hop in 0 until 6) {
                val c = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 10000
                    readTimeout = 20000
                }
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: error("Missing redirect location")
                    c.disconnect()
                    currentUrl = loc
                    continue
                }
                conn = c
                break
            }
            val finalConn = conn ?: error("Too many redirects")
            try {
                if (finalConn.responseCode !in 200..299) {
                    error("Download failed (${finalConn.responseCode})")
                }
                val total = finalConn.contentLengthLong
                var downloaded = 0L
                val buf = ByteArray(16 * 1024)
                finalConn.inputStream.use { input ->
                    outFile.outputStream().use { output ->
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) {
                                onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            } finally {
                finalConn.disconnect()
            }

            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile,
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
            }
        }
    }
}
