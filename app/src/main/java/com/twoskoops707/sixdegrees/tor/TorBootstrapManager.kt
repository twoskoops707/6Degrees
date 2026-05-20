package com.twoskoops707.sixdegrees.tor

import android.content.Context
import android.content.Intent
import android.util.Log
import org.torproject.jni.TorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

object TorBootstrapManager {

    private const val TAG = "TorBootstrap"
    private const val TOR_PORT = 9050

    @Volatile private var started = false
    @Volatile var isReady = false
        private set

    fun start(context: Context) {
        if (started) return
        started = true
        try {
            val probe = Socket()
            probe.connect(InetSocketAddress("127.0.0.1", TOR_PORT), 800)
            probe.close()
            isReady = true
            Log.d(TAG, "Tor already available on :9050")
            return
        } catch (_: Exception) {}

        try {
            val intent = Intent(context.applicationContext, TorService::class.java)
            context.applicationContext.startService(intent)
            Log.d(TAG, "Embedded TorService started")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start embedded Tor: ${e.message}")
        }
    }

    suspend fun waitForReady(timeoutMs: Long = 90_000L): Boolean = withContext(Dispatchers.IO) {
        if (isReady) return@withContext true
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

    private fun probePort(): Boolean = try {
        val s = Socket()
        s.connect(InetSocketAddress("127.0.0.1", TOR_PORT), 800)
        s.close()
        true
    } catch (_: Exception) { false }
}
