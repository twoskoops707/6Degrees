package com.example.a6degrees.domain.model

import java.util.Date

data class OsintReport(
    val id: String,
    val searchQuery: String,
    val generatedAt: Date,
    val person: Person?,
    val companies: List<Company>,
    val properties: List<Property>,
    val vehicleRecords: List<VehicleRecord>,
    val financialSummary: FinancialSummary?,
    val confidenceScore: Double, // 0.0 to 1.0 indicating reliability of information
    val sources: List<DataSource>
)

data class DataSource(
    val name: String,
    val url: String?,
    val retrievedAt: Date,
    val reliabilityScore: Double // 0.0 to 1.0 indicating source reliability
)