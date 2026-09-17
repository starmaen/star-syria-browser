package com.starsyria.browser

/**
 * فلترة بسيطة للإعلانات والمتتبعات عبر قائمة نطاقات محظورة.
 * التطبيق يحمّل هذه القائمة من ملف assets/adblock_hosts.txt (قابلة للتحديث لاحقاً
 * عبر تنزيل قائمة أحدث من الإنترنت - راجع updateFromRemote()).
 */
object AdBlocker {

    private val blockedHosts = mutableSetOf<String>()

    fun loadDefaultList(context: android.content.Context) {
        blockedHosts.clear()
        try {
            context.assets.open("adblock_hosts.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val host = line.trim()
                    if (host.isNotEmpty() && !host.startsWith("#")) {
                        blockedHosts.add(host)
                    }
                }
            }
        } catch (e: Exception) {
            // ملف القائمة غير موجود بعد - أضف قائمة أساسية مدمجة
            blockedHosts.addAll(
                listOf(
                    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                    "adservice.google.com", "ads.yahoo.com", "adnxs.com", "taboola.com",
                    "outbrain.com", "scorecardresearch.com", "moatads.com"
                )
            )
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

    /** نقطة توسعة مستقبلية: تنزيل قائمة أحدث (مثل قوائم EasyList) وتحديث blockedHosts. */
    fun updateFromRemote(url: String, onDone: (Int) -> Unit) {
        // TODO: تنفيذ التنزيل عبر OkHttp/HttpURLConnection في خيط خلفي
        // ثم استبدال محتوى blockedHosts وحفظها محلياً لإعادة الاستخدام دون اتصال
        onDone(blockedHosts.size)
    }
}
