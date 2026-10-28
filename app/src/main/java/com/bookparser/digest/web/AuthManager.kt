package com.bookparser.digest.web

import android.content.Context
import android.webkit.CookieManager

class AuthManager(private val context: Context) {

    private val cookieManager: CookieManager = CookieManager.getInstance()

    fun getCookies(): String {
        return cookieManager.getCookie("https://4pda.to")
            ?: cookieManager.getCookie("https://4pda.to/forum/")
            ?: ""
    }

    fun isLoggedIn(): Boolean {
        val cookies = getCookies()
        val memberCookie = cookies.split(";").map { it.trim() }.find { it.startsWith("member_id=") }
        val passHash = cookies.split(";").map { it.trim() }.find { it.startsWith("pass_hash=") }
        return memberCookie != null &&
               memberCookie != "member_id=0" &&
               memberCookie != "member_id=" &&
               passHash != null &&
               passHash != "pass_hash=0" &&
               passHash != "pass_hash="
    }
}
