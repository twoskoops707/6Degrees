package com.twoskoops707.sixdegrees.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsintAiReportTest {

    @Test
    fun enforceFactsOnlyRedactsUnsourcedPhone() {
        val report = OsintAiReport(
            executiveSummary = "Subject uses (555) 123-9999 which is not in records.",
            keyFindings = emptyList(),
            confidence = "medium",
            confidenceRationale = "",
            falsePositiveNotes = emptyList(),
            nextSteps = emptyList(),
            provider = "test"
        )
        val allowed = setOf("(555) 111-2222")
        val cleaned = OsintAiReport.enforceFactsOnly(report, allowed)
        assertFalse(cleaned.executiveSummary.contains("123-9999"))
        assertTrue(cleaned.executiveSummary.contains("[redacted phone]"))
    }

    @Test
    fun enforceFactsOnlyKeepsAllowedPhone() {
        val report = OsintAiReport(
            executiveSummary = "Confirmed number (555) 111-2222 appears in public records.",
            keyFindings = emptyList(),
            confidence = "high",
            confidenceRationale = "",
            falsePositiveNotes = emptyList(),
            nextSteps = emptyList(),
            provider = "test"
        )
        val allowed = setOf("(555) 111-2222")
        val cleaned = OsintAiReport.enforceFactsOnly(report, allowed)
        assertTrue(cleaned.executiveSummary.contains("(555) 111-2222"))
    }
}
