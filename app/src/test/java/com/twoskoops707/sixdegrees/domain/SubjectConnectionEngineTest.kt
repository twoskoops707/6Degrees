package com.twoskoops707.sixdegrees.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectConnectionEngineTest {

    @Test
    fun parsesEmploymentLines() {
        val meta = mapOf(
            "pipl_employment" to "Software Engineer at Acme Corp (Current)\nManager at OldCo LLC (until 2019)"
        )
        val jobs = SubjectConnectionEngine.parseEmployment(meta)
        assertEquals(2, jobs.size)
        assertTrue(jobs.any { it.companyName == "Acme Corp" && it.isCurrent })
        assertTrue(jobs.any { it.companyName == "OldCo LLC" && !it.isCurrent })
    }

    @Test
    fun infersCareerTransition() {
        val meta = mutableMapOf(
            "pipl_employment" to "VP Sales at NewCorp (Current)\nAnalyst at Legacy Inc (until 2018)",
            "person_name" to "Jane Doe"
        )
        SubjectConnectionEngine.sync(meta)
        assertTrue(meta["career_transition"]!!.contains("Legacy Inc"))
        assertTrue(meta["career_transition"]!!.contains("NewCorp"))
    }

    @Test
    fun buildsAssociateAndEmployerConnections() {
        val meta = mutableMapOf(
            "pipl_relatives" to "John Smith, Mary Smith",
            "pdl_company" to "Widget LLC",
            "pdl_job_title" to "CEO",
            "sec_person_entities" to "Widget LLC, Holdings Inc"
        )
        SubjectConnectionEngine.sync(meta)
        val edges = SubjectConnectionEngine.parseConnectionsFromJson(meta["connection_edges_json"]!!)
        assertTrue(edges.any { it.relation == "Associate" && it.target == "John Smith" })
        assertTrue(edges.any { it.relation == "Current employer" && it.target == "Widget LLC" })
        assertTrue(edges.any { it.relation == "SEC affiliation" && it.target == "Holdings Inc" })
    }

    @Test
    fun buildsTimelineFromJobsAndAddresses() {
        val meta = mutableMapOf(
            "pipl_employment" to "Engineer at Acme (Current)",
            "search_addresses" to "123 Main St, Austin, TX | 456 Oak Ave, Dallas, TX",
            "sec_filings_count" to "3",
            "sec_person_entities" to "Acme Corp"
        )
        SubjectConnectionEngine.sync(meta)
        val timeline = SubjectConnectionEngine.parseTimelineFromJson(meta["subject_timeline_json"]!!)
        assertTrue(timeline.any { it.kind == "employment" && it.title == "Acme" })
        assertTrue(timeline.any { it.kind == "location" })
        assertTrue(timeline.any { it.kind == "corporate" })
    }

    @Test
    fun collectsCandidateAndProfileImages() {
        val meta = mutableMapOf(
            "profile_photo_url" to "https://example.com/photo.jpg",
            "candidate_0_photo_urls" to "https://example.com/cand1.jpg|https://example.com/cand2.jpg"
        )
        SubjectConnectionEngine.sync(meta)
        assertTrue(meta["imagery_urls"]!!.contains("photo.jpg"))
        assertTrue(meta["imagery_urls"]!!.contains("cand1.jpg"))
    }

    @Test
    fun serializesEmploymentJson() {
        val meta = mapOf("pdl_job_title" to "Director", "pdl_company" to "BigCo")
        val json = SubjectConnectionEngine.employmentToJson(SubjectConnectionEngine.parseEmployment(meta))
        val parsed = SubjectConnectionEngine.parseEmploymentFromJson(json)
        assertEquals(1, parsed.size)
        assertEquals("BigCo", parsed[0].companyName)
        assertEquals("Director", parsed[0].jobTitle)
    }
}
