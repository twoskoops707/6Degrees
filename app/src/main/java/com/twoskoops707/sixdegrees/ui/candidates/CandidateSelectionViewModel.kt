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
        val chosen = _selected.value.sorted().mapNotNull { candidates.getOrNull(it) }
        if (chosen.isEmpty()) return ""
        val primary = chosen.first()
        val parts = mutableListOf<String>()
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
        primary.dob?.takeIf { it.isNotBlank() }?.let { parts.add("dob=$it") }
        if (chosen.size > 1) {
            val alts = chosen.drop(1).filter { it.name.isNotBlank() && it.name != primary.name }
            if (alts.isNotEmpty()) parts.add("context=also check: ${alts.joinToString(", ") { it.name }}")
        }
        return parts.joinToString("|")
    }
}
