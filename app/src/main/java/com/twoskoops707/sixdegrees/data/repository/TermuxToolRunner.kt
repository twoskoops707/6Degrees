package com.twoskoops707.sixdegrees.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class TermuxToolRunner(private val context: Context) {

    private val outputDir = File("/storage/emulated/0/.6degrees").also { it.mkdirs() }

    fun isTermuxInstalled(): Boolean = try {
        context.packageManager.getPackageInfo("com.termux", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun isToolInstalled(name: String): Boolean =
        File("/data/data/com.termux/files/usr/bin/$name").exists()

    private fun fireCommand(cmd: String) {
        val intent = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        context.startForegroundService(intent)
    }

    private suspend fun pollFile(file: File, maxMs: Long = 120_000L): String? {
        val backoffs = longArrayOf(3000, 6000, 12000, 24000, 48000)
        var waited = 0L
        var backoffIdx = 0
        while (waited < maxMs) {
            val delay = if (backoffIdx < backoffs.size) backoffs[backoffIdx++] else backoffs.last()
            delay(delay)
            waited += delay
            if (file.exists() && file.length() > 0) return file.readText()
        }
        return null
    }

    fun runSherlock(username: String): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled() || !isToolInstalled("sherlock")) return@flow
        emit(SearchProgressEvent.Checking("sherlock"))
        val outFile = File(outputDir, "sherlock_${System.currentTimeMillis()}.txt")
        val cmd = "sherlock ${username.trim()} --output ${outFile.absolutePath} --print-found 2>/dev/null"
        try {
            fireCommand(cmd)
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("sherlock", "Could not reach Termux RPC"))
            return@flow
        }
        val content = pollFile(outFile)
        outFile.delete()
        if (content == null) {
            emit(SearchProgressEvent.Blocked("sherlock", "tool timed out"))
            return@flow
        }
        val urls = content.lines()
            .filter { it.startsWith("[+]") || it.contains("http") }
            .mapNotNull { line ->
                val urlMatch = Regex("https?://[^\\s]+").find(line)?.value ?: return@mapNotNull null
                val platform = line.substringBefore(":").trimStart('[', '+', ']', ' ').trim()
                    .ifBlank { urlMatch.substringAfter("://").substringBefore("/").substringBefore(".")
                        .replaceFirstChar { it.uppercase() } }
                Pair(platform, urlMatch)
            }
        if (urls.isEmpty()) {
            emit(SearchProgressEvent.NotFound("sherlock"))
            return@flow
        }
        urls.forEach { (platform, url) ->
            emit(SearchProgressEvent.Found("sherlock/$platform", url))
        }
    }.flowOn(Dispatchers.IO)

    fun runMaigret(username: String): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled() || !isToolInstalled("maigret")) return@flow
        emit(SearchProgressEvent.Checking("maigret"))
        val outFile = File(outputDir, "maigret_${System.currentTimeMillis()}.json")
        val cmd = "maigret ${username.trim()} -J simple --no-pics -o ${outFile.absolutePath} 2>/dev/null"
        try {
            fireCommand(cmd)
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("maigret", "Could not reach Termux RPC"))
            return@flow
        }
        val content = pollFile(outFile)
        outFile.delete()
        if (content == null) {
            emit(SearchProgressEvent.Blocked("maigret", "tool timed out"))
            return@flow
        }
        try {
            val moshi = Moshi.Builder().build()
            val mapAdapter = moshi.adapter<Map<String, Any>>(
                Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            )
            val root = mapAdapter.fromJson(content) ?: return@flow
            var emitted = 0
            @Suppress("UNCHECKED_CAST")
            root.forEach { (site, data) ->
                val entry = data as? Map<String, Any> ?: return@forEach
                val status = entry["status"] as? String ?: ""
                val url = entry["url"] as? String ?: ""
                if ((status.contains("found", ignoreCase = true) || status == "claimed") && url.isNotEmpty()) {
                    emit(SearchProgressEvent.Found("maigret/$site", url))
                    emitted++
                }
            }
            if (emitted == 0) emit(SearchProgressEvent.NotFound("maigret"))
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("maigret", "output parse error"))
        }
    }.flowOn(Dispatchers.IO)

    fun runHolehe(email: String): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled() || !isToolInstalled("holehe")) return@flow
        emit(SearchProgressEvent.Checking("holehe"))
        val outFile = File(outputDir, "holehe_${System.currentTimeMillis()}.txt")
        val cmd = "holehe ${email.trim()} > ${outFile.absolutePath} 2>&1"
        try {
            fireCommand(cmd)
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("holehe", "Could not reach Termux RPC"))
            return@flow
        }
        val content = pollFile(outFile)
        outFile.delete()
        if (content == null) {
            emit(SearchProgressEvent.Blocked("holehe", "tool timed out"))
            return@flow
        }
        val found = content.lines()
            .filter { line ->
                line.contains("[+]") || (line.contains("used") && !line.contains("not used", ignoreCase = true))
            }
            .mapNotNull { line ->
                val service = Regex("\\[\\+\\]\\s*(.+)").find(line)?.groupValues?.get(1)?.trim()
                    ?: line.substringBefore(":").trim().ifBlank { null }
                service?.takeIf { it.isNotBlank() && it.length < 60 }
            }
            .distinct()
        if (found.isEmpty()) {
            emit(SearchProgressEvent.NotFound("holehe"))
            return@flow
        }
        found.forEach { service ->
            emit(SearchProgressEvent.Found("holehe/$service", "Email registered on: $service"))
        }
    }.flowOn(Dispatchers.IO)

    fun runTheHarvester(domain: String): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled() || !isToolInstalled("theHarvester")) return@flow
        emit(SearchProgressEvent.Checking("theHarvester"))
        val outFile = File(outputDir, "harvester_${System.currentTimeMillis()}.txt")
        val cmd = "theHarvester -d ${domain.trim()} -b duckduckgo,bing,google -f ${outFile.absolutePath} 2>/dev/null"
        try {
            fireCommand(cmd)
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("theHarvester", "Could not reach Termux RPC"))
            return@flow
        }
        val content = pollFile(outFile, maxMs = 120_000L)
        outFile.delete()
        if (content == null) {
            emit(SearchProgressEvent.Blocked("theHarvester", "tool timed out"))
            return@flow
        }
        var emitted = 0
        Regex("""\[\*\] Emails found:(.*?)(?:\[\*\]|${'$'})""", RegexOption.DOT_MATCHES_ALL)
            .find(content)?.groupValues?.get(1)?.lines()
            ?.map { it.trim() }?.filter { it.isNotBlank() && it.contains("@") }?.distinct()
            ?.forEach { email ->
                emit(SearchProgressEvent.Found("theHarvester/email", email))
                emitted++
            }
        Regex("""\[\*\] Hosts found:(.*?)(?:\[\*\]|${'$'})""", RegexOption.DOT_MATCHES_ALL)
            .find(content)?.groupValues?.get(1)?.lines()
            ?.map { it.trim() }?.filter { it.isNotBlank() && it.contains(".") }?.distinct()
            ?.forEach { host ->
                emit(SearchProgressEvent.Found("theHarvester/host", host))
                emitted++
            }
        if (emitted == 0) emit(SearchProgressEvent.NotFound("theHarvester"))
    }.flowOn(Dispatchers.IO)

    fun ensureTorRunning(): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled()) return@flow
        val torBin = File("/data/data/com.termux/files/usr/bin/tor")
        if (!torBin.exists()) {
            emit(SearchProgressEvent.NotFound("Tor (not installed)"))
            return@flow
        }
        val probeAlive = try {
            val s = java.net.Socket(); s.connect(java.net.InetSocketAddress("127.0.0.1", 9050), 1000); s.close(); true
        } catch (_: Exception) { false }
        if (probeAlive) {
            emit(SearchProgressEvent.Found("Tor", "Already running on :9050"))
            return@flow
        }
        emit(SearchProgressEvent.Checking("Tor"))
        val torrcFile = File(outputDir, "torrc")
        if (!torrcFile.exists()) torrcFile.writeText("SocksPort 9050\nDataDirectory /data/data/com.termux/files/home/.tor\n")
        try {
            fireCommand("tor --SocksPort 9050 --DataDirectory /data/data/com.termux/files/home/.tor &>/dev/null &")
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("Tor", "Could not start via Termux"))
            return@flow
        }
        var waited = 0L
        val maxWait = 30_000L
        while (waited < maxWait) {
            delay(2000)
            waited += 2000
            val ready = try {
                val s = java.net.Socket(); s.connect(java.net.InetSocketAddress("127.0.0.1", 9050), 500); s.close(); true
            } catch (_: Exception) { false }
            if (ready) {
                emit(SearchProgressEvent.Found("Tor", "Connected on :9050"))
                return@flow
            }
        }
        emit(SearchProgressEvent.Blocked("Tor", "Timed out waiting for SOCKS proxy"))
    }.flowOn(Dispatchers.IO)

    fun runNmap(target: String): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled() || !isToolInstalled("nmap")) return@flow
        emit(SearchProgressEvent.Checking("nmap"))
        val outFile = File(outputDir, "nmap_${System.currentTimeMillis()}.txt")
        val cmd = "nmap -sV --open -oN ${outFile.absolutePath} ${target.trim()} 2>/dev/null"
        try {
            fireCommand(cmd)
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("nmap", "Could not reach Termux RPC"))
            return@flow
        }
        val content = pollFile(outFile, maxMs = 120_000L)
        outFile.delete()
        if (content == null) {
            emit(SearchProgressEvent.Blocked("nmap", "tool timed out"))
            return@flow
        }
        val portLines = content.lines()
            .filter { it.matches(Regex("""^\d+/(tcp|udp)\s+open.*""")) }
        if (portLines.isEmpty()) {
            emit(SearchProgressEvent.NotFound("nmap"))
            return@flow
        }
        portLines.forEach { line ->
            val parts = line.split(Regex("\\s+"))
            val portProto = parts.getOrNull(0) ?: return@forEach
            val service = parts.getOrNull(2) ?: ""
            val version = parts.drop(3).joinToString(" ").take(80)
            val detail = if (version.isNotBlank()) "$portProto $service $version" else "$portProto $service"
            emit(SearchProgressEvent.Found("nmap/port", detail.trim()))
        }
    }.flowOn(Dispatchers.IO)
}
