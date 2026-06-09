package com.twoskoops707.sixdegrees.tor

import android.content.Context
import android.content.Intent
import android.util.Log
import org.torproject.jni.TorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object TorBootstrapManager {

    private const val TAG = "TorBootstrap"
    const val TOR_SOCKS_PORT = 9050

    @Volatile private var started = false
    @Volatile var isReady = false
        private set

    fun isPortOpen(): Boolean = probePort()

    fun markReady() {
        isReady = true
    }

    fun resetReady() {
        isReady = false
    }

    fun start(context: Context) {
        if (started) return
        started = true
        if (probePort()) {
            isReady = true
            Log.d(TAG, "Tor already available on :$TOR_SOCKS_PORT")
            return
        }

        try {
            val intent = Intent(context.applicationContext, TorService::class.java)
            context.applicationContext.startService(intent)
            Log.d(TAG, "Embedded TorService started")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start embedded Tor: ${e.message}")
        }
    }

    suspend fun waitForReady(timeoutMs: Long = 90_000L): Boolean = withContext(Dispatchers.IO) {
        if (isReady && probePort()) return@withContext true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(1000)
            if (probePort()) {
                isReady = true
                return@withContext true
            }
        }
        false
    }

    /** Prefer embedded Tor; returns true when SOCKS :9050 accepts connections. */
    suspend fun ensureReady(context: Context, embeddedTimeoutMs: Long = 60_000L): Boolean {
        if (probePort()) {
            isReady = true
            return true
        }
        start(context)
        return waitForReady(embeddedTimeoutMs)
    }

    private fun probePort(): Boolean = try {
        val s = Socket()
        s.connect(InetSocketAddress("127.0.0.1", TOR_SOCKS_PORT), 800)
        s.close()
        true
    } catch (_: Exception) { false }
}
