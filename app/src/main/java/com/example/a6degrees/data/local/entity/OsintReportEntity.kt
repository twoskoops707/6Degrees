package com.example.a6degrees.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "osint_reports")
data class OsintReportEntity(
    @PrimaryKey
    val id: String,
    val searchQuery: String,
    val generatedAt: Date,
    val personId: String?, // Reference to PersonEntity
    val companiesJson: String, // Store as JSON string
    val propertiesJson: String, // Store as JSON string
    val vehicleRecordsJson: String, // Store as JSON string
    val financialSummaryJson: String?, // Store as JSON string
    val confidenceScore: Double,
    val sourcesJson: String // Store as JSON string
)