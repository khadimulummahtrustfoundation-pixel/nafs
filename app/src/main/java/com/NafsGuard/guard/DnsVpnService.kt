package com.NafsGuard.guard

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * সেইফ সার্চ + ফেসবুক ও ইনস্টাগ্রাম রিলস ব্লক: 
 * লোকাল VPN দিয়ে DNS রিকোয়েস্ট ধরে CleanBrowsing-এ পাঠানোর পাশাপাশি 
 * FB & Insta Reels ডোমেইনগুলো ফিল্টার করে ব্লক করে দেয়।
 */
class DnsVpnService : VpnService() {

    companion object {
        @Volatile var active = false
        private val VIRTUAL_DNS = byteArrayOf(10, 111, 222.toByte(), 2)
        private val UPSTREAM = listOf(
            byteArrayOf(185.toByte(), 228.toByte(), 168.toByte(), 168.toByte()),
            byteArrayOf(185.toByte(), 228.toByte(), 169.toByte(), 168.toByte())
        )

        // রিলস এবং ভিডিও ডোমেইন ফিল্টার করার ব্লকলিস্ট কিওয়ার্ড
        private val BLOCKED_DOMAINS = listOf(
            "fbcdn.net",
            "instagram.com",
            "cdninstagram.com",
            "fbsbx.com"
        )

        fun start(c: Context) {
            c.startService(Intent(c, DnsVpnService::class.java))
        }

        fun stop(c: Context) {
            c.startService(Intent(c, DnsVpnService::class.java).setAction("STOP"))
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            shutdown(); stopSelf(); return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return
        try {
            val pfd = Builder()
                .setSession("NafsGuard Safe Search & Reels Guard")
                .addAddress("10.111.222.1", 32)
                .addDnsServer("10.111.222.2")
                .addRoute("10.111.222.2", 32)
                .setBlocking(true)
                .establish() ?: return
            tun = pfd
            running = true
            active = true
            Thread { loop(pfd) }.start()
        } catch (e: Exception) {
            running = false; active = false
        }
    }

    private fun shutdown() {
        running = false
        active = false
        try { tun?.close() } catch (e: Exception) {}
        tun = null
    }

    override fun onDestroy() { shutdown(); super.onDestroy() }
    override fun onRevoke() { shutdown(); super.onRevoke() }

    private fun u16(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun loop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        var errs = 0
        while (running) {
            try {
                val n = input.read(buf)
                if (n < 0) break
                errs = 0
                if (n < 28) continue
                if (((buf[0].toInt() shr 4) and 0x0F) != 4) continue
                val ihl = (buf[0].toInt() and 0x0F) * 4
                if (buf[9].toInt() != 17 || n < ihl + 8) continue
                var match = true
                for (i in 0..3) if (buf[16 + i] != VIRTUAL_DNS[i]) match = false
                if (!match) continue
                if (u16(buf, ihl + 2) != 53) continue
                val srcPort = u16(buf, ihl)
                val udpLen = u16(buf, ihl + 4)
                val end = minOf(n, ihl + udpLen)
                if (end <= ihl + 8) continue
                val query = buf.copyOfRange(ihl + 8, end)
                val clientIp = buf.copyOfRange(12, 16)
                
                // ডোমেইন পার্স করে চেক করা রিলস বা ভিডিও কিনা
                if (isDomainBlocked(query)) {
                    // যদি ব্লকড লিস্টের ডোমেইন হয়, তবে ফেক ব্ল্যাকহোল রেসপন্স (0.0.0.0) পাঠিয়ে দেবো
                    pool.execute { sendBlockedResponse(query, clientIp, srcPort, output) }
                } else {
                    pool.execute { resolve(query, clientIp, srcPort, output) }
                }
            } catch (e: Exception) {
                if (!running) break
                errs++
                if (errs > 20) break
                try { Thread.sleep(50) } catch (x: Exception) {}
            }
        }
        if (running) { shutdown(); try { stopSelf() } catch (e: Exception) {} }
    }

    // DNS রিকোয়েস্ট বাইট থেকে ডোমেইন নাম বের করে চেক করা
    private fun isDomainBlocked(query: ByteArray): Boolean {
        try {
            var offset = 12 // DNS Header length
            val sb = StringBuilder()
            while (offset < query.size) {
                val len = query[offset].toInt()
                if (len == 0) break
                if (offset + 1 + len > query.size) break
                if (sb.isNotEmpty()) sb.append(".")
                sb.append(String(query, offset + 1, len))
                offset += 1 + len
            }
            val domain = sb.toString().lowercase()
            for (blocked in BLOCKED_DOMAINS) {
                if (domain.contains(blocked)) {
                    // রিলস বা ভিডিও রিলেটেড সাবডোমেইন কিনা যাচাই (যেমন video, reels, scontent)
                    if (domain.contains("video") || domain.contains("reel") || domain.contains("scontent") || domain.contains("media")) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {}
        return false
    }

    // ব্লকড ডোমেইনের জন্য জিরো আইপি (0.0.0.0) রেসপন্স তৈরি করা
    private fun sendBlockedResponse(query: ByteArray, clientIp: ByteArray, clientPort: Int, out: FileOutputStream) {
        try {
            val qLen = query.size
            val resp = ByteArray(qLen + 16)
            System.arraycopy(query, 0, resp, 0, qLen)
            // DNS Response Flags (NoError, Answer section included)
            resp[2] = 0x81.toByte()
            resp[3] = 0x80.toByte()
            resp[7] = 0x01.toByte() // ANCOUNT = 1
            
            // Answer record (Pointer to domain name, Type A, Class IN, TTL, RDLENGTH, IP 0.0.0.0)
            var idx = qLen
            resp[idx++] = 0xC0.toByte(); resp[idx++] = 0x0C.toByte()
            resp[idx++] = 0x00; resp[idx++] = 0x01
            resp[idx++] = 0x00; resp[idx++] = 0x01
            resp[idx++] = 0x00; resp[idx++] = 0x00; resp[idx++] = 0x00; resp[idx++] = 0x3C
            resp[idx++] = 0x00; resp[idx++] = 0x04
            resp[idx++] = 0x00; resp[idx++] = 0x00; resp[idx++] = 0x00; resp[idx++] = 0x00 // IP: 0.0.0.0

            writeResponse(resp, idx, clientIp, clientPort, out)
        } catch (e: Exception) {}
    }

    private fun resolve(query: ByteArray, clientIp: ByteArray, clientPort: Int, out: FileOutputStream) {
        for (up in UPSTREAM) {
            try {
                val s = DatagramSocket()
                try {
                    protect(s)
                    s.soTimeout = 3000
                    s.send(DatagramPacket(query, query.size, InetAddress.getByAddress(up), 53))
                    val rb = ByteArray(4096)
                    val rp = DatagramPacket(rb, rb.size)
                    s.receive(rp)
                    writeResponse(rb, rp.length, clientIp, clientPort, out)
                    return
                } finally {
                    s.close()
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun writeResponse(data: ByteArray, len: Int, clientIp: ByteArray, clientPort: Int, out: FileOutputStream) {
        val total = 20 + 8 + len
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[6] = 0x40.toByte()
        p[8] = 64; p[9] = 17
        System.arraycopy(VIRTUAL_DNS, 0, p, 12, 4)
        System.arraycopy(clientIp, 0, p, 16, 4)
        var sum = 0
        var i = 0
        while (i < 20) {
            sum += ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)
            i += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv() and 0xFFFF
        p[10] = (ck shr 8).toByte(); p[11] = ck.toByte()
        p[20] = 0; p[21] = 53
        p[22] = (clientPort shr 8).toByte(); p[23] = clientPort.toByte()
        val ul = 8 + len
        p[24] = (ul shr 8).toByte(); p[25] = ul.toByte()
        System.arraycopy(data, 0, p, 28, len)
        synchronized(out) { out.write(p) }
    }
}
