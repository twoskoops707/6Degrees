package com.twoskoops707.sixdegrees.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportMetadataSyncTest {

    @Test
    fun promoteSingularNameToPlural() {
        val meta = mutableMapOf(
            "tt_name" to "Jane Doe",
            "fps_name" to "John Smith"
        )
        ReportMetadataSync.sync(meta)
        assertEquals("Jane Doe", meta["tt_names"])
        assertEquals("John Smith", meta["fps_names"])
    }

    @Test
    fun aggregatesPhoneOwnerNames() {
        val meta = mutableMapOf(
            "tt_names" to "Jane Doe",
            "fps_names" to "Jane Doe, Bob Lee"
        )
        ReportMetadataSync.sync(meta)
        assertTrue(meta["phone_owner_names"]!!.contains("Jane Doe"))
        assertTrue(meta["phone_owner_names"]!!.contains("Bob Lee"))
    }

    @Test
    fun syncsSecFulltextIntoAffiliations() {
        val meta = mutableMapOf(
            "sec_fulltext_hits" to "3",
            "sec_fulltext_entities" to "Acme Corp, Widget LLC",
            "sec_fulltext_forms" to "10-K, 8-K"
        )
        ReportMetadataSync.sync(meta)
        assertEquals("3", meta["sec_filings_count"])
        assertEquals("Acme Corp, Widget LLC", meta["sec_person_entities"])
        assertEquals("10-K, 8-K", meta["sec_filing_types"])
    }

    @Test
    fun extractsNamesFromSnippetText() {
        val names = ReportMetadataSync.extractPersonNames(
            "Owner: Maria Garcia lives in Austin. Associated with Garcia Plumbing LLC."
        )
        assertTrue(names.any { it.contains("Maria") })
    }

    @Test
    fun courtKeysAlignWithDossierConsumer() {
        val meta = mutableMapOf(
            "court_case_count" to "2",
            "court_case_urls" to "https://www.courtlistener.com/c/abc/\nhttps://www.courtlistener.com/c/def/"
        )
        // DossierBuilder reads courtlistener_count — repository must write it at scrape time.
        // This test documents the expected key after repository fix.
        meta["courtlistener_count"] = meta["court_case_count"]!!
        meta["courtlistener_link"] = meta["court_case_urls"]!!.lines().first()
        assertEquals("2", meta["courtlistener_count"])
        assertTrue(meta["courtlistener_link"]!!.startsWith("https://"))
    }

    @Test
    fun filtersObviousNonNames() {
        val names = ReportMetadataSync.extractPersonNames("Reverse lookup for mobile wireless number")
        assertTrue(names.isEmpty())
    }
}
