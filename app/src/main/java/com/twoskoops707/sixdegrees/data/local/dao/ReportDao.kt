package com.twoskoops707.sixdegrees.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import java.util.Date

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: OsintReportEntity)

    @Query("SELECT * FROM osint_reports WHERE id = :id")
    suspend fun getReportById(id: String): OsintReportEntity?

    @Query("SELECT * FROM osint_reports WHERE searchQuery LIKE :query ORDER BY generatedAt DESC")
    suspend fun searchReports(query: String): List<OsintReportEntity>

    @Query("SELECT * FROM osint_reports ORDER BY generatedAt DESC LIMIT :limit")
    suspend fun getRecentReports(limit: Int): List<OsintReportEntity>

    @Query("SELECT * FROM osint_reports WHERE generatedAt BETWEEN :startDate AND :endDate ORDER BY generatedAt DESC")
    suspend fun getReportsBetweenDates(startDate: Date, endDate: Date): List<OsintReportEntity>

    @Query("DELETE FROM osint_reports WHERE generatedAt < :beforeDate")
    suspend fun deleteReportsBefore(beforeDate: Date)

    @Query("DELETE FROM osint_reports")
    suspend fun deleteAllReports()
}