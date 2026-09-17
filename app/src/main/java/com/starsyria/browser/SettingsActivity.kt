package com.starsyria.browser

import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        // اختيار محرك البحث الافتراضي
        val engines = arrayOf("Google", "DuckDuckGo", "Bing")
        val spinner = findViewById<Spinner>(R.id.spinner_search_engine)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, engines)
        val savedEngine = prefs.getString("search_engine", "Google")
        spinner.setSelection(engines.indexOf(savedEngine).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                prefs.edit().putString("search_engine", engines[pos]).apply()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        // تنظيف الكاش وبيانات التصفح
        findViewById<Button>(R.id.btn_clear_cache).setOnClickListener {
            clearBrowsingData()
            Toast.makeText(this, "تم تنظيف الكاش وبيانات التصفح", Toast.LENGTH_SHORT).show()
        }

        // تحديث قائمة حظر الإعلانات يدوياً + عرض حالة آخر تحديث
        val statusView = findViewById<TextView>(R.id.tv_adblock_status)
        updateAdblockStatusText(statusView)
        findViewById<Button>(R.id.btn_update_adblock).setOnClickListener {
            statusView.text = getString(R.string.adblock_updating)
            AdBlocker.updateFromRemote(this) { count ->
                if (count >= 0) {
                    Toast.makeText(this, getString(R.string.adblock_update_success, count), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.adblock_update_failed), Toast.LENGTH_SHORT).show()
                }
                updateAdblockStatusText(statusView)
            }
        }

        // رقم الإصدار أسفل شاشة الإعدادات
        val versionText = findViewById<TextView>(R.id.tv_version)
        val pInfo = packageManager.getPackageInfo(packageName, 0)
        versionText.text = getString(R.string.version_format, pInfo.versionName, pInfo.longVersionCode)
    }

    private fun updateAdblockStatusText(view: TextView) {
        val last = AdBlocker.lastUpdateTime(this)
        view.text = if (last == 0L) {
            getString(R.string.adblock_never_updated, AdBlocker.blockedCount())
        } else {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(last))
            getString(R.string.adblock_last_updated, AdBlocker.blockedCount(), date)
        }
    }

    private fun clearBrowsingData() {
        WebView(this).apply {
            clearCache(true)
            clearHistory()
            clearFormData()
        }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        cacheDir.deleteRecursively()
    }
}
