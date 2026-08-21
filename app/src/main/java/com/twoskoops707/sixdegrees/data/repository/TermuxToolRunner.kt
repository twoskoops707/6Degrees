package com.twoskoops707.sixdegrees.data.repository



import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

import com.squareup.moshi.Moshi

import com.squareup.moshi.Types

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.flow

import kotlinx.coroutines.flow.flowOn

import java.io.File



class TermuxToolRunner(private val context: Context) {



    companion object {
        private const val DONE_SENTINEL = "__DONE__"

        /** Shared path Termux can write after `termux-setup-storage`. */
        const val SHARED_OUTPUT_DIR = "/storage/emulated/0/.6degrees"

        const val LEGACY_STATUS_FILE = "$SHARED_OUTPUT_DIR/.6d_tools_status.txt"

        val SEARCH_TOOLS = listOf("sherlock", "maigret", "holehe", "tor", "theharvester", "nmap")



        /** Maps logical tool id → executable names Termux may install. */

        private val TOOL_BINARIES = mapOf(

            "sherlock" to listOf("sherlock"),

            "maigret" to listOf("maigret"),

            "holehe" to listOf("holehe"),

            "tor" to listOf("tor"),

            "theharvester" to listOf("theHarvester", "theharvester"),

            "nmap" to listOf("nmap")

        )

        /** App-private output dir for contexts without a TermuxToolRunner instance. */
        fun appPrivateOutputPath(context: Context): String {
            val dir = context.getExternalFilesDir(null)?.let { File(it, ".6degrees") }
                ?: File(context.filesDir, ".6degrees")
            dir.mkdirs()
            return dir.absolutePath
        }

    }



    private val outputDir: File = resolveOutputDir()

    private val statusFile: File = File(outputDir, ".6d_tools_status.txt")



    /** App-writable path Termux can also write to via RUN_COMMAND. */

    fun statusFilePath(): String = statusFile.absolutePath



    /** App-private output dir (no storage permission needed on any Android version). */

    fun appPrivateOutputPath(): String = appPrivateOutputDir().absolutePath



    private fun resolveOutputDir(): File = appPrivateOutputDir()

    private fun appPrivateOutputDir(): File {
        val ext = context.getExternalFilesDir(null)?.let { File(it, ".6degrees") }
        return (ext ?: File(context.filesDir, ".6degrees")).also { it.mkdirs() }
    }



    fun isTermuxInstalled(): Boolean = try {

        context.packageManager.getPackageInfo("com.termux", 0)

        true

    } catch (_: PackageManager.NameNotFoundException) { false }



    fun readToolStatus(): Map<String, Boolean> {
        for (candidate in statusFileCandidates()) {
            readStatusText(candidate)?.let { return parseStatusText(it) }
        }
        return emptyMap()
    }

    private fun statusFileCandidates(): List<File> = listOf(
        statusFile,
        File(appPrivateOutputDir(), ".6d_tools_status.txt"),
        File(LEGACY_STATUS_FILE)
    ).distinctBy { it.absolutePath }

    private fun readStatusText(file: File): String? {
        if (!file.exists()) return null
        return try {
            file.readText().takeIf { it.contains(DONE_SENTINEL) }
        } catch (_: Exception) { null }
    }



    private fun parseStatusText(content: String): Map<String, Boolean> =

        content.lines()

            .filter { it.contains(":ok") || it.contains(":missing") }

            .associate { line ->

                val key = line.substringBefore(":").trim().lowercase()

                key to line.contains(":ok")

            }



    fun infrastructureSummary(): String {

        val termux = if (isTermuxInstalled()) "installed" else "missing"

        val status = readToolStatus()

        val ready = SEARCH_TOOLS.count { status[it.lowercase()] == true }

        val torLine = when {

            com.twoskoops707.sixdegrees.tor.TorBootstrapManager.isPortOpen() -> "Tor SOCKS :9050 ready"

            status["tor"] == true -> "Tor installed in Termux (not running)"

            else -> "Tor not available"

        }

        return "Termux: $termux · CLI tools: $ready/${SEARCH_TOOLS.size} · $torLine"

    }



    fun requestToolStatusRefresh() {

        if (!isTermuxInstalled()) return

        fireCommand(buildStatusCheckCommand(SEARCH_TOOLS))

    }



    fun buildStatusCheckCommand(tools: List<String>): String {

        val checks = tools.joinToString(" ; ") { toolId ->

            val bins = TOOL_BINARIES[toolId.lowercase()] ?: listOf(toolId)

            val existsChecks = bins.joinToString(" || ") { bin ->
                "command -v $bin >/dev/null 2>&1" +
                    " || [ -x \"\$PREFIX/bin/$bin\" ]" +
                    " || [ -x \"\$HOME/.local/bin/$bin\" ]" +
                    " || [ -x /data/data/com.termux/files/usr/bin/$bin ]"
            }

            "($existsChecks) && echo $toolId:ok || echo $toolId:missing"

        }

        val appPath = statusFile.absolutePath
        val sharedPath = "$SHARED_OUTPUT_DIR/.6d_tools_status.txt"
        return "mkdir -p ${shellQuote(outputDir.absolutePath)} ${shellQuote(SHARED_OUTPUT_DIR)} && { $checks ; } > ${shellQuote(appPath)} 2>&1 ; cp ${shellQuote(appPath)} ${shellQuote(sharedPath)} 2>/dev/null ; echo $DONE_SENTINEL >> ${shellQuote(appPath)} ; echo $DONE_SENTINEL >> ${shellQuote(sharedPath)} 2>/dev/null"

    }



    private fun isToolInstalled(name: String): Boolean {
        val key = name.lowercase()
        val status = readToolStatus()
        return when (status[key]) {
            true -> true
            false -> false
            null -> {
                requestToolStatusRefresh()
                true
            }
        }
    }



    private fun fireCommand(cmd: String) {

        val intent = Intent().apply {

            setClassName("com.termux", "com.termux.app.RunCommandService")

            action = "com.termux.RUN_COMMAND"

            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")

            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd))

            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")

            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)

            putExtra("com.termux.RUN_COMMAND_RESULT_DIRECTORY", outputDir.absolutePath)

        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            context.startService(intent)
        }

    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun commandWithSentinel(command: String, outFile: File): String {
        val outPath = shellQuote(outFile.absolutePath)
        return "rm -f $outPath ; $command ; echo $DONE_SENTINEL >> $outPath"
    }



    private suspend fun pollFile(file: File, maxMs: Long = 120_000L): String? {

        val backoffs = longArrayOf(3000, 6000, 12000, 24000, 48000)

        var waited = 0L

        var backoffIdx = 0

        while (waited < maxMs) {

            val delayMs = if (backoffIdx < backoffs.size) backoffs[backoffIdx++] else backoffs.last()

            delay(delayMs)

            waited += delayMs

            if (file.exists() && file.length() > 0) {
                val content = file.readText()
                if (content.contains(DONE_SENTINEL)) {
                    return content.replace(DONE_SENTINEL, "").trim()
                }
            }

        }

        return null

    }



    fun runSherlock(username: String): Flow<SearchProgressEvent> = flow {

        if (!isTermuxInstalled()) return@flow

        if (!isToolInstalled("sherlock")) {

            emit(SearchProgressEvent.NotFound("sherlock"))

            return@flow

        }

        emit(SearchProgressEvent.Checking("sherlock"))

        val outFile = File(outputDir, "sherlock_${System.currentTimeMillis()}.txt")

        val cmd = commandWithSentinel(
            "sherlock ${shellQuote(username.trim())} --output ${shellQuote(outFile.absolutePath)} --print-found 2>/dev/null",
            outFile
        )

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

        if (!isTermuxInstalled()) return@flow

        if (!isToolInstalled("maigret")) {

            emit(SearchProgressEvent.NotFound("maigret"))

            return@flow

        }

        emit(SearchProgressEvent.Checking("maigret"))

        val outFile = File(outputDir, "maigret_${System.currentTimeMillis()}.json")

        val cmd = commandWithSentinel(
            "maigret ${shellQuote(username.trim())} -J simple --no-pics -o ${shellQuote(outFile.absolutePath)} 2>/dev/null",
            outFile
        )

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

        if (!isTermuxInstalled()) return@flow

        if (!isToolInstalled("holehe")) {

            emit(SearchProgressEvent.NotFound("holehe"))

            return@flow

        }

        emit(SearchProgressEvent.Checking("holehe"))

        val outFile = File(outputDir, "holehe_${System.currentTimeMillis()}.txt")

        val cmd = commandWithSentinel(
            "holehe ${shellQuote(email.trim())} > ${shellQuote(outFile.absolutePath)} 2>&1",
            outFile
        )

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

        if (!isTermuxInstalled()) return@flow

        if (!isToolInstalled("theharvester")) {

            emit(SearchProgressEvent.NotFound("theHarvester"))

            return@flow

        }

        emit(SearchProgressEvent.Checking("theHarvester"))

        val outFile = File(outputDir, "harvester_${System.currentTimeMillis()}.txt")

        val harvester = "theHarvester"

        val cmd = commandWithSentinel(
            "$harvester -d ${shellQuote(domain.trim())} -b duckduckgo,bing,google > ${shellQuote(outFile.absolutePath)} 2>/dev/null",
            outFile
        )

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

        if (com.twoskoops707.sixdegrees.tor.TorBootstrapManager.isPortOpen()) {

            com.twoskoops707.sixdegrees.tor.TorBootstrapManager.markReady()

            emit(SearchProgressEvent.Found("Tor", "SOCKS proxy ready on :9050"))

            return@flow

        }

        if (!isToolInstalled("tor")) {

            emit(SearchProgressEvent.NotFound("Tor (not installed in Termux)"))

            return@flow

        }

        emit(SearchProgressEvent.Checking("Tor"))

        try {

            fireCommand("tor --SocksPort 9050 --DataDirectory /data/data/com.termux/files/home/.tor &>/dev/null &")

        } catch (_: Exception) {

            emit(SearchProgressEvent.Blocked("Tor", "Could not start via Termux"))

            return@flow

        }

        var waited = 0L

        val maxWait = 45_000L

        while (waited < maxWait) {

            delay(2000)

            waited += 2000

            if (com.twoskoops707.sixdegrees.tor.TorBootstrapManager.isPortOpen()) {

                com.twoskoops707.sixdegrees.tor.TorBootstrapManager.markReady()

                emit(SearchProgressEvent.Found("Tor", "Termux Tor connected on :9050"))

                return@flow

            }

        }

        emit(SearchProgressEvent.Blocked("Tor", "Timed out waiting for SOCKS proxy"))

    }.flowOn(Dispatchers.IO)



    fun runNmap(target: String): Flow<SearchProgressEvent> = flow {

        if (!isTermuxInstalled()) return@flow

        if (!isToolInstalled("nmap")) {

            emit(SearchProgressEvent.NotFound("nmap"))

            return@flow

        }

        emit(SearchProgressEvent.Checking("nmap"))

        val outFile = File(outputDir, "nmap_${System.currentTimeMillis()}.txt")

        val cmd = commandWithSentinel(
            "nmap -sV --open -oN ${shellQuote(outFile.absolutePath)} ${shellQuote(target.trim())} 2>/dev/null",
            outFile
        )

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

