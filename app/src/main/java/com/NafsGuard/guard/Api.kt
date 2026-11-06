package com.NafsGuard.guard

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {
    const val ENDPOINT = "https://nafsguard-api-new.khadimulummahtrustfoundation.workers.dev"

    data class Res(
        val ok: Boolean, val msg: String,
        val name: String = "", val days: Int = 0,
        val net: Boolean = false, val approved: Boolean = false,
        val unlock: Boolean = false,
        val fbAccess: Boolean = false, val igAccess: Boolean = false,
        val mediaAccess: Boolean = false,
        val ytAccess: Boolean = false,
        val off: Boolean = false
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
                mediaAccess = j.optBoolean("media"), ytAccess = j.optBoolean("ytShorts"), off = j.optBoolean("off"))
        } catch (e: Exception) {
            Res(false, "সার্ভার সঠিক উত্তর দেয়নি, একটু পরে আবার চেষ্টা করুন", net = true)
        }
    }

    // সার্ভার (D1 "rules" টেবিল) থেকে স্ক্রিন-চেনার নিয়ম (JSON লেখা) নামানো; না পেলে null
    fun rules(): String? {
        return try {
            val cn = URL("$ENDPOINT?action=rules").openConnection() as HttpURLConnection
            cn.connectTimeout = 20000
            cn.readTimeout = 20000
            cn.instanceFollowRedirects = true
            val body = cn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(body)
            if (j.optBoolean("ok")) j.optJSONObject("rules")?.toString() else null
        } catch (e: Exception) { null }
    }

    // রেজিস্ট্রেশন: সার্ভারে শুধু নাম, ফোন, FCM token ও (সার্ভারের বসানো) তারিখ-সময় যায়। এটি শুধু ডাটা সেভ, কোনো নিয়ন্ত্রণ নয়
    fun register(name: String, phone: String, token: String) =
        call("action=register&name=${enc(name)}&phone=${enc(phone)}&token=${enc(token)}")

    // FCM token বদলালে সার্ভারে নতুন token সেভ
    fun registerToken(phone: String, token: String) =
        call("action=token&phone=${enc(phone)}&token=${enc(token)}")

    class Ver(val code: Int, val name: String, val url: String)

    // সর্বশেষ ভার্সন (D1 "app_config" টেবিল); নেটওয়ার্ক বা সার্ভারের সমস্যা হলে null
    fun version(): Ver? {
        return try {
            val cn = URL("$ENDPOINT?action=version").openConnection() as HttpURLConnection
            cn.connectTimeout = 20000
            cn.readTimeout = 20000
            cn.instanceFollowRedirects = true
            val body = cn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(body)
            if (!j.optBoolean("ok")) null
            else Ver(j.optInt("code"), j.optString("name").trim(), j.optString("url").trim())
        } catch (e: Exception) { null }
    }
}
