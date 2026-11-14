package com.NafsGuard.guard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

/**
 * স্ক্রিনে মেয়ের মুখ থাকলে শুধু ওই অংশ ব্লার করে।
 * ধাপ: স্ক্রিনশট -> মুখ খোঁজা (ML Kit) -> মেয়ে কিনা (TFLite, assets/gender.tflite) -> ওভারলেতে ব্লার।
 * gender.tflite না থাকলে নিরাপদ দিকে যায়: সব মুখই ব্লার হয়।
 */
class FaceBlur(private val svc: AccessibilityService) {

    companion object {
        const val INTERVAL_MS = 350L        // takeScreenshot-এর সীমা প্রায় ৩৩৩ms, এর কম দেওয়া যায় না
        const val DETECT_WIDTH = 360        // শনাক্তকরণের জন্য ছবি এই চওড়ায় ছোট করা হয় (গতির জন্য)
        const val FEMALE_THRESHOLD = 0.35f  // কম রাখা মানে সন্দেহ হলে ব্লার (ভুল ছাড় কমে)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: MosaicView? = null
    private var running = false
    private var busy = false
    private var emptyTicks = 0
    private var lastTickAt = 0L

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.04f)
                .build()
        )
    }
    private val gender by lazy { GenderModel(svc) }

    private val loop = object : Runnable {
        override fun run() {
            try { tick() } catch (e: Throwable) { busy = false }
            if (running) handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.postDelayed(loop, 1500)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(loop)
        removeView()
    }

    /** স্ক্রল হলে সঙ্গে সঙ্গে আবার স্ক্যান (সীমার ভেতরে) */
    fun kick() {
        if (!running || busy) return
        if (System.currentTimeMillis() - lastTickAt < INTERVAL_MS) return
        handler.removeCallbacks(loop)
        loop.run()
    }

    // ---------------------------------------------------------------- মূল চক্র
    private fun tick() {
        if (Build.VERSION.SDK_INT < 30 || busy) return
        if (!Prefs.faceBlur(svc) || !Prefs.loggedIn(svc)) { clear(); return }
        val pm = svc.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) { clear(); return }
        val root = svc.rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (pkg == null || pkg == svc.packageName) { clear(); return }

        busy = true
        lastTickAt = System.currentTimeMillis()
        val cb = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                var offX = 0
                var offY = 0
                if (Build.VERSION.SDK_INT >= 34) {
                    val b = Rect()
                    try { root.window?.getBoundsInScreen(b) } catch (e: Throwable) {}
                    offX = b.left; offY = b.top
                }
                view?.alpha = 1f
                val bmp = try {
                    val hb = r.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                    val c = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    c
                } catch (e: Throwable) { null }
                if (bmp == null) { busy = false; return }
                process(bmp, offX, offY)
            }
            override fun onFailure(code: Int) { view?.alpha = 1f; busy = false }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            // শুধু অ্যাপের উইন্ডো ধরা হয়: আমাদের ব্লার ওভারলে স্ক্রিনশটে আসে না, তাই ঝিলমিল হয় না
            val wid = try { root.window?.id ?: -1 } catch (e: Throwable) { -1 }
            if (wid >= 0) svc.takeScreenshotOfWindow(wid, svc.mainExecutor, cb)
            else svc.takeScreenshot(Display.DEFAULT_DISPLAY, svc.mainExecutor, cb)
        } else {
            // Android 11–13: পুরো স্ক্রিন ধরতে হয়, তাই এক মুহূর্ত ওভারলে লুকিয়ে ছবি নেওয়া হয় (হালকা ঝিলিক হতে পারে)
            val v = view
            if (v != null && v.hasItems()) {
                v.alpha = 0f
                handler.postDelayed({ svc.takeScreenshot(Display.DEFAULT_DISPLAY, svc.mainExecutor, cb) }, 45)
            } else svc.takeScreenshot(Display.DEFAULT_DISPLAY, svc.mainExecutor, cb)
        }
    }

    private fun process(bmp: Bitmap, offX: Int, offY: Int) {
        val scale = minOf(1f, DETECT_WIDTH.toFloat() / bmp.width)
        val small = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1),
            (bmp.height * scale).toInt().coerceAtLeast(1), true)
        detector.process(InputImage.fromBitmap(small, 0))
            .addOnSuccessListener { faces ->
                worker.execute {
                    val items = ArrayList<MosaicView.Item>()
                    try {
                        for (f in faces) {
                            val fb = f.boundingBox
                            // মূল রেজল্যুশনে মুখের বক্স
                            val l = (fb.left / scale).toInt().coerceIn(0, bmp.width - 1)
                            val t = (fb.top / scale).toInt().coerceIn(0, bmp.height - 1)
                            val r = (fb.right / scale).toInt().coerceIn(l + 1, bmp.width)
                            val b = (fb.bottom / scale).toInt().coerceIn(t + 1, bmp.height)
                            val fw = r - l
                            val fh = b - t
                            val tooSmall = fw < 24 || fh < 24
                            val female = if (tooSmall || !gender.available) true else {
                                val face = Bitmap.createBitmap(bmp, l, t, fw, fh)
                                val p = gender.femaleProb(face)
                                face.recycle()
                                p >= FEMALE_THRESHOLD
                            }
                            if (!female) continue
                            // মাথা-চুল-গলা-কাঁধ ঢাকতে বক্স বড় করা
                            val rl = (l - 0.45f * fw).toInt().coerceAtLeast(0)
                            val rt = (t - 0.60f * fh).toInt().coerceAtLeast(0)
                            val rr = (r + 0.45f * fw).toInt().coerceAtMost(bmp.width)
                            val rb = (b + 1.00f * fh).toInt().coerceAtMost(bmp.height)
                            if (rr - rl < 2 || rb - rt < 2) continue
                            val crop = Bitmap.createBitmap(bmp, rl, rt, rr - rl, rb - rt)
                            val tiny = Bitmap.createScaledBitmap(crop,
                                maxOf(2, crop.width / 14), maxOf(2, crop.height / 14), true)
                            crop.recycle()
                            items.add(MosaicView.Item(
                                RectF((rl + offX).toFloat(), (rt + offY).toFloat(),
                                    (rr + offX).toFloat(), (rb + offY).toFloat()), tiny))
                        }
                    } catch (e: Throwable) {
                    } finally {
                        try { bmp.recycle(); small.recycle() } catch (e: Throwable) {}
                    }
                    handler.post {
                        busy = false
                        if (items.isEmpty()) {
                            emptyTicks++
                            if (emptyTicks >= 2) clear()
                        } else {
                            emptyTicks = 0
                            ensureView().setItems(items)
                        }
                    }
                }
            }
            .addOnFailureListener {
                busy = false
                try { bmp.recycle(); small.recycle() } catch (e: Throwable) {}
            }
    }

    // ---------------------------------------------------------------- ওভারলে
    private fun ensureView(): MosaicView {
        view?.let { return it }
        val v = MosaicView(svc)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try { wm.addView(v, lp); view = v } catch (e: Throwable) {}
        return v
    }

    private fun clear() { emptyTicks = 0; view?.setItems(emptyList()) }

    private fun removeView() {
        val v = view ?: return
        view = null
        try { wm.removeView(v) } catch (e: Throwable) {}
    }

    // ---------------------------------------------------------------- ব্লার আঁকার ভিউ
    class MosaicView(c: Context) : View(c) {
        class Item(val rect: RectF, val bmp: Bitmap)
        private var items: List<Item> = emptyList()
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val loc = IntArray(2)

        fun hasItems() = items.isNotEmpty()
        fun setItems(n: List<Item>) {
            val old = items
            items = n
            invalidate()
            for (o in old) try { o.bmp.recycle() } catch (e: Throwable) {}
        }

        override fun onDraw(canvas: Canvas) {
            if (items.isEmpty()) return
            getLocationOnScreen(loc)
            canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
            for (it in items) canvas.drawBitmap(it.bmp, null, it.rect, paint)
        }
    }

    // ---------------------------------------------------------------- মেয়ে চেনার মডেল
    /**
     * app/src/main/assets/gender.tflite (float32 ইনপুট [1,H,W,3], মান 0..1)।
     * আউটপুট: [1,2] হলে নিচের FEMALE_INDEX-এর ঘরটা "মেয়ে"; [1,1] হলে সিগময়েড = মেয়ের সম্ভাবনা।
     * আপনার মডেলের লেবেল ক্রম আলাদা হলে FEMALE_INDEX বা NORM বদলান।
     */
    class GenderModel(ctx: Context) {
        private val FEMALE_INDEX = 0   // Seeed মডেলে ক্লাসের ক্রম: female, male
        private val NORM = 255f
        private var interp: Interpreter? = null
        private var w = 64
        private var h = 64
        private var outN = 2
        val available get() = interp != null

        init {
            try {
                val fd = ctx.assets.openFd("gender.tflite")
                val map = FileInputStream(fd.fileDescriptor).channel
                    .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                val it = Interpreter(map, Interpreter.Options().setNumThreads(2))
                val shp = it.getInputTensor(0).shape()
                h = shp[1]; w = shp[2]
                outN = it.getOutputTensor(0).shape().last()
                interp = it
            } catch (e: Throwable) { interp = null }
        }

        @Synchronized
        fun femaleProb(face: Bitmap): Float {
            val it = interp ?: return 1f
            return try {
                val s = Bitmap.createScaledBitmap(face, w, h, true)
                val px = IntArray(w * h)
                s.getPixels(px, 0, w, 0, 0, w, h)
                if (s !== face) s.recycle()
                val buf = ByteBuffer.allocateDirect(w * h * 3 * 4).order(ByteOrder.nativeOrder())
                for (p in px) {
                    buf.putFloat(((p shr 16) and 0xFF) / NORM)
                    buf.putFloat(((p shr 8) and 0xFF) / NORM)
                    buf.putFloat((p and 0xFF) / NORM)
                }
                buf.rewind()
                val out = Array(1) { FloatArray(outN) }
                it.run(buf, out)
                val o = out[0]
                if (outN >= 2) {
                    // আউটপুট সম্ভাবনা না হলে (লজিট) softmax করে নেওয়া হয়
                    val sum = o.sum()
                    val isProb = o.all { v -> v in 0f..1f } && sum > 0.95f && sum < 1.05f
                    val p = if (isProb) o else {
                        val m = o.max()
                        val e = FloatArray(outN) { i -> Math.exp((o[i] - m).toDouble()).toFloat() }
                        FloatArray(outN) { i -> e[i] / e.sum() }
                    }
                    p[FEMALE_INDEX.coerceAtMost(outN - 1)]
                } else o[0]
            } catch (e: Throwable) { 1f }
        }
    }
}
