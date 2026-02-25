package com.twoskoops707.sixdegrees.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.twoskoops707.sixdegrees.data.local.OsintDatabase
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val db = OsintDatabase.getDatabase(app)

    val reports: LiveData<List<OsintReportEntity>> =
        db.reportDao().getAllReportsFlow().asLiveData()

    fun loadHistory() {}
}
