package com.twoskoops707.sixdegrees.domain.model

data class Company(
    val id: String,
    val name: String,
    val legalName: String?,
    val description: String?,
    val industry: String?,
    val employeeCount: Int?,
    val foundedYear: Int?,
    val headquartersAddress: Address?,
    val website: String?,
    val linkedinUrl: String?,
    val twitterUrl: String?,
    val facebookUrl: String?,
    val ownershipStructure: List<Ownership>,
    val subsidiaries: List<Subsidiary>,
    val financialSummary: FinancialSummary?,
    val technologyStack: List<Technology>
)

data class Ownership(
    val ownerId: String,
    val ownerName: String,
    val ownershipPercentage: Double,
    val ownerType: String // Individual, Corporation, etc.
)

data class Subsidiary(
    val name: String,
    val relationship: String // subsidiary, acquired, etc.
)

data class Technology(
    val name: String,
    val category: String, // Framework, Language, Cloud, etc.
    val confidence: Double // 0.0 to 1.0
)