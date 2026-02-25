package com.twoskoops707.sixdegrees.ui.results

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import com.twoskoops707.sixdegrees.data.local.entity.PersonEntity
import com.twoskoops707.sixdegrees.data.repository.OsintRepository
import kotlinx.coroutines.launch

data class ResultsUiState(
    val isLoading: Boolean = false,
    val report: OsintReportEntity? = null,
    val person: PersonEntity? = null,
    val error: String? = null
)

class ResultsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = OsintRepository(app)

    private val _state = MutableLiveData<ResultsUiState>(ResultsUiState(isLoading = true))
    val state: LiveData<ResultsUiState> = _state

    fun loadReport(reportId: String) {
        _state.value = ResultsUiState(isLoading = true)
        viewModelScope.launch {
            val report = repository.getReportById(reportId)
            if (report == null) {
                _state.value = ResultsUiState(error = "Report not found.")
                return@launch
            }
            val person = report.personId?.let { repository.getPersonById(it) }
            _state.value = ResultsUiState(isLoading = false, report = report, person = person)
        }
    }
}
