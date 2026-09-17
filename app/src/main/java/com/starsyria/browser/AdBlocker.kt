package com.starsyria.browser

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * فلترة الإعلانات والمتتبعات عبر قائمة نطاقات محظورة.
 * الأولوية: قائمة محدّثة محفوظة محلياً (من آخر تحديث عبر الإنترنت) → ملف افتراضي
 * مرفق بالتطبيق (assets/adblock_hosts.txt) → قائمة أساسية مدمجة بالكود كحل أخير.
 */
object AdBlocker {

    private const val PREFS_NAME = "adblock_prefs"
    private const val KEY_LAST_UPDATE = "last_update_time"
    private const val CACHE_FILE_NAME = "adblock_custom_hosts.txt"

    // مصدر قائمة حظر إعلانات مفتوحة ومحدّثة (تنسيق hosts قياسي)
    private const val DEFAULT_REMOTE_URL =
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"

    // كل كم مدة يُسمح بتحديث تلقائي (7 أيام)
    private const val UPDATE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

    private val blockedHosts = mutableSetOf<String>()

    /** يحمّل القائمة عند إقلاع التطبيق: المحدّثة محلياً إن وُجدت، وإلا القائمة الافتراضية. */
    fun loadDefaultList(context: Context) {
        blockedHosts.clear()
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        if (cacheFile.exists()) {
            try {
                cacheFile.bufferedReader().useLines { lines -> parseInto(lines) }
                if (blockedHosts.isNotEmpty()) return
            } catch (_: Exception) { /* نتابع للقائمة الافتراضية عند أي خطأ */ }
        }
        loadBundledAssetList(context)
    }

    private fun loadBundledAssetList(context: Context) {
        try {
            context.assets.open("adblock_hosts.txt").bufferedReader().useLines { lines -> parseInto(lines) }
        } catch (e: Exception) {
            blockedHosts.addAll(
                listOf(
                    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                    "adservice.google.com", "ads.yahoo.com", "adnxs.com", "taboola.com",
                    "outbrain.com", "scorecardresearch.com", "moatads.com"
                )
            )
        }
    }

    private fun parseInto(lines: Sequence<String>) {
        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            // يدعم تنسيق "0.0.0.0 domain.com" أو "domain.com" مباشرة
            val host = line.split(Regex("\\s+")).lastOrNull()?.trim()
            if (!host.isNullOrEmpty() && host != "0.0.0.0" && host != "localhost") {
                blockedHosts.add(host)
            }
        }
    }

    fun isBlocked(url: String): Boolean {
        return try {
            val host = android.net.Uri.parse(url).host ?: return false
            blockedHosts.any { host == it || host.endsWith(".$it") }
        } catch (e: Exception) {
            false
        }
    }

    fun blockedCount(): Int = blockedHosts.size

    /** آخر وقت تحديث ناجح، أو 0 إن لم يحدث تحديث بعد. */
    fun lastUpdateTime(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_LAST_UPDATE, 0L)

    /**
     * يتحقق تلقائياً عند الإقلاع إن مرّ أسبوع على آخر تحديث، وإن كان كذلك
     * يجلب قائمة أحدث في الخلفية دون إبطاء واجهة المستخدم.
     */
    fun autoUpdateIfDue(context: Context) {
        val elapsed = System.currentTimeMillis() - lastUpdateTime(context)
        if (elapsed < UPDATE_INTERVAL_MS) return
        updateFromRemote(context, DEFAULT_REMOTE_URL) { /* صامت: تحديث خلفي دوري */ }
    }

    /**
     * يجلب قائمة حظر أحدث من الإنترنت في خيط خلفي، يحفظها محلياً، ويستبدل
     * القائمة الحالية بها. onDone يُستدعى على الخيط الرئيسي بعدد النطاقات النهائي،
     * أو -1 في حال فشل التحديث (يبقى التطبيق يعمل بالقائمة الحالية دون توقف).
     */
    fun updateFromRemote(context: Context, url: String = DEFAULT_REMOTE_URL, onDone: (Int) -> Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            var success = false
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.requestMethod = "GET"

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val newHosts = mutableSetOf<String>()
                    text.lineSequence().forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) return@forEach
                        val host = line.split(Regex("\\s+")).lastOrNull()?.trim()
                        if (!host.isNullOrEmpty() && host != "0.0.0.0" && host != "localhost") {
                            newHosts.add(host)
                        }
                    }
                    if (newHosts.isNotEmpty()) {
                        File(context.filesDir, CACHE_FILE_NAME).writeText(newHosts.joinToString("\n"))
                        blockedHosts.clear()
                        blockedHosts.addAll(newHosts)
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                            .apply()
                        success = true
                    }
                }
                connection.disconnect()
            } catch (_: Exception) {
                // لا اتصال إنترنت أو فشل التنزيل: نتجاهل بصمت ونُبقي القائمة الحالية سارية
            }
            mainHandler.post { onDone(if (success) blockedHosts.size else -1) }
        }.start()
    }
}
