package com.starsyria.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * الشاشة الرئيسية لمتصفح "النجم السوري" (Star Syria Browser).
 * تحتوي: WebView، درج التبويبات المفتوحة، شريط بحث، تنزيل الملفات/الفيديو،
 * تنظيف الكاش، وزر مختصر لتفعيل VPN (يفتح تطبيق Proton VPN إن كان مثبتاً).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tabsRecycler: RecyclerView
    private val openTabs = mutableListOf<BrowserTab>()
    private var currentTabIndex = 0

    // محركات البحث الأساسية - يختارها المستخدم من الإعدادات
    private val searchEngines = mapOf(
        "Google" to "https://www.google.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Bing" to "https://www.bing.com/search?q="
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AdBlocker.loadDefaultList(this)

        drawerLayout = findViewById(R.id.drawer_layout)
        tabsRecycler = findViewById(R.id.tabs_recycler)
        webView = findViewById(R.id.webview)

        setupWebView()
        setupTabsDrawer()
        openNewTab("https://www.google.com")

        findViewById<android.widget.ImageButton>(R.id.btn_new_tab).setOnClickListener {
            openNewTab(defaultHomeUrl())
        }
        findViewById<android.widget.ImageButton>(R.id.btn_tabs).setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.END)
        }
        findViewById<android.widget.ImageButton>(R.id.btn_vpn).setOnClickListener {
            launchVpnApp()
        }
        findViewById<android.widget.ImageButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<SearchView>(R.id.search_bar).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query ?: return true
                val finalUrl = resolveInput(query)
                loadInCurrentTab(finalUrl)
                return true
            }
            override fun onQueryTextChange(newText: String?) = false
        })
    }

    private fun defaultHomeUrl() = "https://www.google.com"

    /** يحدد إن كان النص المدخل رابطاً أو عبارة بحث، ويبني الرابط النهائي. */
    private fun resolveInput(input: String): String {
        val looksLikeUrl = input.contains(".") && !input.contains(" ")
        return if (looksLikeUrl) {
            if (input.startsWith("http")) input else "https://$input"
        } else {
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val engine = prefs.getString("search_engine", "Google") ?: "Google"
            (searchEngines[engine] ?: searchEngines["Google"]!!) + Uri.encode(input)
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // فلترة الإعلانات: أي طلب لنطاق محظور يُرجع استجابة فارغة
                if (AdBlocker.isBlocked(request.url.toString())) {
                    return WebResourceResponse("text/plain", "utf-8", null)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                openTabs.getOrNull(currentTabIndex)?.let {
                    it.url = url
                    it.title = view.title ?: url
                    tabsRecycler.adapter?.notifyDataSetChanged()
                }
            }
        }

        // دعم تنزيل الملفات والفيديوهات عبر مدير التنزيلات النظامي
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                setDescription("جاري التنزيل عبر النجم السوري")
                setTitle(URLUtilFileName(url, contentDisposition, mimeType))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, URLUtilFileName(url, contentDisposition, mimeType))
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        }
    }

    private fun URLUtilFileName(url: String, contentDisposition: String?, mimeType: String?): String =
        URLUtil.guessFileName(url, contentDisposition, mimeType)

    // ---------- إدارة التبويبات ----------

    private fun setupTabsDrawer() {
        tabsRecycler.layoutManager = LinearLayoutManager(this)
        tabsRecycler.adapter = object : RecyclerView.Adapter<TabViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TabViewHolder {
                val v = layoutInflater.inflate(R.layout.item_tab, parent, false)
                return TabViewHolder(v)
            }
            override fun getItemCount() = openTabs.size
            override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
                holder.bind(openTabs[position]) {
                    currentTabIndex = position
                    webView.loadUrl(openTabs[position].url)
                    drawerLayout.closeDrawers()
                }
            }
        }
    }

    private fun openNewTab(url: String) {
        openTabs.add(BrowserTab(url))
        currentTabIndex = openTabs.size - 1
        webView.loadUrl(url)
        tabsRecycler.adapter?.notifyDataSetChanged()
    }

    private fun loadInCurrentTab(url: String) {
        if (openTabs.isEmpty()) openNewTab(url) else webView.loadUrl(url)
    }

    // ---------- VPN ----------

    /**
     * فتح تطبيق Proton VPN المجاني إن كان مثبتاً على الجهاز (لا يوجد SDK رسمي مجاني
     * لدمج بروتون داخل تطبيقات الطرف الثالث). إن لم يكن مثبتاً يوجّه المستخدم لصفحته.
     */
    private fun launchVpnApp() {
        val protonPackage = "ch.protonvpn.android"
        val launchIntent = packageManager.getLaunchIntentForPackage(protonPackage)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$protonPackage")))
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
