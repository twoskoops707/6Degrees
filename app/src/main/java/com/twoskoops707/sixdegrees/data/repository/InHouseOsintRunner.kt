package com.twoskoops707.sixdegrees.data.repository

import com.twoskoops707.sixdegrees.domain.model.DataSource
import com.twoskoops707.sixdegrees.domain.model.UsernamePlatformHit
import kotlinx.coroutines.channels.SendChannel
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs OSINT CLI-equivalent tools inside the APK (no Termux permission required).
 * Replaces sherlock, maigret, and holehe for default search flows.
 */
class InHouseOsintRunner(
    private val usernameDiscovery: UsernameDiscoveryService = UsernameDiscoveryService(),
    private val emailRegistration: EmailRegistrationService = EmailRegistrationService()
) {

    suspend fun runUsernameScan(
        username: String,
        metadata: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        channel: SendChannel<SearchProgressEvent>,
        socialUrls: Collection<String> = emptyList()
    ) {
        val clean = username.trim().removePrefix("@")
        if (clean.length < 2 || clean.contains(' ')) return

        val hits = if (socialUrls.isNotEmpty()) {
            usernameDiscovery.discoverFromHints(listOf(clean), socialUrls) { channel.send(it) }
        } else {
            usernameDiscovery.discoverAndEnrich(clean) { channel.send(it) }
        }
        if (hits.isEmpty()) return

        usernameDiscovery.applyToMetadata(hits, metadata, clean)
        sources.add(DataSource("Username Scan", "in-app", Date(), 0.88))
        writeUsernameCompatKeys(hits, metadata)
    }

    suspend fun runEmailRegistrationScan(
        email: String,
        metadata: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        channel: SendChannel<SearchProgressEvent>
    ) {
        val hits = emailRegistration.check(email) { channel.send(it) }
        if (hits.isEmpty()) return
        val services = hits.map { it.service }
        metadata["holehe_services"] = services.joinToString(", ")
        metadata["holehe_found"] = hits.joinToString("\n") { "${it.service}: ${it.detail}" }
        sources.add(DataSource("Email Registration Scan", "in-app", Date(), 0.8))
    }

    private fun writeUsernameCompatKeys(hits: List<UsernamePlatformHit>, metadata: ConcurrentHashMap<String, String>) {
        val lines = hits.map { "${it.platform}: ${it.url}" }
        val block = lines.joinToString("\n")
        metadata["inhouse_username_found"] = block
        // Legacy keys consumed by dossier UI
        metadata["sherlock_found"] = block
        metadata["maigret_found"] = block
        lines.forEach { line ->
            val existing = metadata["found_urls"]
            metadata["found_urls"] = if (existing.isNullOrBlank()) line else "$existing\n$line"
        }
    }
}
