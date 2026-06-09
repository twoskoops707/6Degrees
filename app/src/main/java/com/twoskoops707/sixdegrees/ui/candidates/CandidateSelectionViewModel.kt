package com.twoskoops707.sixdegrees.ui.candidates

import androidx.lifecycle.ViewModel
import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import com.twoskoops707.sixdegrees.domain.model.SubjectProfile

class CandidateSelectionViewModel : ViewModel() {

    /** Build a locked deep-dive query from the chosen candidate. */
    fun buildLockedQuery(candidate: CandidateProfile): String =
        SubjectProfile.fromCandidate(candidate).toQueryString()

    /** @deprecated Use [buildLockedQuery] which returns SubjectProfile-based query */
    fun buildRefinedQuery(candidates: List<CandidateProfile>, baseFields: Map<String, String> = emptyMap()): String {
        val primary = candidates.firstOrNull() ?: return ""
        return SubjectProfile.fromCandidate(primary, SubjectProfile.fromFields(baseFields)).toQueryString()
    }

    fun buildLockedSubjectProfile(candidate: CandidateProfile, baseFields: Map<String, String> = emptyMap()) =
        SubjectProfile.fromCandidate(candidate, SubjectProfile.fromFields(baseFields))

    /** Format raw search query for display (e.g. name=John|city=Austin → John Smith · Austin, TX). */
    fun formatDisplayQuery(raw: String): String {
        if (raw.isBlank()) return ""
        if (!raw.contains("=")) return raw.trim()
        val fields = raw.split("|").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq == -1) null else part.substring(0, eq).trim().lowercase() to part.substring(eq + 1).trim()
        }.toMap()

        val name = listOfNotNull(
            fields["firstname"] ?: fields["first"],
            fields["lastname"] ?: fields["last"]
        ).joinToString(" ").ifBlank { fields["name"] ?: "" }

        val city = fields["city"] ?: ""
        val state = fields["state"] ?: ""
        val location = when {
            city.isNotBlank() && state.isNotBlank() -> "$city, $state"
            city.isNotBlank() -> city
            state.isNotBlank() -> state
            fields["location"]?.isNotBlank() == true -> fields["location"]!!
            else -> ""
        }

        return listOf(name, location).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
