package com.twoskoops707.sixdegrees.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val legalName: String?,
    val description: String?,
    val industry: String?,
    val employeeCount: Int?,
    val foundedYear: Int?,
    val headquartersAddressJson: String?, // Store as JSON string
    val website: String?,
    val linkedinUrl: String?,
    val twitterUrl: String?,
    val facebookUrl: String?,
    val ownershipStructureJson: String, // Store as JSON string
    val subsidiariesJson: String, // Store as JSON string
    val financialSummaryJson: String?, // Store as JSON string
    val technologyStackJson: String // Store as JSON string
)