package com.twoskoops707.sixdegrees.domain.model

data class CandidateProfile(
    val name: String,
    val age: String,
    val location: String,
    val phones: List<String>,
    val address: String,
    val source: String,
    val confidence: Float,
    val photoUrl: String? = null,
    val relatives: List<String> = emptyList(),
    val profileUrl: String? = null,
    // Company-specific
    val isCompany: Boolean = false,
    val logoUrl: String? = null,
    val domain: String? = null,
    val industry: String? = null,
    // OSINT - Identity & Legal
    val fullDOB: String? = null,
    val ssnLast4: String? = null,
    val akasNicknames: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val politicalAffiliation: String? = null,
    // OSINT - Digital & Contact
    val allEmails: List<String> = emptyList(),
    val usernames: List<String> = emptyList(),
    val socialProfiles: List<String> = emptyList(),
    // OSINT - Employment & Education
    val employmentHistory: List<String> = emptyList(),
    val educationHistory: List<String> = emptyList(),
    // OSINT - Financial & Property
    val propertyRecords: List<String> = emptyList(),
    val financialFlags: List<String> = emptyList(),  // bankruptcies, liens, judgments
    // OSINT - Vehicles
    val vehicleInfo: List<String> = emptyList(),
    // OSINT - Legal
    val legalRecords: List<String> = emptyList(),    // arrests, court cases, sanctions
    val associatedPersons: List<String> = emptyList() // co-subjects from same household/record
)
