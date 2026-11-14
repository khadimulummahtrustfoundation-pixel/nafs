package com.NafsGuard.guard

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * সেইফ সার্চ: লোকাল VPN দিয়ে DNS রিকোয়েস্ট ধরে ফ্যামিলি-ফিল্টার DNS-এ পাঠায়।
 * ছবি/ভিডিওর CDN ডোমেইন (fbcdn, scontent, video…) ইচ্ছা করেই ব্লক করা হয় না,
 * কারণ DNS দিয়ে রিল আর সাধারণ ভিডিও আলাদা করা যায় না। রিল ব্লক করে GuardService।
 *
 * স্থিতিশীলতার জন্য (WiFi ও মোবাইল ডাটা দুটোতেই):
 *  - একটা DNS সার্ভার না পেলে সঙ্গে সঙ্গে পরেরটায় যায় (CleanBrowsing → Cloudflare Family → OpenDNS FamilyShield),
 *    IPv4 ও IPv6 দুটোই। ফিল্টার বন্ধ করে না।
 *  - সব সার্ভার ফেল করলে চুপ করে বসে না, সঙ্গে সঙ্গে SERVFAIL ফেরত দেয় (নেট "ঝুলে" থাকে না)।
 *  - TCP/DoT (Private DNS) প্রশ্নে সঙ্গে সঙ্গে RST দেয়, তাই টাইমআউটের জন্য অপেক্ষা করতে হয় না।
 *  - ভিপিএনকে "metered নয়" চিহ্নিত করা হয়, তাই WiFi-কে মোবাইল ডাটা ভেবে অ্যাপ ধীর/বন্ধ হয় না।
 */
class DnsVpnService : VpnService() {

    companion object {
        @Volatile var active = false
        private val VIRTUAL_DNS = byteArrayOf(10, 111, 222.toByte(), 2)

        // সেইফ সার্চ বন্ধ থাকলে (শুধু লিংক ব্লক চালু) সাধারণ DNS ব্যবহার হবে, ফ্যামিলি ফিল্টার নয়
        private val PLAIN = listOf(
            "1.1.1.1", "8.8.8.8", "9.9.9.9",
            "2606:4700:4700::1111", "2001:4860:4860::8888"
        )

        // ফ্যামিলি/অ্যাডাল্ট ফিল্টার সহ DNS। প্রথমটা না পেলে পরেরটা — সবই ফিল্টার করা।
        private val FAMILY = listOf(
            "185.228.168.168", "185.228.169.168",      // CleanBrowsing Family
            "1.1.1.3", "1.0.0.3",                      // Cloudflare Family
            "208.67.222.123", "208.67.220.123",        // OpenDNS FamilyShield
            "2a0d:2a00:1::", "2a0d:2a00:2::",          // CleanBrowsing Family (IPv6)
            "2606:4700:4700::1113", "2606:4700:4700::1003" // Cloudflare Family (IPv6)
        )

        private const val TRY_TIMEOUT_MS = 1500
        private const val TOTAL_BUDGET_MS = 4500L
        private const val MAX_UDP_REPLY = 1400   // টান ইন্টারফেসের MTU-র মধ্যে থাকতে হবে

        fun start(c: Context) {
            c.startService(Intent(c, DnsVpnService::class.java))
        }

        fun stop(c: Context) {
            c.startService(Intent(c, DnsVpnService::class.java).setAction("STOP"))
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private val pool = ThreadPoolExecutor(
        4, 32, 30, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(128),
        ThreadPoolExecutor.DiscardPolicy()
    )
    @Volatile private var running = false

    // সর্বশেষ যে সার্ভার থেকে উত্তর পাওয়া গেছে, সেটা আগে চেষ্টা হবে (বারবার টাইমআউট এড়াতে)
    @Volatile private var goodSafe = 0
    @Volatile private var goodPlain = 0

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
            val b = Builder()
                .setSession("NafsGuard Safe Search")
                .addAddress("10.111.222.1", 32)
                .addDnsServer("10.111.222.2")
                .addRoute("10.111.222.2", 32)
                .setMtu(1500)
                .setBlocking(true)
            // WiFi-কে "মোবাইল ডাটা (metered)" ভেবে অ্যাপ/ভিডিও/আপডেট ধীর বা বন্ধ করা ঠেকায়
            if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
            // নির্দিষ্ট করে বলা: আন্ডারলাইং নেটওয়ার্ক = সক্রিয় নেটওয়ার্ক (WiFi/মোবাইল বদলালে নিজে থেকে মেনে চলে)
            if (Build.VERSION.SDK_INT >= 22) b.setUnderlyingNetworks(null)
            val pfd = b.establish() ?: return
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

    override fun onDestroy() { shutdown(); try { pool.shutdownNow() } catch (e: Exception) {}; super.onDestroy() }
    override fun onRevoke() { shutdown(); super.onRevoke() }

    private fun u16(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
        ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

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
                if (n < 20) continue
                if (((buf[0].toInt() shr 4) and 0x0F) != 4) continue
                val ihl = (buf[0].toInt() and 0x0F) * 4
                if (ihl < 20 || n < ihl + 8) continue
                var match = true
                for (i in 0..3) if (buf[16 + i] != VIRTUAL_DNS[i]) match = false
                if (!match) continue
                val proto = buf[9].toInt() and 0xFF
                if (proto == 6) {
                    // TCP (DNS-over-TCP বা Private DNS/DoT): আমরা সামলাই না — সঙ্গে সঙ্গে RST দিয়ে জানাই,
                    // যাতে সিস্টেম টাইমআউটের জন্য আটকে না থেকে সাধারণ UDP DNS-এ ফিরে যায়
                    try { replyRst(buf, n, ihl, output) } catch (e: Exception) {}
                    continue
                }
                if (proto != 17) continue
                if (u16(buf, ihl + 2) != 53) continue
                val srcPort = u16(buf, ihl)
                val udpLen = u16(buf, ihl + 4)
                val end = minOf(n, ihl + udpLen)
                if (end <= ihl + 8) continue
                val query = buf.copyOfRange(ihl + 8, end)
                val clientIp = buf.copyOfRange(12, 16)
                pool.execute { resolve(query, clientIp, srcPort, output) }
            } catch (e: Exception) {
                if (!running) break
                errs++
                if (errs > 200) break
                try { Thread.sleep(50) } catch (x: Exception) {}
            }
        }
        if (running) { shutdown(); try { stopSelf() } catch (e: Exception) {} }
    }

    // ---------- TCP RST ----------
    private fun csum(p: ByteArray, off: Int, len: Int, init: Long = 0): Int {
        var sum = init
        var i = off
        val e = off + len
        while (i + 1 < e) {
            sum += ((p[i].toLong() and 0xFF) shl 8) or (p[i + 1].toLong() and 0xFF)
            i += 2
        }
        if (i < e) sum += (p[i].toLong() and 0xFF) shl 8
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun replyRst(req: ByteArray, n: Int, ihl: Int, out: FileOutputStream) {
        if (n < ihl + 20) return
        val flags = req[ihl + 13].toInt() and 0xFF
        if ((flags and 0x04) != 0) return            // RST-এর জবাবে RST নয়
        val dataOff = ((req[ihl + 12].toInt() and 0xF0) shr 4) * 4
        val payload = maxOf(0, n - ihl - dataOff)
        val seqIn = u32(req, ihl + 4)
        val ackIn = u32(req, ihl + 8)
        val p = ByteArray(40)
        p[0] = 0x45
        p[2] = 0; p[3] = 40
        p[6] = 0x40.toByte()
        p[8] = 64; p[9] = 6
        System.arraycopy(req, 16, p, 12, 4)          // src = ভার্চুয়াল DNS
        System.arraycopy(req, 12, p, 16, 4)          // dst = ক্লায়েন্ট
        val ipck = csum(p, 0, 20)
        p[10] = (ipck shr 8).toByte(); p[11] = ipck.toByte()
        // TCP
        p[20] = req[ihl + 2]; p[21] = req[ihl + 3]   // src port = আসা dst port
        p[22] = req[ihl];     p[23] = req[ihl + 1]   // dst port = আসা src port
        val seq: Long
        val ack: Long
        val outFlags: Int
        if ((flags and 0x10) != 0) {
            seq = ackIn; ack = 0; outFlags = 0x04
        } else {
            val consumed = payload + (if ((flags and 0x02) != 0) 1 else 0) + (if ((flags and 0x01) != 0) 1 else 0)
            seq = 0; ack = (seqIn + consumed) and 0xFFFFFFFFL; outFlags = 0x14
        }
        for (k in 0..3) p[24 + k] = (seq shr (24 - 8 * k)).toByte()
        for (k in 0..3) p[28 + k] = (ack shr (24 - 8 * k)).toByte()
        p[32] = 0x50.toByte()                         // হেডার ২০ বাইট
        p[33] = outFlags.toByte()
        // pseudo header
        var ps = 0L
        for (k in 0 until 8 step 2) ps += ((p[12 + k].toLong() and 0xFF) shl 8) or (p[13 + k].toLong() and 0xFF)
        ps += 6 + 20
        val tck = csum(p, 20, 20, ps)
        p[36] = (tck shr 8).toByte(); p[37] = tck.toByte()
        synchronized(out) { out.write(p) }
    }

    // DNS প্রশ্ন থেকে ডোমেইন নাম বের করা (যেমন "t.me")
    private fun qname(q: ByteArray): String? {
        if (q.size < 13) return null
        val sb = StringBuilder()
        var i = 12
        while (i < q.size) {
            val len = q[i].toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) != 0 || i + 1 + len > q.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..len) sb.append((q[i + k].toInt() and 0xFF).toChar())
            i += 1 + len
        }
        return sb.toString()
    }

    // শুধু হেডার + প্রশ্ন রেখে একটা ত্রুটির উত্তর বানানো (rcode: 3 = NXDOMAIN, 2 = SERVFAIL)
    private fun errReply(q: ByteArray, rcode: Int): ByteArray {
        var i = 12
        while (i < q.size && q[i].toInt() != 0) i += (q[i].toInt() and 0xFF) + 1
        val end = minOf(q.size, i + 1 + 4)
        val r = q.copyOfRange(0, end)
        r[2] = (0x80 or (q[2].toInt() and 0x01)).toByte()
        r[3] = (0x80 or rcode).toByte()
        for (k in 6..11) r[k] = 0
        return r
    }

    // ---------- আপস্ট্রিম ----------
    private fun queryUdp(q: ByteArray, server: String): ByteArray? {
        val s = DatagramSocket()
        try {
            if (!protect(s)) return null            // ভিপিএনের ভেতর দিয়ে নয়, সরাসরি সক্রিয় নেটওয়ার্ক দিয়ে
            s.soTimeout = TRY_TIMEOUT_MS
            val addr = InetAddress.getByName(server)   // আইপি লিটারেল — DNS লুকআপ হয় না
            s.send(DatagramPacket(q, q.size, addr, 53))
            val rb = ByteArray(4096)
            val rp = DatagramPacket(rb, rb.size)
            while (true) {
                s.receive(rp)
                // আইডি না মিললে (বাসি/ভুয়া প্যাকেট) বাদ
                if (rp.length >= 12 && rb[0] == q[0] && rb[1] == q[1]) break
            }
            return rb.copyOf(rp.length)
        } finally {
            try { s.close() } catch (e: Exception) {}
        }
    }

    // উত্তর ছাঁটা (TC) হলে TCP-তে পুরো উত্তর আনা
    private fun queryTcp(q: ByteArray, server: String): ByteArray? {
        val s = Socket()
        try {
            s.tcpNoDelay = true
            if (!protect(s)) return null
            s.soTimeout = TRY_TIMEOUT_MS
            s.connect(InetSocketAddress(InetAddress.getByName(server), 53), TRY_TIMEOUT_MS)
            val os = s.getOutputStream()
            os.write(byteArrayOf((q.size shr 8).toByte(), q.size.toByte()))
            os.write(q); os.flush()
            val ins = s.getInputStream()
            val h = ByteArray(2)
            var got = 0
            while (got < 2) { val r = ins.read(h, got, 2 - got); if (r < 0) return null; got += r }
            val len = ((h[0].toInt() and 0xFF) shl 8) or (h[1].toInt() and 0xFF)
            if (len < 12) return null
            val rb = ByteArray(len)
            got = 0
            while (got < len) { val r = ins.read(rb, got, len - got); if (r < 0) return null; got += r }
            return rb
        } finally {
            try { s.close() } catch (e: Exception) {}
        }
    }

    private fun upstream(query: ByteArray): ByteArray? {
        val safe = Prefs.safe(this)
        val list = if (safe) FAMILY else PLAIN
        val start = if (safe) goodSafe else goodPlain
        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
        for (k in list.indices) {
            if (System.currentTimeMillis() >= deadline) break
            val idx = (start + k) % list.size
            val server = list[idx]
            try {
                var r = queryUdp(query, server) ?: continue
                // উত্তর ছাঁটা হলে TCP-তে পুরোটা আনি; MTU-তে না ধরলে ছাঁটা উত্তরই যাক (সিস্টেম নিজে সামলাবে)
                if (r.size > 2 && (r[2].toInt() and 0x02) != 0) {
                    try {
                        val full = queryTcp(query, server)
                        if (full != null && full.size <= MAX_UDP_REPLY) r = full
                    } catch (e: Exception) {}
                }
                if (r.size > MAX_UDP_REPLY) continue
                if (safe) goodSafe = idx else goodPlain = idx
                return r
            } catch (e: Exception) {
                // এই সার্ভার/নেটওয়ার্কে হলো না — পরেরটা
            }
        }
        return null
    }

    private fun resolve(query: ByteArray, clientIp: ByteArray, clientPort: Int, out: FileOutputStream) {
        try {
            val host = qname(query)
            if (host != null && Prefs.linkBlock(this) && LinkBlock.isBlockedHost(host)) {
                val r = errReply(query, 3)
                writeResponse(r, r.size, clientIp, clientPort, out)
                return
            }
            val r = upstream(query)
            if (r != null) {
                writeResponse(r, r.size, clientIp, clientPort, out)
            } else if (query.size >= 12) {
                // সব সার্ভার ফেল: চুপ না থেকে সঙ্গে সঙ্গে SERVFAIL, যাতে অ্যাপ দ্রুত আবার চেষ্টা করতে পারে
                val e = errReply(query, 2)
                writeResponse(e, e.size, clientIp, clientPort, out)
            }
        } catch (e: Exception) {
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
        val ck = csum(p, 0, 20)
        p[10] = (ck shr 8).toByte(); p[11] = ck.toByte()
        p[20] = 0; p[21] = 53
        p[22] = (clientPort shr 8).toByte(); p[23] = clientPort.toByte()
        val ul = 8 + len
        p[24] = (ul shr 8).toByte(); p[25] = ul.toByte()
        System.arraycopy(data, 0, p, 28, len)
        synchronized(out) { out.write(p) }
    }
}
