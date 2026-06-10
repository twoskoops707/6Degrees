package com.twoskoops707.sixdegrees.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectSearchOrchestratorTest {

    @Test
    fun shouldRunScraper_allowsDarkWebInDiscoveryForSafetyIntent() {
        assertTrue(
            SubjectSearchOrchestrator.shouldRunScraper(
                scraperName = "DarkSearch",
                phase = SearchPhase.CANDIDATE_DISCOVERY,
                intent = "first_date",
                activeCategories = setOf("darknet")
            )
        )
    }

    @Test
    fun shouldRunScraper_stillHonorsPresetCategories() {
        assertFalse(
            SubjectSearchOrchestrator.shouldRunScraper(
                scraperName = "DarkSearch",
                phase = SearchPhase.CANDIDATE_DISCOVERY,
                intent = "first_date",
                activeCategories = setOf("person")
            )
        )
    }
}
