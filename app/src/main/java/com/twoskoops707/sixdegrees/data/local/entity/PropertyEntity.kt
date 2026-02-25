package com.twoskoops707.sixdegrees.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "properties")
data class PropertyEntity(
    @PrimaryKey
    val id: String,
    val addressJson: String, // Store as JSON string
    val propertyType: String,
    val estimatedValue: Double?,
    val squareFootage: Int?,
    val bedrooms: Int?,
    val bathrooms: Int?,
    val yearBuilt: Int?,
    val lotSize: Double?,
    val ownerId: String,
    val ownerName: String,
    val ownershipType: String
)