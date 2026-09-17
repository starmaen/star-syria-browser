package com.starsyria.browser

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.webkit.*
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

/**
 * الشاشة الرئيسية لمتصفح "النجم السوري" (Star Syria Browser).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tabsRecycler: RecyclerView
    private val openTabs = mutableListOf<BrowserTab>()
    private val closedTabsStack = mutableListOf<BrowserTab>()
    private var currentTabIndex = 0
    private var isIncognitoMode = false

    private val searchEngines = mapOf(
        "Google" to "https://www.google.com/search?q=",
        "Yandex" to "https://yandex.com/search/?text=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Bing (Microsoft)" to "https://www.bing.com/search?q="
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
        openHomeTab()

        findViewById<android.widget.ImageButton>(R.id.btn_new_tab).setOnClickListener {
            openHomeTab()
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

        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (AdBlocker.isBlocked(request.url.toString())) {
                    return WebResourceResponse("text/plain", "utf-8", null)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val currentTab = openTabs.getOrNull(currentTabIndex)
                if (currentTab != null && currentTab.url != HOME_URL_MARKER) {
                    currentTab.url = url
                    currentTab.title = view.title ?: url
                    tabsRecycler.adapter?.notifyDataSetChanged()
                }
                view.evaluateJavascript(VIDEO_DOWNLOAD_JS, null)
            }
        }

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
                        navigateTabToWebView(openTabs[position])
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

    private fun openHomeTab() {
        openTabs.add(BrowserTab(HOME_URL_MARKER, title = getString(R.string.home_title), isIncognito = isIncognitoMode))
        currentTabIndex = openTabs.size - 1
        applyIncognitoWebSettings(isIncognitoMode)
        loadHomePage()
        tabsRecycler.adapter?.notifyDataSetChanged()
    }

    private fun navigateTabToWebView(tab: BrowserTab) {
        applyIncognitoWebSettings(tab.isIncognito)
        if (tab.url == HOME_URL_MARKER) loadHomePage() else webView.loadUrl(tab.url)
    }

    private fun loadHomePage() {
        webView.loadDataWithBaseURL(null, buildHomePageHtml(), "text/html", "utf-8", null)
    }

    private fun loadInCurrentTab(url: String) {
        if (openTabs.isEmpty()) openNewTab(url) else webView.loadUrl(url)
    }

    private fun closeTab(position: Int) {
        if (position !in openTabs.indices) return
        val removed = openTabs.removeAt(position)
        closedTabsStack.add(removed)

        if (openTabs.isEmpty()) {
            openHomeTab()
        } else {
            if (currentTabIndex >= openTabs.size) currentTabIndex = openTabs.size - 1
            navigateTabToWebView(openTabs[currentTabIndex])
        }
        tabsRecycler.adapter?.notifyDataSetChanged()
    }

    private fun reopenLastClosedTab() {
        if (closedTabsStack.isEmpty()) {
            android.widget.Toast.makeText(this, getString(R.string.no_closed_tabs), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val tab = closedTabsStack.removeAt(closedTabsStack.size - 1)
        openTabs.add(tab)
        currentTabIndex = openTabs.size - 1
        navigateTabToWebView(tab)
        tabsRecycler.adapter?.notifyDataSetChanged()
        android.widget.Toast.makeText(this, getString(R.string.tab_reopened), android.widget.Toast.LENGTH_SHORT).show()
    }

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

    private fun toggleIncognitoMode() {
        isIncognitoMode = !isIncognitoMode
        updateIncognitoIndicator()
        applyIncognitoWebSettings(isIncognitoMode)

        val message = if (isIncognitoMode) getString(R.string.incognito_on) else getString(R.string.incognito_off)
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()

        if (!isIncognitoMode) {
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

    private fun applyIncognitoWebSettings(incognito: Boolean) {
        webView.settings.apply {
            cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            saveFormData = !incognito
            domStorageEnabled = !incognito
        }
        CookieManager.getInstance().setAcceptCookie(!incognito)
    }

    private fun defaultShortcutSites(): List<Pair<String, String>> = listOf(
        "YouTube" to "https://www.youtube.com",
        "Telegram" to "https://web.telegram.org",
        "WhatsApp" to "https://web.whatsapp.com",
        "GitHub" to "https://github.com",
        "XDA" to "https://xdaforums.com"
    )

    private fun loadCustomSites(): List<Pair<String, String>> {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val json = prefs.getString("custom_sites", "[]") ?: "[]"
        val list = mutableListOf<Pair<String, String>>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(obj.getString("name") to obj.getString("url"))
            }
        } catch (_: Exception) { }
        return list
    }

    private fun saveCustomSite(name: String, url: String) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val current = loadCustomSites().toMutableList()
        current.add(name to url)
        val arr = JSONArray()
        current.forEach { (n, u) ->
            arr.put(JSONObject().apply { put("name", n); put("url", u) })
        }
        prefs.edit().putString("custom_sites", arr.toString()).apply()
    }

    private fun buildHomePageHtml(): String {
        val allSites = defaultShortcutSites() + loadCustomSites()
        val tilesHtml = allSites.joinToString("\n") { (name, url) ->
            val domain = Uri.parse(url).host ?: url
            """
            <div class="tile" onclick="AndroidBridge.openUrl('$url')">
                <img src="https://www.google.com/s2/favicons?sz=64&domain=$domain" />
                <span>${name.replace("'", "")}</span>
            </div>
            """.trimIndent()
        }
        return """
        <html dir="rtl">
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            body { background:#1E1E2E; margin:0; padding:24px 16px; font-family:sans-serif; }
            h2 { color:#FFC429; text-align:center; }
            .grid { display:flex; flex-wrap:wrap; justify-content:center; gap:16px; margin-top:20px; }
            .tile { width:80px; text-align:center; cursor:pointer; }
            .tile img { width:48px; height:48px; border-radius:12px; background:#2d2d44; padding:8px; }
            .tile span { display:block; color:#eee; font-size:12px; margin-top:6px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
            .add-tile { width:80px; text-align:center; cursor:pointer; }
            .add-tile .plus { width:48px; height:48px; border-radius:12px; background:#2d2d44; color:#FFC429; font-size:28px; display:flex; align-items:center; justify-content:center; margin:0 auto; }
        </style>
        </head>
        <body>
            <h2>⭐ النجم السوري</h2>
            <div class="grid">
                $tilesHtml
                <div class="add-tile" onclick="AndroidBridge.promptAddCustomSite()">
                    <div class="plus">+</div>
                    <span>${getString(R.string.add_site)}</span>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun showAddSiteDialog() {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply { hint = getString(R.string.add_site_name_hint) }
        val urlInput = EditText(this).apply {
            hint = getString(R.string.add_site_url_hint)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        container.addView(nameInput)
        container.addView(urlInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_site)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString().trim()
                var url = urlInput.text.toString().trim()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    if (!url.startsWith("http")) url = "https://$url"
                    saveCustomSite(name, url)
                    if (openTabs.getOrNull(currentTabIndex)?.url == HOME_URL_MARKER) loadHomePage()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread { loadInCurrentTab(url) }
        }

        @JavascriptInterface
        fun promptAddCustomSite() {
            runOnUiThread { showAddSiteDialog() }
        }

        @JavascriptInterface
        fun showVideoQualityPicker(sourcesJson: String) {
            runOnUiThread {
                try {
                    val arr = JSONArray(sourcesJson)
                    if (arr.length() == 0) return@runOnUiThread
                    val labels = Array<CharSequence>(arr.length()) { "" }
                    val urls = Array(arr.length()) { "" }
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        labels[i] = obj.optString("label", "الجودة ${i + 1}")
                        urls[i] = obj.optString("url", "") 
                    }
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.choose_quality)
                        .setItems(labels) { _, which ->
                            downloadUrl(urls[which]) 
                        }
                        .show()
                } catch (_: Exception) { }
            }
        }
    }

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

        const val HOME_URL_MARKER = "startpage://home"

        const val VIDEO_DOWNLOAD_JS = """
        (function() {
            function parseSources(video) {
                var sources = [];
                var srcEls = video.querySelectorAll('source');
                if (srcEls.length > 0) {
                    srcEls.forEach(function(s) {
                        var label = s.getAttribute('label') || s.getAttribute('res') || s.getAttribute('title') || (s.src.split('/').pop());
                        if (s.src) sources.push({url: s.src, label: label});
                    });
                }
                if (sources.length === 0 && (video.currentSrc || video.src)) {
                    sources.push({url: video.currentSrc || video.src, label: 'الجودة الافتراضية'});
                }
                return sources;
            }
            function addButton(video) {
                if (video.dataset.starDlAdded) return;
                video.dataset.starDlAdded = "1";
                var btn = document.createElement('div');
                btn.innerText = '⬇ تنزيل الفيديو';
                btn.style.cssText = 'position:relative;z-index:9999;background:#FFC429;color:#1a1a2a;padding:8px 14px;border-radius:20px;text-align:center;font-family:sans-serif;font-size:14px;margin:6px auto;width:fit-content;cursor:pointer;box-shadow:0 2px 6px rgba(0,0,0,0.3);';
                btn.onclick = function(e) {
                    e.preventDefault(); e.stopPropagation();
                    var sources = parseSources(video);
                    if (window.AndroidBridge && sources.length > 0) {
                        AndroidBridge.showVideoQualityPicker(JSON.stringify(sources));
                    }
                };
                if (video.parentNode) {
                    video.parentNode.insertBefore(btn, video.nextSibling);
                }
            }
            function scan() {
                document.querySelectorAll('video').forEach(addButton);
            }
            scan();
            if (!window.__starDlObserver) {
                window.__starDlObserver = new MutationObserver(scan);
                window.__starDlObserver.observe(document.body, {childList: true, subtree: true});
            }
        })();
        """
    }
}
