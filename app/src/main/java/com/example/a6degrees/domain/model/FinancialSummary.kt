package com.example.a6degrees.domain.model

data class FinancialSummary(
    val creditScore: Int?,
    val creditRating: String?,
    val totalAccounts: Int?,
    val delinquentAccounts: Int?,
    val bankruptcies: List<Bankruptcy>,
    val liens: List<Lien>,
    val judgments: List<Judgment>,
    val lastUpdated: String
)

data class Bankruptcy(
    val filingDate: String,
    val dischargeDate: String?,
    val chapter: String,
    val assets: Double?,
    val liabilities: Double?
)

data class Lien(
    val filingDate: String,
    val amount: Double,
    val creditor: String,
    val status: String
)

data class Judgment(
    val filingDate: String,
    val amount: Double,
    val plaintiff: String,
    val defendant: String,
    val status: String
)