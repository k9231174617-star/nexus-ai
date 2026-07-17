package com.nexus.agent.core.browser

import android.webkit.CookieManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CookieJar @Inject constructor() {

    private val cookieManager = CookieManager.getInstance()

    fun setCookie(url: String, cookie: String) {
        cookieManager.setCookie(url, cookie)
    }

    fun getCookie(url: String): String? = cookieManager.getCookie(url)

    fun removeAllCookies() {
        cookieManager.removeAllCookies(null)
    }

    fun hasCookies(url: String): Boolean = !getCookie(url).isNullOrBlank()

    fun flush() {
        cookieManager.flush()
    }
}
