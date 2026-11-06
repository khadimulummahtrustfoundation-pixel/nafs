package com.NafsGuard.guard

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * ভার্সন চেক: সার্ভারের (Cloudflare D1) latest_version_code, latest_version_name ও update_url দিনে একবার ব্যাকগ্রাউন্ডে দেখে।
 * সার্ভারের কোড অ্যাপের versionCode-এর চেয়ে বড় হলে আপডেট পপ-আপ দেখায় (অ্যাপ খোলা থাকলে সাথে সাথে, নইলে পরের বার খোলার সময়) এবং নোটিফিকেশন দেয়।
 */
object UpdateCheck {
    private const val WORK = "nafs_update_check"

    fun schedule(c: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(c.applicationContext)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } catch (e: Throwable) {}
    }

    /** true = চেক সম্পন্ন, false = নেটওয়ার্ক/সার্ভারের কারণে পরে আবার চেষ্টা */
    fun run(c: Context): Boolean {
        return try {
            val v = Api.version() ?: return false
            if (v.code <= 0) return true
            Prefs.setUpdate(c, v.code, v.name, v.url)
            if (Update.needed(c)) {
                if (Prefs.updNotified(c) != v.code) {
                    Prefs.setUpdNotified(c, v.code)
                    val label = if (v.name.isNotEmpty()) " ${v.name}" else ""
                    Push.notify(c, "NafsGuard আপডেট", "নতুন ভার্সন$label এসেছে। অ্যাপ খুলে আপডেট করুন।")
                }
                Update.onFound?.run()
            }
            true
        } catch (e: Throwable) { false }
    }
}

class UpdateWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result =
        if (UpdateCheck.run(applicationContext)) Result.success() else Result.retry()
}
