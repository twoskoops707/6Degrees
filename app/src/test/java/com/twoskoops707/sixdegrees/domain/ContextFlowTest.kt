package com.twoskoops707.sixdegrees.domain

import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import com.twoskoops707.sixdegrees.domain.model.SubjectProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks down the intake "context window": freeform context (where they were met, hobbies,
 * distinguishing details) must survive parsing, the subject-profile query round-trip, and
 * candidate locking, and must be threaded into the secondary DDG sweep queries.
 */
class ContextFlowTest {

    private val candidate = CandidateProfile(
        id = "cand-1",
        name = "John Smith",
        age = "42",
        location = "Austin, TX",
        phones = listOf("5125550100"),
        address = "Austin, TX",
        source = "Web",
        confidence = 0.72f
    )

    @Test
    fun freeformKeepsContextAlongsideStructuredFields() {
        val fields = SubjectIntakeParser.parseToFields(
            "John Smith, Austin TX, met at the dog park, likes cycling"
        )
        assertEquals("name extracted", "John Smith", fields["name"])
        assertEquals("city extracted", "Austin", fields["city"])
        assertEquals("state extracted", "TX", fields["state"])
        val ctx = fields["context"].orEmpty().lowercase()
        assertTrue("context keeps met-where clue", ctx.contains("dog park"))
        assertTrue("context keeps hobby", ctx.contains("cycling"))
    }

    @Test
    fun contextSurvivesProfileQueryRoundTrip() {
        val profile = SubjectProfile.fromFields(
            mapOf("name" to "John Smith", "city" to "Austin", "context" to "met at the dog park, likes cycling")
        )
        val query = profile.toQueryString()
        assertTrue("context serialized into query", query.contains("context=met at the dog park"))

        val reparsed = SubjectProfile.fromFields(
            query.split("|").mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq == -1) null else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            }.toMap()
        )
        assertEquals("context survives round-trip", "met at the dog park, likes cycling", reparsed.context)
    }

    @Test
    fun contextSurvivesCandidateLock() {
        val base = SubjectProfile.fromFields(mapOf("name" to "John Smith", "context" to "met at the dog park"))
        val locked = SubjectProfile.fromCandidate(candidate, base)
        assertTrue("locked query carries context", locked.toQueryString().contains("context=met at the dog park"))
    }

    @Test
    fun darkWebTermsIncludeContext() {
        val profile = SubjectProfile.fromFields(
            mapOf("name" to "John Smith", "context" to "met at the dog park likes cycling")
        )
        val terms = profile.darkWebSearchTerms()
        assertTrue("dark-web terms include context phrase", terms.any { it.contains("dog park") })
    }

    @Test
    fun secondaryPassesAppendContextToEveryQuery() {
        val passes = SubjectSearchOrchestrator.secondaryDdgPassQueries(
            name = "John Smith",
            city = "Austin",
            state = "TX",
            phone = "",
            email = "",
            phase = SearchPhase.DEEP_INVESTIGATION,
            passIndex = 0,
            context = "likes cycling"
        )
        assertTrue("passes generated", passes.isNotEmpty())
        passes.forEach { (_, q) ->
            assertTrue("every query carries context: '$q'", q.contains("likes cycling"))
        }
    }

    @Test
    fun blankContextDoesNotAlterPasses() {
        val plain = SubjectSearchOrchestrator.secondaryDdgPassQueries(
            "John Smith", "Austin", "TX", "", "", SearchPhase.DEEP_INVESTIGATION, 0
        )
        val withBlank = SubjectSearchOrchestrator.secondaryDdgPassQueries(
            "John Smith", "Austin", "TX", "", "", SearchPhase.DEEP_INVESTIGATION, 0, ""
        )
        assertEquals("blank context leaves queries unchanged", plain, withBlank)
        assertFalse("no context leaked", withBlank.first().second.contains("null"))
    }

    @Test
    fun freeformExtractsDobAkaAndMiddleName() {
        val profile = SubjectIntakeParser.parseFreeformText(
            "John Michael Smith, aka Mike, born 01/15/1990, Austin TX"
        )
        assertEquals("full name kept", "John Michael Smith", profile.name)
        assertEquals("first name", "John", profile.firstName)
        assertEquals("middle name extracted", "Michael", profile.middleName)
        assertEquals("last name", "Smith", profile.lastName)
        assertEquals("aka extracted", "Mike", profile.aka)
        assertEquals("dob extracted", "01/15/1990", profile.dob)
        assertEquals("city extracted", "Austin", profile.city)
    }

    @Test
    fun dobAkaMiddleNameSurviveQueryRoundTrip() {
        val profile = SubjectProfile.fromFields(
            mapOf(
                "name" to "John Michael Smith",
                "middleName" to "Michael",
                "aka" to "Mike",
                "dob" to "01/15/1990"
            )
        )
        val reparsed = SubjectProfile.fromFields(
            profile.toQueryString().split("|").mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq == -1) null else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            }.toMap()
        )
        assertEquals("middle name survives", "Michael", reparsed.middleName)
        assertEquals("aka survives", "Mike", reparsed.aka)
        assertEquals("dob survives", "01/15/1990", reparsed.dob)
    }

    @Test
    fun darkWebTermsIncludeAka() {
        val profile = SubjectProfile.fromFields(
            mapOf("name" to "John Smith", "aka" to "Mike")
        )
        val terms = profile.darkWebSearchTerms()
        assertTrue("dark-web terms include aka", terms.any { it.equals("Mike", ignoreCase = true) })
    }

    @Test
    fun secondaryPassesAppendAkaToQueries() {
        val passes = SubjectSearchOrchestrator.secondaryDdgPassQueries(
            name = "John Smith",
            city = "Austin",
            state = "TX",
            phone = "",
            email = "",
            phase = SearchPhase.DEEP_INVESTIGATION,
            passIndex = 0,
            aka = "Mike"
        )
        assertTrue("passes generated", passes.isNotEmpty())
        passes.forEach { (_, q) ->
            assertTrue("every query carries aka: '$q'", q.contains("Mike"))
        }
    }

    @Test
    fun defaultSearchesRunForMinutesNotSeconds() {
        // The default (fast) mode must still run a real investigation — a person cannot
        // be found in seconds. Discovery >= 2 minutes, deep >= 4 minutes.
        assertTrue(
            "discovery floor too short: ${SubjectSearchOrchestrator.MIN_DISCOVERY_FAST_MS}",
            SubjectSearchOrchestrator.MIN_DISCOVERY_FAST_MS >= 120_000L
        )
        assertTrue(
            "deep floor too short: ${SubjectSearchOrchestrator.MIN_DEEP_FAST_MS}",
            SubjectSearchOrchestrator.MIN_DEEP_FAST_MS >= 240_000L
        )
        assertTrue(
            "secondary-pass threshold too short: ${SubjectSearchOrchestrator.SECONDARY_PASS_THRESHOLD_FAST_MS}",
            SubjectSearchOrchestrator.SECONDARY_PASS_THRESHOLD_FAST_MS >= 120_000L
        )
    }
}
