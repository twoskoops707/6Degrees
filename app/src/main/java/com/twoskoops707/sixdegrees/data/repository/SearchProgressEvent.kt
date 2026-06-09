package com.twoskoops707.sixdegrees.data.repository

import com.twoskoops707.sixdegrees.domain.model.CandidateProfile

sealed class SearchProgressEvent {
    data class PhaseUpdate(val phase: String, val detail: String = "") : SearchProgressEvent()
    data class Checking(val source: String) : SearchProgressEvent()
    data class Found(val source: String, val detail: String) : SearchProgressEvent()
    data class NotFound(val source: String) : SearchProgressEvent()
    data class Blocked(val source: String, val reason: String = "Cloudflare / Bot protection") : SearchProgressEvent()
    /** Source skipped because domain is in [BlockedSourceCache] (24h TTL). */
    data class Skipped(val source: String, val reason: String = "blocked") : SearchProgressEvent()
    data class Failed(val source: String, val reason: String = "") : SearchProgressEvent()
    data class CandidatesReady(
        val candidates: List<CandidateProfile>,
        val reportId: String,
        val round: Int,
        val autoSelect: Boolean = false,
        val refinedQuery: String = ""
    ) : SearchProgressEvent()
    data class BrowserToolsReady(
        val categories: LinkedHashMap<String, List<Pair<String, String>>>
    ) : SearchProgressEvent()
    data class PartialResultsReady(val reportId: String, val hitCount: Int) : SearchProgressEvent()
    data class Complete(val reportId: String, val hitCount: Int) : SearchProgressEvent()
}
