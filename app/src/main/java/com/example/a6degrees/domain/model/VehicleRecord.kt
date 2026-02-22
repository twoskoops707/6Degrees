package com.example.a6degrees.domain.model

data class VehicleRecord(
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
    val violations: List<Violation>
)

data class Violation(
    val date: String,
    val description: String,
    val fineAmount: Double?,
    val status: String // pending, paid, dismissed
)