package com.NafsGuard.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging

/** FCM সংক্রান্ত সহায়ক: token আনা, topic-এ যোগ, ইউজারকে নোটিফিকেশন দেখানো */
object Push {
    const val TOPIC_ALL = "all"

    /** FCM token আনে; Firebase সেট না থাকলে বা Google Play Services না থাকলে "" দিয়ে callback */
    fun fetchToken(cb: (String) -> Unit) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                cb(if (t.isSuccessful) (t.result ?: "") else "")
            }
        } catch (e: Throwable) { cb("") }
    }

    fun subscribeAll() {
        try { FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL) } catch (e: Throwable) {}
    }

    fun unsubscribeAll() {
        try { FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_ALL) } catch (e: Throwable) {}
    }

    /** নতুন token শীটে পাঠানো (রেজিস্টার্ড হলে) */
    fun sendToken(c: Context, token: String) {
        if (!Prefs.registered(c) || token.isEmpty()) return
        val phone = Prefs.phone(c)
        Thread { try { Api.registerToken(phone, token) } catch (e: Exception) {} }.start()
    }

    private var nid = 5000

    fun notify(c: Context, title: String, body: String) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel("nafs_push", "NafsGuard বার্তা", NotificationManager.IMPORTANCE_HIGH))
            }
            val open = PendingIntent.getActivity(c, 0, Intent(c, LoginActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, "nafs_push") else Notification.Builder(c)
            val n = b.setContentTitle(title).setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(open).setAutoCancel(true).build()
            nm.notify(nid++, n)
        } catch (e: Throwable) {}
    }
}
