package com.twoskoops707.sixdegrees.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.twoskoops707.sixdegrees.data.repository.OsintRepository
import com.twoskoops707.sixdegrees.data.repository.SearchProgressEvent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchProgressViewModel(
    app: Application,
    val query: String,
    val type: String,
    val round: Int = 1,
    val displayQuery: String = ""
) : AndroidViewModel(app) {

    private val repository = OsintRepository(app)

    /**
     * Live search events. `replay = 1` so that a fragment re-subscribing after a
     * configuration change redelivers the most recent event (phase label, last
     * source status, etc.) without restarting the search.
     */
    private val _events = MutableSharedFlow<SearchProgressEvent>(replay = 1, extraBufferCapacity = 512)
    val events: SharedFlow<SearchProgressEvent> = _events.asSharedFlow()

    /**
     * Survives view destruction. Holds the aggregate UI state the fragment needs to
     * rebuild its list after rotation without re-running the search.
     */
    data class ProgressState(
        val hitCount: Int = 0,
        val checkedCount: Int = 0,
        val searchStartMs: Long = System.currentTimeMillis(),
        val completedReportId: String? = null,
        val partialReportId: String? = null,
        val searchComplete: Boolean = false,
        val searchFailed: Boolean = false,
        val failureMessage: String? = null,
        val pendingCandidatesJson: String? = null,
        val pendingCandidatesRound: Int = 1,
        val sourceRows: List<SourceRow> = emptyList()
    )

    data class SourceRow(
        val source: String,
        val state: State,
        val detail: String = ""
    ) {
        enum class State { CHECKING, FOUND, NOT_FOUND, FAILED, BLOCKED, SKIPPED }
    }

    private val _state = MutableStateFlow(ProgressState(searchStartMs = System.currentTimeMillis()))
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    private var started = false

    fun startSearch() {
        if (started) return
        started = true
        val handler = CoroutineExceptionHandler { _, throwable ->
            viewModelScope.launch {
                val msg = throwable.message ?: "Unexpected error"
                _events.emit(SearchProgressEvent.Failed("Search", msg))
                _state.value = _state.value.copy(searchFailed = true, failureMessage = msg)
            }
        }
        viewModelScope.launch(Dispatchers.IO + handler) {
            try {
                repository.searchWithProgress(query, type, round).collect { event ->
                    applyEventToState(event)
                    _events.emit(event)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unexpected error"
                _events.emit(SearchProgressEvent.Failed("Search", msg))
                _state.value = _state.value.copy(searchFailed = true, failureMessage = msg)
            }
        }
    }

    private fun applyEventToState(event: SearchProgressEvent) {
        val current = _state.value
        when (event) {
            is SearchProgressEvent.Checking -> {
                if (current.sourceRows.none { it.source == event.source }) {
                    _state.value = current.copy(
                        sourceRows = current.sourceRows + SourceRow(event.source, SourceRow.State.CHECKING)
                    )
                }
            }
            is SearchProgressEvent.Found -> {
                _state.value = current.copy(
                    hitCount = current.hitCount + 1,
                    checkedCount = current.checkedCount + 1,
                    sourceRows = updateRow(current.sourceRows, event.source, SourceRow.State.FOUND, event.detail)
                )
            }
            is SearchProgressEvent.NotFound -> {
                _state.value = current.copy(
                    checkedCount = current.checkedCount + 1,
                    sourceRows = updateRow(current.sourceRows, event.source, SourceRow.State.NOT_FOUND)
                )
            }
            is SearchProgressEvent.Failed -> {
                if (event.source == "Search") {
                    _state.value = current.copy(searchFailed = true, failureMessage = event.reason)
                } else {
                    _state.value = current.copy(
                        checkedCount = current.checkedCount + 1,
                        sourceRows = updateRow(current.sourceRows, event.source, SourceRow.State.FAILED, event.reason)
                    )
                }
            }
            is SearchProgressEvent.Blocked -> {
                _state.value = current.copy(
                    checkedCount = current.checkedCount + 1,
                    sourceRows = updateRow(current.sourceRows, event.source, SourceRow.State.BLOCKED, event.reason)
                )
            }
            is SearchProgressEvent.Skipped -> {
                _state.value = current.copy(
                    checkedCount = current.checkedCount + 1,
                    sourceRows = updateRow(current.sourceRows, event.source, SourceRow.State.SKIPPED, event.reason)
                )
            }
            is SearchProgressEvent.PartialResultsReady -> {
                _state.value = current.copy(partialReportId = event.reportId)
            }
            is SearchProgressEvent.CandidatesReady -> {
                _state.value = current.copy(
                    completedReportId = event.reportId,
                    pendingCandidatesRound = event.round,
                    pendingCandidatesJson = com.squareup.moshi.Moshi.Builder()
                        .build()
                        .adapter(List::class.java)
                        .toJson(event.candidates)
                )
            }
            is SearchProgressEvent.Complete -> {
                _state.value = current.copy(
                    searchComplete = true,
                    completedReportId = event.reportId
                )
            }
            is SearchProgressEvent.PhaseUpdate,
            is SearchProgressEvent.BrowserToolsReady -> {
                // No state change needed — fragment reads phase from event stream.
            }
        }
    }

    private fun updateRow(
        rows: List<SourceRow>,
        source: String,
        state: SourceRow.State,
        detail: String = ""
    ): List<SourceRow> {
        val idx = rows.indexOfFirst { it.source == source }
        return if (idx != -1) {
            rows.toMutableList().also { it[idx] = it[idx].copy(state = state, detail = detail) }
        } else {
            rows + SourceRow(source, state, detail)
        }
    }

    class Factory(
        private val app: Application,
        private val query: String,
        private val type: String,
        private val round: Int = 1,
        private val displayQuery: String = ""
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchProgressViewModel(app, query, type, round, displayQuery) as T
    }
}
