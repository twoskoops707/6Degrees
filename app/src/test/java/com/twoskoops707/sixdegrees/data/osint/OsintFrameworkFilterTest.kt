package com.twoskoops707.sixdegrees.data.osint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
class OsintFrameworkFilterTest {

    @Test
    fun urlBuilder_replacesFrameworkPlaceholders() {
        val url = OsintUrlBuilder.buildUrl(
            "https://api.protonmail.ch/pks/lookup?op=index&search=<username>@protonmail.com",
            "alice"
        )
        assertTrue(url.contains("alice"))
        assertFalse(url.contains("<username>"))
    }

    @Test
    fun urlBuilder_replacesSixDegreesTemplates() {
        val url = OsintUrlBuilder.buildUrl(
            "https://example.com/{q-encoded}?n={first}&l={last}",
            "Jane Doe"
        )
        assertTrue(url.contains("Jane"))
        assertTrue(url.contains("Doe"))
    }

    @Test
    fun normalizeToolName_stripsSuffixTags() {
        assertEquals("courtlistener", OsintUrlBuilder.normalizeToolName("CourtListener"))
        assertEquals("dehashed", OsintUrlBuilder.normalizeToolName("DeHashed (R)"))
    }

    @Test
    fun executor_resolvesCourtListenerByHost() {
        val executor = OsintInAppExecutorCatalog.resolveExecutor(
            "CourtListener",
            "https://www.courtlistener.com/",
            listOf("Public Records", "CourtListener")
        )
        assertNotNull(executor)
        assertEquals(OsintInAppExecutorCatalog.ExecutorId.COURT_LISTENER, executor!!.id)
    }

    @Test
    fun executor_resolvesUsernameSearchFolder() {
        val executor = OsintInAppExecutorCatalog.resolveExecutor(
            "WhatsMyName Web",
            "https://whatsmyname.app/",
            listOf("Username", "Username Search Engines")
        )
        assertNotNull(executor)
        assertEquals(OsintInAppExecutorCatalog.ExecutorId.USERNAME_SCAN, executor!!.id)
    }

    @Test
    fun reportBridge_tagsFrameworkToolsUsed() {
        val metadata = java.util.concurrent.ConcurrentHashMap<String, String>()
        metadata["courtlistener_count"] = "2"
        metadata["court_cases"] = "Case A; Case B"
        val tools = listOf(
            OsintToolRegistry.OsintTool(
                id = "1",
                name = "CourtListener",
                description = "Courts",
                urlTemplate = "https://www.courtlistener.com/",
                categories = setOf("records"),
                frameworkPath = listOf("Public Records", "CourtListener"),
                isQueryable = false
            )
        )
        OsintFrameworkReportBridge.apply(metadata, tools)
        assertTrue(metadata[OsintFrameworkReportBridge.KEY_TOOLS_USED]?.contains("CourtListener") == true)
        assertTrue(metadata[OsintFrameworkReportBridge.KEY_REPORT_LINES]?.isNotBlank() == true)
    }
}
