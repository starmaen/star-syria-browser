package com.starsyria.browser

data class BrowserTab(
    var url: String = "", // جعلناها String عادية وليست String? لحل مشكلة Type Mismatch
    var title: String = url,
    val isIncognito: Boolean = false
)
