package com.zigorminsk.textbook

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Проверка последнего релиза в репозитории через GitHub API. */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/zigorminsk-debug/learning/releases/latest"

    /** Выключается в unit-тестах, чтобы не ходить в сеть. */
    @Volatile
    var enabled: Boolean = true

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class Result(val ok: Boolean, val latestBuild: Int?, val tag: String?)

    /** «1.0.6» -> 6; «apk-v1.0.6» -> 6. */
    fun buildNumber(text: String?): Int? =
        Regex("""(\d+)\.(\d+)\.(\d+)""").find(text ?: "")
            ?.groupValues?.get(3)?.toIntOrNull()

    fun check(callback: (Result) -> Unit) {
        if (!enabled) return
        executor.execute {
            val result = fetch()
            mainHandler.post { callback(result) }
        }
    }

    private fun fetch(): Result = try {
        val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val code = conn.responseCode
        if (code == 200) {
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()
            val tag = JSONObject(body).optString("tag_name", "")
            if (tag.isNotEmpty()) {
                Result(true, buildNumber(tag), tag)
            } else {
                Result(false, null, null)
            }
        } else {
            conn.disconnect()
            Result(false, null, null)
        }
    } catch (e: Exception) {
        Result(false, null, null)
    }
}
