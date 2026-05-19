package com.twoskoops707.sixdegrees.data

import android.content.Context
import android.content.SharedPreferences

data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val country: String = "US",
    val company: String = "",
    val website: String = "",
    val ccNumber: String = "",
    val ccExpiry: String = "",
    val ccCvv: String = "",
    val ccName: String = ""
) {
    val fullName: String get() = "$firstName $lastName".trim()

    fun fieldsForApi(apiName: String): List<Pair<String, String>> {
        val base = mutableListOf(
            "First Name" to firstName,
            "Last Name" to lastName,
            "Email" to email,
            "Phone" to phone,
            "Street Address" to address,
            "City" to city,
            "State" to state,
            "ZIP / Postal Code" to zip,
            "Country" to country
        )
        if (company.isNotBlank()) base.add("Company" to company)
        if (website.isNotBlank()) base.add("Website" to website)
        if (apiName in PAID_APIS && ccNumber.isNotBlank()) {
            base.add("Card Number" to ccNumber)
            base.add("Expiry" to ccExpiry)
            base.add("CVV" to ccCvv)
            base.add("Name on Card" to ccName)
        }
        return base.filter { it.second.isNotBlank() }
    }

    companion object {
        val PAID_APIS = setOf("Pipl", "FullContact", "Whitepages Pro", "Twilio", "FaceCheck.id", "IntelX", "Dehashed", "PimEyes")
    }
}

class UserProfileManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    fun save(profile: UserProfile) {
        prefs.edit().apply {
            putString("first_name", profile.firstName)
            putString("last_name", profile.lastName)
            putString("email", profile.email)
            putString("phone", profile.phone)
            putString("address", profile.address)
            putString("city", profile.city)
            putString("state", profile.state)
            putString("zip", profile.zip)
            putString("country", profile.country)
            putString("company", profile.company)
            putString("website", profile.website)
            putString("cc_number", profile.ccNumber)
            putString("cc_expiry", profile.ccExpiry)
            putString("cc_cvv", profile.ccCvv)
            putString("cc_name", profile.ccName)
            apply()
        }
    }

    fun load(): UserProfile = UserProfile(
        firstName = prefs.getString("first_name", "") ?: "",
        lastName = prefs.getString("last_name", "") ?: "",
        email = prefs.getString("email", "") ?: "",
        phone = prefs.getString("phone", "") ?: "",
        address = prefs.getString("address", "") ?: "",
        city = prefs.getString("city", "") ?: "",
        state = prefs.getString("state", "") ?: "",
        zip = prefs.getString("zip", "") ?: "",
        country = prefs.getString("country", "US") ?: "US",
        company = prefs.getString("company", "") ?: "",
        website = prefs.getString("website", "") ?: "",
        ccNumber = prefs.getString("cc_number", "") ?: "",
        ccExpiry = prefs.getString("cc_expiry", "") ?: "",
        ccCvv = prefs.getString("cc_cvv", "") ?: "",
        ccName = prefs.getString("cc_name", "") ?: ""
    )

    fun hasProfile(): Boolean = prefs.getString("email", "").isNullOrBlank().not()
            || prefs.getString("first_name", "").isNullOrBlank().not()
}
