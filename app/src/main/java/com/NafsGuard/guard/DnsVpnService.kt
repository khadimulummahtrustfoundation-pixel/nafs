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
 * সেইফ সার্চ: শুধু DNS প্রশ্নগুলো লোকাল VPN দিয়ে ধরে CleanBrowsing Family ফিল্টারে পাঠায়
 * (অ্যাডাল্ট সাইট ব্লক + Google/Bing/YouTube এ সেইফ সার্চ বাধ্যতামূলক)।
 * বাকি ইন্টারনেট ট্রাফিক VPN এর ভেতর দিয়ে যায় না।
 */
class DnsVpnService : VpnService() {

    companion object {
        @Volatile var active = false
        private val VIRTUAL_DNS = byteArrayOf(10, 111, 222.toByte(), 2)
        private val UPSTREAM = listOf(
            byteArrayOf(185.toByte(), 228.toByte(), 168.toByte(), 168.toByte()),
            byteArrayOf(185.toByte(), 228.toByte(), 169.toByte(), 168.toByte())
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
                .setSession("NafsGuard Safe Search")
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
        try {
            while (running) {
                val n = input.read(buf)
                if (n < 0) break
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
                pool.execute { resolve(query, clientIp, srcPort, output) }
            }
        } catch (e: Exception) {
        }
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
