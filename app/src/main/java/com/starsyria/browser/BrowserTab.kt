package com.starsyria.browser

/** يمثل تبويب (صفحة مفتوحة) واحد في متصفح النجم السوري. */
data class BrowserTab(
    var url: String,
    var title: String = "صفحة جديدة",
    var faviconBytes: ByteArray? = null
)
