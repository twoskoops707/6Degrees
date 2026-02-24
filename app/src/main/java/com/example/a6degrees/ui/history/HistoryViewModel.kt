package com.example.a6degrees.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.a6degrees.data.local.entity.OsintReportEntity
import com.example.a6degrees.data.repository.OsintRepository
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = OsintRepository(app)

    private val _reports = MutableLiveData<List<OsintReportEntity>>(emptyList())
    val reports: LiveData<List<OsintReportEntity>> = _reports

    fun loadHistory() {
        viewModelScope.launch {
            _reports.value = repository.getRecentReports(50)
        }
    }
}
