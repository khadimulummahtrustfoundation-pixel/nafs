package com.NafsGuard.guard

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {
    const val ENDPOINT =
        "https://script.google.com/macros/s/AKfycbz1O2A9v49gd1TVEwTiAd15-vzEu7gMaRq-q0zXI1JjTVYrKrlRXdflU9aEUBDvCgMA/exec"

    data class Res(
        val ok: Boolean, val msg: String,
        val name: String = "", val days: Int = 0,
        val net: Boolean = false, val approved: Boolean = false,
        val unlock: Boolean = false,
        val fbAccess: Boolean = false, val igAccess: Boolean = false,
        val mediaAccess: Boolean = false,
        val off: Boolean = false          // Google Sheet-এ LoginAccess OFF করা হয়েছে
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun call(query: String): Res {
        val body: String
        try {
            val cn = URL("$ENDPOINT?$query").openConnection() as HttpURLConnection
            cn.connectTimeout = 20000
            cn.readTimeout = 20000
            cn.instanceFollowRedirects = true
            body = cn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return Res(false, "ইন্টারনেট বা সার্ভারে সমস্যা, আবার চেষ্টা করুন", net = true)
        }
        return try {
            val j = JSONObject(body)
            Res(j.optBoolean("ok"), j.optString("msg"), j.optString("name"),
                j.optInt("daysLeft"), approved = j.optBoolean("approved"), unlock = j.optBoolean("unlock"),
                fbAccess = j.optBoolean("fbReel"), igAccess = j.optBoolean("igReel"),
                mediaAccess = j.optBoolean("media"), off = j.optBoolean("off"))
        } catch (e: Exception) {
            Res(false, "সার্ভার সঠিক উত্তর দেয়নি। Apps Script নতুন কোড দিয়ে Deploy (Anyone) করা হয়েছে কিনা দেখুন।", net = true)
        }
    }

    fun login(phone: String, pass: String) =
        call("action=login&phone=${enc(phone)}&password=${enc(pass)}")

    fun register(name: String, phone: String, pass: String) =
        call("action=register&name=${enc(name)}&phone=${enc(phone)}&password=${enc(pass)}")
}
