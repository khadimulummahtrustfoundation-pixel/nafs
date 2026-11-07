package com.NafsGuard.guard

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.util.concurrent.TimeUnit

/**
 * ভার্সন চেক (Firebase Remote Config; Cloudflare/D1 লাগে না)।
 *
 * Remote Config প্যারামিটার: latest_version_code, latest_version_name, update_url
 *
 * নিয়ম:
 *  1. রেজিস্ট্রেশন সফল হলে সাথে সাথে প্রথম Fetch (নেটওয়ার্ক পেলেই) — [ensure]
 *  2. প্রথম সফল চেকের পর WorkManager-এ প্রতি ২৪ ঘণ্টার Periodic কাজ (unique নাম + KEEP = ডুপ্লিকেট হয় না)
 *  3. Minimum Fetch Interval ৬ ঘণ্টা; এর মধ্যে আবার ডাকলেও নেটওয়ার্কে যায় না, ক্যাশ ব্যবহার হয়
 *     অ্যাপ খুললেও ([checkOnOpen]) এই নিয়মেই চেক হয়, তাই নতুন ভার্সন ২৪ ঘণ্টা অপেক্ষা না করে দ্রুত ধরা পড়ে
 *  4. ইন্টারনেট না থাকলে ক্যাশ করা মান দিয়ে মিলিয়ে দেখে, Fetch পরে আবার চেষ্টা (WorkManager retry)
 *  5. রেজিস্ট্রেশন না থাকলে কিছুই Schedule হয় না
 *
 * অ্যাপ বন্ধ থাকলেও WorkManager চালানোর চেষ্টা করে; তবে Battery Optimization, Doze Mode ও
 * Background Restriction-এর কারণে সময় কিছুটা পিছিয়ে যেতে পারে।
 */
object UpdateCheck {
    private const val WORK_FIRST = "nafs_update_first"      // প্রথম চেক (এক-বারের কাজ)
    private const val WORK = "nafs_update_check"            // ২৪ ঘণ্টার Periodic (আগের নামই, তাই পুরোনো ইউজারের কাজও ডুপ্লিকেট হয় না)

    private const val K_CODE = "latest_version_code"
    private const val K_NAME = "latest_version_name"
    private const val K_URL = "update_url"
    private const val FETCH_INTERVAL_SEC = 6L * 60 * 60   // অ্যাপ খুললে ৬ ঘণ্টার পুরোনো হলে তবেই নতুন Fetch (এর মধ্যে ক্যাশ)

    private val NET = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    @Volatile private var configured = false

    /**
     * রেজিস্টার্ড ইউজারের জন্য ভার্সন চেকের ব্যবস্থা নিশ্চিত করে (বারবার ডাকলেও ক্ষতি নেই):
     *  - প্রথম সফল চেক বাকি থাকলে → এক-বারের প্রথম চেক enqueue
     *  - হয়ে গেলে → ২৪ ঘণ্টার Periodic কাজ আছে কিনা নিশ্চিত করে
     */
    fun ensure(c: Context) {
        try {
            if (!Prefs.registered(c)) return
            if (Prefs.verFirstDone(c)) {
                schedulePeriodic(c)
            } else {
                val req = OneTimeWorkRequestBuilder<FirstUpdateWorker>()
                    .setConstraints(NET)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(c.applicationContext)
                    .enqueueUniqueWork(WORK_FIRST, ExistingWorkPolicy.KEEP, req)
            }
        } catch (e: Throwable) {}
    }

    @Volatile private var lastOpenTry = 0L

    /** অ্যাপ খুললে ব্যাকগ্রাউন্ডে ভার্সন চেক (৩০ মিনিটে একবারের বেশি নয়; Remote Config নিজেও ৬ ঘণ্টার মধ্যে নেটওয়ার্কে যায় না) */
    fun checkOnOpen(c: Context) {
        try {
            if (!Prefs.registered(c) || !Prefs.verFirstDone(c)) return
            val t = System.currentTimeMillis()
            if (t - lastOpenTry < 30L * 60 * 1000) return
            lastOpenTry = t
            val app = c.applicationContext
            Thread { try { run(app) } catch (e: Throwable) {} }.start()
        } catch (e: Throwable) {}
    }

    /** পরীক্ষার জন্য: ৬ ঘণ্টা অপেক্ষা না করে এখনই সার্ভার থেকে আনে। ফলাফলের লেখা callback-এ আসে (ব্যাকগ্রাউন্ড থ্রেড) */
    fun forceNow(c: Context, cb: (String) -> Unit) {
        val app = c.applicationContext
        Thread {
            val msg = try {
                val rc = remoteConfig()
                Tasks.await(rc.fetch(0), 40, TimeUnit.SECONDS)
                Tasks.await(rc.activate(), 20, TimeUnit.SECONDS)
                evaluate(app, rc)
                val sc = rc.getString(K_CODE).trim()
                "সার্ভারের কোড: ${if (sc.isEmpty()) "-" else sc}, অ্যাপের কোড: ${Update.currentCode(app)}\n" +
                    if (Update.needed(app)) "নতুন ভার্সন আছে, পপ-আপ আসবে।" else "নতুন ভার্সন নেই (সার্ভারের কোড অ্যাপের কোডের চেয়ে বড় হলে পপ-আপ আসে)।"
            } catch (e: Throwable) {
                "চেক ব্যর্থ: ইন্টারনেট বা Firebase সমস্যা (${e.javaClass.simpleName})"
            }
            cb(msg)
        }.start()
    }

    /** প্রথম সফল চেকের পর ডাকা হয়; প্রথম রান ২৪ ঘণ্টা পরে (এখনই আবার চেক করার দরকার নেই) */
    internal fun schedulePeriodic(c: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(24, TimeUnit.HOURS)
                .setConstraints(NET)
                .build()
            WorkManager.getInstance(c.applicationContext)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } catch (e: Throwable) {}
    }

    private fun remoteConfig(): FirebaseRemoteConfig {
        val rc = FirebaseRemoteConfig.getInstance()
        if (!configured) {
            Tasks.await(rc.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_SEC).build()),
                20, TimeUnit.SECONDS)
            Tasks.await(rc.setDefaultsAsync(mapOf(K_CODE to "0", K_NAME to "", K_URL to Update.SITE)),
                20, TimeUnit.SECONDS)
            configured = true
        }
        return rc
    }

    /**
     * Fetch করে (২৪ ঘণ্টার মধ্যে হলে ক্যাশ থেকেই) মান মিলিয়ে দেখে।
     * true = Fetch সফল (ক্যাশ-ফ্রেশও সফল ধরা হয়), false = নেটওয়ার্ক/Firebase সমস্যা — পরে আবার চেষ্টা।
     * Fetch ব্যর্থ হলেও আগের ক্যাশ করা মান দিয়ে মিলিয়ে দেখা হয়।
     */
    fun run(c: Context): Boolean {
        return try {
            val rc = remoteConfig()
            val fetched = try {
                Tasks.await(rc.fetchAndActivate(), 40, TimeUnit.SECONDS); true
            } catch (e: Throwable) { false }
            evaluate(c, rc)
            fetched
        } catch (e: Throwable) { false }
    }

    private fun evaluate(c: Context, rc: FirebaseRemoteConfig) {
        val code = rc.getString(K_CODE).trim().toIntOrNull() ?: 0
        if (code <= 0) return
        val name = rc.getString(K_NAME).trim()
        val url = rc.getString(K_URL).trim().let {
            if (it.startsWith("https://") || it.startsWith("http://")) it else Update.SITE
        }
        Prefs.setUpdate(c, code, name, url)
        if (Update.needed(c)) {
            if (Prefs.updNotified(c) != code) {
                Prefs.setUpdNotified(c, code)
                val label = if (name.isNotEmpty()) " $name" else ""
                Push.notify(c, "NafsGuard আপডেট", "নতুন ভার্সন$label এসেছে। অ্যাপ খুলে আপডেট করুন।")
            }
            Update.onFound?.run()
        }
    }
}

/** রেজিস্ট্রেশনের পর প্রথম ভার্সন চেক; সফল হলে ২৪ ঘণ্টার Periodic কাজ চালু করে */
class FirstUpdateWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        val c = applicationContext
        if (!Prefs.registered(c)) return Result.success()
        return if (UpdateCheck.run(c)) {
            Prefs.setVerFirstDone(c)
            UpdateCheck.schedulePeriodic(c)
            Result.success()
        } else Result.retry()
    }
}

/** প্রতি ২৪ ঘণ্টার ভার্সন চেক */
class UpdateWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        val c = applicationContext
        if (!Prefs.registered(c)) return Result.success()
        return if (UpdateCheck.run(c)) Result.success() else Result.retry()
    }
}
