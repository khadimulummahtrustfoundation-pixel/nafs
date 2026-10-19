package com.NafsGuard.guard

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {
    const val ENDPOINT =
        "https://script.google.com/macros/s/AKfycbzR1i8uXA42dcUCoJelPqNUAqfQYawbfRlcpbt4M2_YAlGIG0LayWtKHLdjnpTzeZfu/exec"

    data class Res(
        val ok: Boolean, val msg: String,
        val name: String = "", val days: Int = 0, val net: Boolean = false
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun login(phone: String, pass: String): Res = try {
        val u = URL("$ENDPOINT?action=login&phone=${enc(phone)}&password=${enc(pass)}")
        val cn = u.openConnection() as HttpURLConnection
        cn.connectTimeout = 15000
        cn.readTimeout = 15000
        cn.instanceFollowRedirects = true
        val body = cn.inputStream.bufferedReader().use { it.readText() }
        val j = JSONObject(body)
        Res(j.optBoolean("ok"), j.optString("msg"), j.optString("name"), j.optInt("daysLeft"))
    } catch (e: Exception) {
        Res(false, "ইন্টারনেট বা সার্ভারে সমস্যা, আবার চেষ্টা করুন", net = true)
    }
}
