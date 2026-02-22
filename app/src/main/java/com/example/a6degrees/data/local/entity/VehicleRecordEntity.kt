package com.example.a6degrees.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_records")
data class VehicleRecordEntity(
    @PrimaryKey
    val id: String,
    val vin: String,
    val licensePlate: String,
    val state: String,
    val make: String,
    val model: String,
    val year: Int,
    val color: String,
    val registrationExpiration: String,
    val registeredOwner: String,
    val registrationStatus: String,
    val violationsJson: String // Store as JSON string
)