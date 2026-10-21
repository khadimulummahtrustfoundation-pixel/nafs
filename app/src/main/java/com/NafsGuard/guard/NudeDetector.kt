package com.NafsGuard.guard

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.nio.FloatBuffer

/**
 * NudeNet (320n, YOLOv8) দিয়ে ফোনের ভেতরেই ছবি যাচাই। ছবি কোথাও পাঠানো হয় না।
 * শুধু নিচের ৫টি ক্লাস ব্লক হয় (খোলা স্তন, নারী-যৌনাঙ্গ, নিতম্ব, পায়ুপথ, পুরুষ-যৌনাঙ্গ)।
 * মুখ, পেট, পা, বগল, কাপড় পরা অংশ ইত্যাদি কিছুই ব্লক হয় না।
 */
object NudeDetector {
    private const val SIZE = 320
    private const val MODEL = "models/320n.onnx"

    // লেবেলের ক্রম NudeNet-এর all_labels অনুযায়ী
    // 2 BUTTOCKS_EXPOSED, 3 FEMALE_BREAST_EXPOSED, 4 FEMALE_GENITALIA_EXPOSED,
    // 6 ANUS_EXPOSED, 14 MALE_GENITALIA_EXPOSED
    private val BLOCK = setOf(2, 3, 4, 6, 14)

    // কড়াকড়ি এখানে বদলানো যায়: সংখ্যা বাড়ালে কম ব্লক (ভুল কম), কমালে বেশি ব্লক
    const val STRONG = 0.80f   // এর বেশি হলে সাথে সাথে ব্লক
    const val MEDIUM = 0.60f   // এর বেশি হলে ৮ সেকেন্ডে ২ বার ধরলে ব্লক

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    @Volatile private var failed = false
    private var info = ""
    fun hasFailed() = failed

    fun modelBundled(c: Context): Boolean = try {
        c.applicationContext.assets.list("models")?.contains("320n.onnx") == true
    } catch (e: Exception) { false }

    @Synchronized
    fun ready(c: Context): Boolean {
        if (session != null) return true
        if (failed) return false
        return try {
            val bytes = c.applicationContext.assets.open(MODEL).use { it.readBytes() }
            info = "size=" + bytes.size + " first=" + bytes.take(4).joinToString(" ") { String.format("%02X", it) }
            val e = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
            session = e.createSession(bytes, opts)
            env = e
            Diag.model = "লোড হয়েছে"
            true
        } catch (t: Throwable) {
            failed = true
            Diag.model = "ব্যর্থ: " + t.javaClass.simpleName + " [" + info + "]"
            false
        }
    }

    /** ব্লক-যোগ্য ক্লাসের সর্বোচ্চ স্কোর (0 হলে কিছু পাওয়া যায়নি) */
    fun scan(src: Bitmap): Float {
        val s = session ?: return 0f
        val e = env ?: return 0f
        return try {
            val w = src.width; val h = src.height
            val scale = SIZE.toFloat() / maxOf(w, h)
            val nw = maxOf(1, (w * scale).toInt()); val nh = maxOf(1, (h * scale).toInt())
            val scaled = Bitmap.createScaledBitmap(src, nw, nh, true)
            // NudeNet-এর মতো: নিচে/ডানে কালো প্যাড দিয়ে বর্গ বানানো
            val sq = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            Canvas(sq).apply { drawColor(Color.BLACK); drawBitmap(scaled, 0f, 0f, null) }
            if (scaled !== src) scaled.recycle()

            val px = IntArray(SIZE * SIZE)
            sq.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            sq.recycle()
            val plane = SIZE * SIZE
            val data = FloatArray(3 * plane)
            for (i in 0 until plane) {
                val p = px[i]
                data[i] = ((p shr 16) and 0xFF) / 255f
                data[plane + i] = ((p shr 8) and 0xFF) / 255f
                data[2 * plane + i] = (p and 0xFF) / 255f
            }

            val input = OnnxTensor.createTensor(e, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()))
            input.use {
                val res = s.run(mapOf(s.inputNames.first() to input))
                res.use {
                    @Suppress("UNCHECKED_CAST")
                    val out = (res[0].value as Array<Array<FloatArray>>)[0]
                    best(out)
                }
            }
        } catch (t: Throwable) {
            Diag.model = "স্ক্যান ত্রুটি: " + t.javaClass.simpleName + " " + (t.message ?: "").take(80)
            0f
        }
    }

    // আউটপুট: [22][N] (৪টি বক্স + ১৮ ক্লাস) অথবা উল্টো [N][22]
    private fun best(out: Array<FloatArray>): Float {
        val transposed = out.size > out[0].size          // [N][22]
        val n = if (transposed) out.size else out[0].size
        var top = 0f
        for (i in 0 until n) {
            var bc = -1; var bs = 0f
            for (c in 0 until 18) {
                val sc = if (transposed) out[i][4 + c] else out[4 + c][i]
                if (sc > bs) { bs = sc; bc = c }
            }
            if (bc in BLOCK && bs > top) {
                val bw = if (transposed) out[i][2] else out[2][i]
                val bh = if (transposed) out[i][3] else out[3][i]
                if (bw >= 6f && bh >= 6f) top = bs   // খুব ছোট বক্স বাদ
            }
        }
        return top
    }
}
