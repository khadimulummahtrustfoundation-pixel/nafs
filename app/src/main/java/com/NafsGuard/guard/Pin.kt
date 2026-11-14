package com.NafsGuard.guard

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.util.Base64
import android.widget.LinearLayout
import android.widget.Toast
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** ঐচ্ছিক অফলাইন পাসওয়ার্ড: অ্যাপে ঢোকার সময় প্রতিবার লাগে। শুধু ফোনে hash করে রাখা হয়, ইন্টারনেট লাগে না। */
object Pin {
    private const val ITER = 20000

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITER, 256)).encoded

    fun has(c: android.content.Context) = Prefs.pinHash(c).isNotEmpty()

    fun set(c: android.content.Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        Prefs.setPin(c, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP), Base64.encodeToString(salt, Base64.NO_WRAP))
    }

    fun check(c: android.content.Context, pin: String): Boolean {
        if (!has(c)) return true
        return try {
            val salt = Base64.decode(Prefs.pinSalt(c), Base64.NO_WRAP)
            val want = Base64.decode(Prefs.pinHash(c), Base64.NO_WRAP)
            MessageDigest.isEqual(want, hash(pin, salt))
        } catch (e: Exception) { false }
    }

    fun clear(c: android.content.Context) = Prefs.clearPin(c)

    private fun ask(act: Activity, title: String, hint: String, onOk: (String) -> Unit) {
        val et = Ui.dedit(act, hint).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(act, 20); setPadding(p, p, p, 0)
            addView(et)
        }
        AlertDialog.Builder(act).setTitle(title).setView(box)
            .setPositiveButton("ঠিক আছে") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("বাতিল", null).show()
    }

    private fun toast(a: Activity, s: String) = Toast.makeText(a, s, Toast.LENGTH_LONG).show()

    /** মূল পেজে যোগ করার কার্ড */
    fun card(act: Activity, refresh: () -> Unit): LinearLayout {
        val c = act
        val k = Ui.dcard(c)
        k.addView(Ui.dtext(c, "অফলাইন পাসওয়ার্ড (ঐচ্ছিক)", 17f, true))
        if (!has(c)) {
            k.addView(Ui.dtext(c, "সেট করলে অ্যাপে ঢুকতে প্রতিবার এই পাসওয়ার্ড লাগবে। ইন্টারনেট ছাড়াই কাজ করে।", 12.5f, false, Ui.TXT2))
            k.addView(Ui.dbutton(c, "পাসওয়ার্ড সেট করুন") {
                ask(act, "নতুন পাসওয়ার্ড", "কমপক্ষে ৪ অক্ষর") { a ->
                    if (a.length < 4) { toast(act, "কমপক্ষে ৪ অক্ষর দিন"); return@ask }
                    ask(act, "আবার লিখুন", "পাসওয়ার্ড নিশ্চিত করুন") { b ->
                        if (a != b) toast(act, "দুটি মিলছে না")
                        else { set(c, a); toast(act, "পাসওয়ার্ড সেট হয়েছে"); refresh() }
                    }
                }
            })
        } else {
            k.addView(Ui.dtext(c, "✅ চালু আছে। ভুলে গেলে অ্যাডমিনকে জানান।", 14f, false, Ui.NEON))
            k.addView(Ui.dbutton(c, "পাসওয়ার্ড বদলান", true) {
                ask(act, "বর্তমান পাসওয়ার্ড", "বর্তমান পাসওয়ার্ড") { cur ->
                    if (!check(c, cur)) { toast(act, "বর্তমান পাসওয়ার্ড ভুল"); return@ask }
                    ask(act, "নতুন পাসওয়ার্ড", "কমপক্ষে ৪ অক্ষর") { a ->
                        if (a.length < 4) toast(act, "কমপক্ষে ৪ অক্ষর দিন")
                        else { set(c, a); toast(act, "পাসওয়ার্ড বদলেছে"); refresh() }
                    }
                }
            })
            k.addView(Ui.dbutton(c, "পাসওয়ার্ড বন্ধ করুন", true) {
                ask(act, "বর্তমান পাসওয়ার্ড", "বর্তমান পাসওয়ার্ড") { cur ->
                    if (!check(c, cur)) toast(act, "পাসওয়ার্ড ভুল")
                    else { clear(c); toast(act, "পাসওয়ার্ড বন্ধ হয়েছে"); refresh() }
                }
            })
        }
        return k
    }
}
