package com.twoskoops707.sixdegrees.ui.candidates

import androidx.lifecycle.ViewModel
import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CandidateSelectionViewModel : ViewModel() {

    private val _selected = MutableStateFlow<Set<Int>>(emptySet())
    val selected: StateFlow<Set<Int>> = _selected

    fun toggleSelection(index: Int, maxAllowed: Int) {
        val current = _selected.value.toMutableSet()
        if (current.contains(index)) {
            current.remove(index)
        } else {
            if (current.size < maxAllowed) {
                current.add(index)
            } else {
                current.clear()
                current.add(index)
            }
        }
        _selected.value = current
    }

    fun buildRefinedQuery(candidates: List<CandidateProfile>): String {
        val parts = mutableListOf<String>()
        val chosen = _selected.value.sorted().mapNotNull { candidates.getOrNull(it) }
        if (chosen.isEmpty()) return ""
        val primary = chosen.first()

        if (primary.isCompany) {
            if (primary.name.isNotBlank()) parts.add("name=${primary.name}")
            if (!primary.domain.isNullOrBlank()) parts.add("domain=${primary.domain}")
            if (primary.location.isNotBlank()) parts.add("location=${primary.location}")
        } else {
            if (primary.name.isNotBlank()) parts.add("name=${primary.name}")
            primary.phones.firstOrNull()?.let { parts.add("phone=$it") }
            if (primary.location.isNotBlank()) {
                val locParts = primary.location.split(",").map { it.trim() }
                if (locParts.size >= 2) {
                    parts.add("city=${locParts[0]}")
                    parts.add("state=${locParts[1]}")
                } else {
                    parts.add("city=${primary.location}")
                }
            }
            if (primary.address.isNotBlank()) parts.add("address=${primary.address}")
            if (chosen.size > 1) {
                val secondary = chosen[1]
                if (secondary.name.isNotBlank() && secondary.name != primary.name) {
                    parts.add("relatives=${secondary.name}")
                }
            }
        }
        return parts.joinToString("|")
    }
}
