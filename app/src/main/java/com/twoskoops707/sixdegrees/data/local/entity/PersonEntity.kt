package com.twoskoops707.sixdegrees.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey
    val id: String,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val emailAddress: String?,
    val phoneNumber: String?,
    val dateOfBirth: String?,
    val addressesJson: String, // Store as JSON string
    val employmentHistoryJson: String, // Store as JSON string
    val socialProfilesJson: String, // Store as JSON string
    val aliasesJson: String, // Store as JSON string
    val nationalitiesJson: String, // Store as JSON string
    val gender: String?,
    val profileImageUrl: String?
)