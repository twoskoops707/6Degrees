package com.twoskoops707.sixdegrees.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.twoskoops707.sixdegrees.data.local.OsintDatabase
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val db = OsintDatabase.getDatabase(app)

    val reports: LiveData<List<OsintReportEntity>> =
        db.reportDao().getAllReportsFlow().asLiveData()

    fun loadHistory() {}

    fun deleteReport(report: OsintReportEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.reportDao().deleteReport(report)
        }
    }

    fun restoreReport(report: OsintReportEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.reportDao().insertReport(report)
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            db.reportDao().deleteAllReports()
        }
    }
}
