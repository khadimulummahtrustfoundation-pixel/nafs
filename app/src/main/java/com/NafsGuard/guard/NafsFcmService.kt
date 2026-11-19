package com.NafsGuard.guard

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** অ্যাডমিনের Push Command গ্রহণ (শুধু data message)। */
class NafsFcmService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val from = message.from ?: ""
        try { Remote.handle(applicationContext, message.data, from.startsWith("/topics/")) } catch (e: Throwable) {}
    }

    override fun onNewToken(token: String) {
        Push.sendToken(applicationContext, token)
    }
}
