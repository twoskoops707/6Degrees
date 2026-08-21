package com.twoskoops707.sixdegrees.domain

import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import com.twoskoops707.sixdegrees.domain.model.SubjectProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the multi-select deep-search queue contract: each selected candidate is locked
 * into a query string (SubjectProfile.fromCandidate(...).toQueryString()), that string is
 * passed into the next search round, and parsing it back must yield a locked profile that
 * keeps the candidate's identity fields + the base search context.
 */
class CandidateLockChainTest {

    private val candidate = CandidateProfile(
        id = "cand-1",
        name = "John Smith",
        age = "42",
        location = "Austin, TX",
        phones = listOf("5125550100"),
        address = "Austin, TX",
        source = "Web",
        confidence = 0.72f,
        email = "john.smith@example.com"
    )

    @Test
    fun lockedQueryRoundTripsIntoLockedProfile() {
        val base = SubjectProfile.fromFields(mapOf("name" to "John Smith", "city" to "Austin", "state" to "TX"))
        val locked = SubjectProfile.fromCandidate(candidate, base)

        val query = locked.toQueryString()
        assertTrue("query should carry the candidate id", query.contains("candidateId=cand-1"))
        assertTrue("query should mark the subject locked", query.contains("locked=true"))

        // What the next round does: parse the query back into a profile. Key case is preserved
        // (matches SearchProgressFragment.parseDisplayFields, which does NOT lowercase keys).
        val reparsed = SubjectProfile.fromFields(
            query.split("|").mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq == -1) null else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            }.toMap()
        )

        assertTrue("reparsed profile must stay locked", reparsed.locked)
        assertEquals("candidate id survives", "cand-1", reparsed.candidateId)
        assertEquals("name survives", "John Smith", reparsed.name)
        assertEquals("phone survives", "5125550100", reparsed.phone)
        assertEquals("email survives", "john.smith@example.com", reparsed.email)
        assertEquals("age survives", "42", reparsed.age)
    }

    @Test
    fun candidateLockKeepsBaseGeoContextWhenCandidateLacksIt() {
        val candidateNoLoc = candidate.copy(location = "", address = "", phones = emptyList())
        val base = SubjectProfile.fromFields(mapOf("name" to "John Smith", "city" to "Austin", "state" to "TX"))
        val locked = SubjectProfile.fromCandidate(candidateNoLoc, base)

        val reparsed = SubjectProfile.fromFields(
            locked.toQueryString().split("|").mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq == -1) null else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            }.toMap()
        )

        assertEquals("base city preserved", "Austin", reparsed.city)
        assertEquals("base state preserved", "TX", reparsed.state)
    }
}
