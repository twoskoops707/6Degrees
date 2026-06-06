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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class SearchProgressViewModel(
    app: Application,
    val query: String,
    val type: String,
    val round: Int = 1,
    val displayQuery: String = ""
) : AndroidViewModel(app) {

    private val repository = OsintRepository(app)

    private val _events = MutableSharedFlow<SearchProgressEvent>(replay = 0, extraBufferCapacity = 512)
    val events: SharedFlow<SearchProgressEvent> = _events

    private var started = false

    fun startSearch() {
        if (started) return
        started = true
        val handler = CoroutineExceptionHandler { _, _ -> }
        viewModelScope.launch(Dispatchers.IO + handler) {
            try {
                repository.searchWithProgress(query, type, round).collect { event ->
                    _events.emit(event)
                }
            } catch (_: Exception) {
                // search flow handles its own errors
            }
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
