package com.twoskoops707.sixdegrees.ui.results

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingUrlHelperTest {

    private val meta = mapOf(
        "person_name" to "Jane Doe",
        "person_city" to "Austin",
        "person_state" to "TX",
        "person_entered_address" to "123 Main St",
        "person_phone" to "(512) 555-0100",
        "person_email" to "jane@example.com"
    )

    @Test
    fun courtUrl_usesSubjectNotLabelText() {
        val ctx = FindingUrlHelper.subjectContext(meta)
        val url = FindingUrlHelper.courtUrl(ctx, "Court docs")
        assertTrue(url.contains("courtlistener.com"))
        assertTrue(url.contains("Jane"))
        assertFalse(url.contains("Court+docs"))
    }

    @Test
    fun resolveUrl_prefersSourceUrlOverDisplayText() {
        val finding = DossierFinding(
            label = "Court records",
            value = "Search court records",
            source = "CourtListener",
            confidence = DossierConfidence.MEDIUM,
            isLink = true,
            sourceUrl = FindingUrlHelper.courtUrl(FindingUrlHelper.subjectContext(meta))
        )
        val url = FindingUrlHelper.resolveUrl(finding, meta)
        assertTrue(url!!.contains("courtlistener.com"))
        assertTrue(url.contains("Jane"))
    }

    @Test
    fun verificationUrl_mapsAddressToMaps() {
        val url = FindingUrlHelper.verificationUrl(
            DossierFinding(
                label = "Address",
                value = "123 Main St, Austin, TX",
                source = "Records",
                confidence = DossierConfidence.HIGH
            ),
            meta
        )
        assertTrue(url!!.contains("google.com/maps"))
        assertTrue(url.contains("123"))
    }

    @Test
    fun isUsableSearchText_rejectsGenericLabels() {
        assertFalse(FindingUrlHelper.isUsableSearchText("court docs"))
        assertFalse(FindingUrlHelper.isUsableSearchText("Search court records"))
        assertTrue(FindingUrlHelper.isUsableSearchText("Jane Doe Austin TX"))
    }

    @Test
    fun bestDisplayLocation_includesStreetAddress() {
        val loc = FindingUrlHelper.bestDisplayLocation(meta)
        assertTrue(loc.contains("123 Main St"))
        assertTrue(loc.contains("Austin"))
    }
}
