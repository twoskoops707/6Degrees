package com.example.a6degrees.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.a6degrees.data.local.entity.OsintReportEntity
import com.example.a6degrees.data.repository.OsintRepository
import kotlinx.coroutines.launch

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val reportId: String) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = OsintRepository(app)

    private val _searchState = MutableLiveData<SearchUiState>(SearchUiState.Idle)
    val searchState: LiveData<SearchUiState> = _searchState

    private val _recentSearches = MutableLiveData<List<OsintReportEntity>>(emptyList())
    val recentSearches: LiveData<List<OsintReportEntity>> = _recentSearches

    fun loadRecentSearches() {
        viewModelScope.launch {
            _recentSearches.value = repository.getRecentReports(10)
        }
    }

    fun search(query: String, type: String) {
        if (query.isBlank()) return
        _searchState.value = SearchUiState.Loading
        viewModelScope.launch {
            val result = when (type) {
                "company" -> repository.searchCompany(query)
                else -> repository.searchPerson(query)
            }
            _searchState.value = result.fold(
                onSuccess = { reportId -> SearchUiState.Success(reportId) },
                onFailure = { e -> SearchUiState.Error(e.message ?: "Unknown error") }
            )
        }
    }

    fun resetState() {
        _searchState.value = SearchUiState.Idle
    }
}
