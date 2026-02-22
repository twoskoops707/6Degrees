package com.example.a6degrees.domain.model

data class Property(
    val id: String,
    val address: Address,
    val propertyType: String, // residential, commercial, land
    val estimatedValue: Double?,
    val squareFootage: Int?,
    val bedrooms: Int?,
    val bathrooms: Int?,
    val yearBuilt: Int?,
    val lotSize: Double?,
    val ownerId: String,
    val ownerName: String,
    val ownershipType: String // owned, rented, etc.
)