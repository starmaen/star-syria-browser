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
    private val closedTabsStack = mutableListOf<BrowserTab>()
    private var currentTabIndex = 0
    private var isIncognitoMode = false

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
        AdBlocker.autoUpdateIfDue(this)

        drawerLayout = findViewById(R.id.drawer_layout)
        tabsRecycler = findViewById(R.id.tabs_recycler)
        webView = findViewById(R.id.webview)

        setupWebView()
        setupTabsDrawer()
        setupLinkContextMenu()
        openNewTab("https://www.google.com")

        findViewById<android.widget.ImageButton>(R.id.btn_new_tab).setOnClickListener {
            openNewTab(defaultHomeUrl())
        }
        findViewById<android.widget.ImageButton>(R.id.btn_tabs).setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.END)
        }
        findViewById<android.widget.ImageButton>(R.id.btn_reopen_tab).setOnClickListener {
            reopenLastClosedTab()
        }
        findViewById<android.widget.ImageButton>(R.id.btn_incognito).setOnClickListener {
            toggleIncognitoMode()
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
                holder.bind(
                    openTabs[position],
                    onClick = {
                        currentTabIndex = position
                        applyIncognitoWebSettings(openTabs[position].isIncognito)
                        webView.loadUrl(openTabs[position].url)
                        drawerLayout.closeDrawers()
                    },
                    onClose = { closeTab(position) }
                )
            }
        }
    }

    private fun openNewTab(url: String) {
        openTabs.add(BrowserTab(url, isIncognito = isIncognitoMode))
        currentTabIndex = openTabs.size - 1
        applyIncognitoWebSettings(isIncognitoMode)
        webView.loadUrl(url)
        tabsRecycler.adapter?.notifyDataSetChanged()
    }

    private fun loadInCurrentTab(url: String) {
        if (openTabs.isEmpty()) openNewTab(url) else webView.loadUrl(url)
    }

    /** يغلق تبويباً محدداً ويحفظه في مكدس التبويبات المغلقة لاسترجاعه لاحقاً. */
    private fun closeTab(position: Int) {
        if (position !in openTabs.indices) return
        val removed = openTabs.removeAt(position)
        closedTabsStack.add(removed)

        if (openTabs.isEmpty()) {
            openNewTab(defaultHomeUrl())
        } else {
            if (currentTabIndex >= openTabs.size) currentTabIndex = openTabs.size - 1
            applyIncognitoWebSettings(openTabs[currentTabIndex].isIncognito)
            webView.loadUrl(openTabs[currentTabIndex].url)
        }
        tabsRecycler.adapter?.notifyDataSetChanged()
    }

    /** يعيد فتح آخر تبويب مغلق (يدعم استرجاعات متعددة متتالية). */
    private fun reopenLastClosedTab() {
        if (closedTabsStack.isEmpty()) {
            android.widget.Toast.makeText(this, getString(R.string.no_closed_tabs), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val tab = closedTabsStack.removeAt(closedTabsStack.size - 1)
        openTabs.add(tab)
        currentTabIndex = openTabs.size - 1
        applyIncognitoWebSettings(tab.isIncognito)
        webView.loadUrl(tab.url)
        tabsRecycler.adapter?.notifyDataSetChanged()
        android.widget.Toast.makeText(this, getString(R.string.tab_reopened), android.widget.Toast.LENGTH_SHORT).show()
    }

    // ---------- قائمة السياق (الضغط المطوّل على الروابط) ----------

    /**
     * يفعّل قائمة سياق أندرويد القياسية عند الضغط المطوّل على أي رابط داخل الصفحة:
     * نسخ الرابط، مشاركته، فتحه بتبويب جديد، أو تنزيله مباشرة.
     */
    private fun setupLinkContextMenu() {
        registerForContextMenu(webView)
    }

    override fun onCreateContextMenu(
        menu: android.view.ContextMenu,
        v: android.view.View,
        menuInfo: android.view.ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val result = webView.hitTestResult
        val linkUrl: String? = when (result.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> result.extra
            else -> null
        } ?: return

        menu.setHeaderTitle(linkUrl)
        menu.add(0, MENU_OPEN_NEW_TAB, 0, getString(R.string.ctx_open_new_tab)).setOnMenuItemClickListener {
            openNewTab(linkUrl); true
        }
        menu.add(0, MENU_COPY_LINK, 1, getString(R.string.ctx_copy_link)).setOnMenuItemClickListener {
            copyToClipboard(linkUrl); true
        }
        menu.add(0, MENU_SHARE_LINK, 2, getString(R.string.ctx_share_link)).setOnMenuItemClickListener {
            shareLink(linkUrl); true
        }
        menu.add(0, MENU_DOWNLOAD_LINK, 3, getString(R.string.ctx_download_link)).setOnMenuItemClickListener {
            downloadUrl(linkUrl); true
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("link", text))
        android.widget.Toast.makeText(this, getString(R.string.ctx_link_copied), android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun shareLink(url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.ctx_share_link)))
    }

    private fun downloadUrl(url: String) {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setDescription("جاري التنزيل عبر النجم السوري")
            setTitle(URLUtilFileName(url, null, null))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, URLUtilFileName(url, null, null))
        }
        (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    // ---------- التصفح الخفي (Incognito) ----------

    /**
     * تبديل وضع التصفح الخفي. أي تبويب جديد يُفتح بعدها لا يحفظ كوكيز أو كاش،
     * ولا يُحفظ في سجل التصفح، وتُنظّف بيانات الجلسة عند إيقافه.
     */
    private fun toggleIncognitoMode() {
        isIncognitoMode = !isIncognitoMode
        updateIncognitoIndicator()
        applyIncognitoWebSettings(isIncognitoMode)

        val message = if (isIncognitoMode) getString(R.string.incognito_on) else getString(R.string.incognito_off)
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()

        if (!isIncognitoMode) {
            // عند إيقاف الوضع الخفي: نظّف أي بيانات جلسة تراكمت أثناءه
            CookieManager.getInstance().removeSessionCookies(null)
            webView.clearHistory()
        }
    }

    private fun updateIncognitoIndicator() {
        val toolbar = findViewById<android.widget.LinearLayout>(R.id.toolbar_row)
        toolbar.setBackgroundColor(
            if (isIncognitoMode) android.graphics.Color.parseColor("#3A2E5C")
            else android.graphics.Color.parseColor("#1E1E2E")
        )
    }

    /** يضبط إعدادات WebView بحيث لا يحتفظ بكاش/كوكيز/بيانات نماذج أثناء التصفح الخفي. */
    private fun applyIncognitoWebSettings(incognito: Boolean) {
        webView.settings.apply {
            cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            saveFormData = !incognito
            domStorageEnabled = !incognito
        }
        CookieManager.getInstance().setAcceptCookie(!incognito)
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

    companion object {
        private const val MENU_OPEN_NEW_TAB = 1
        private const val MENU_COPY_LINK = 2
        private const val MENU_SHARE_LINK = 3
        private const val MENU_DOWNLOAD_LINK = 4
    }
}
