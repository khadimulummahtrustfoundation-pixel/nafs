package com.NafsGuard.guard

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.util.concurrent.TimeUnit

/**
 * ভার্সন চেক: Firebase Remote Config-এর "latest_version" (এবং ঐচ্ছিক "update_url") দিনে একবার ব্যাকগ্রাউন্ডে দেখে।
 * অ্যাপের ভার্সনের সাথে না মিললে আপডেট পপ-আপ দেখায় (অ্যাপ খোলা থাকলে সাথে সাথে, নইলে পরের বার খোলার সময়) এবং নোটিফিকেশন দেয়।
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

    /** true = চেক সম্পন্ন (বা Firebase সেট নেই), false = নেটওয়ার্ক ইত্যাদির কারণে পরে আবার চেষ্টা */
    fun run(c: Context): Boolean {
        val rc = try { FirebaseRemoteConfig.getInstance() } catch (e: Throwable) { return true }
        return try {
            rc.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(3600).build())
            Tasks.await(rc.fetchAndActivate(), 30, TimeUnit.SECONDS)
            val ver = rc.getString("latest_version").trim()
            val url = rc.getString("update_url").trim()
            if (ver.isEmpty()) return true
            Prefs.setUpdate(c, ver, url)
            if (Update.needed(c)) {
                if (Prefs.updNotified(c) != ver) {
                    Prefs.setUpdNotified(c, ver)
                    Push.notify(c, "NafsGuard আপডেট", "নতুন ভার্সন $ver এসেছে। অ্যাপ খুলে আপডেট করুন।")
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
