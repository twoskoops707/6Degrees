package com.twoskoops707.sixdegrees.domain.model

/**
 * Normalized subject built from intake fields or locked after candidate selection.
 */
data class SubjectProfile(
    val name: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val city: String = "",
    val state: String = "",
    val phone: String = "",
    val email: String = "",
    val username: String = "",
    val employer: String = "",
    val address: String = "",
    val age: String = "",
    val dob: String = "",
    val photoUri: String = "",
    val photoUrl: String? = null,
    val photoUrls: List<String> = emptyList(),
    val profileUrl: String? = null,
    val intent: String = "",
    val locked: Boolean = false,
    val candidateId: String? = null
) {
    val location: String
        get() = listOf(city, state).filter { it.isNotBlank() }.joinToString(", ")

    /** Terms used for dark-web index searches after subject lock (or during deep dive). */
    fun darkWebSearchTerms(): List<String> = buildList {
        if (name.isNotBlank()) add(name.trim())
        email.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        phone.takeIf { it.isNotBlank() }?.let { add(it.replace(Regex("[^0-9+]"), "")) }
        username.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
    }.distinct().filter { it.length >= 3 }

    fun toQueryString(): String {
        val parts = mutableListOf<String>()
        if (name.isNotBlank()) parts.add("name=$name")
        if (phone.isNotBlank()) parts.add("phone=$phone")
        if (email.isNotBlank()) parts.add("email=$email")
        if (username.isNotBlank()) parts.add("username=$username")
        if (city.isNotBlank()) parts.add("city=$city")
        if (state.isNotBlank()) parts.add("state=$state")
        if (address.isNotBlank()) parts.add("address=$address")
        if (age.isNotBlank()) parts.add("age=$age")
        if (dob.isNotBlank()) parts.add("dob=$dob")
        if (photoUri.isNotBlank()) parts.add("image=$photoUri")
        if (intent.isNotBlank()) parts.add("intent=$intent")
        if (locked) parts.add("locked=true")
        if (!candidateId.isNullOrBlank()) parts.add("candidateId=$candidateId")
        return parts.joinToString("|")
    }

    companion object {
        fun fromFields(fields: Map<String, String>): SubjectProfile {
            val name = fields["name"]?.trim().orEmpty()
            val parts = name.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val intent = fields["intent"]?.trim().orEmpty()
            val employer = fields["employer"]?.trim().orEmpty()
                .ifBlank {
                    if (intent.startsWith("employer=", ignoreCase = true)) {
                        intent.removePrefix("employer=").trim()
                    } else ""
                }
            return SubjectProfile(
                name = name,
                firstName = parts.firstOrNull().orEmpty(),
                lastName = if (parts.size > 1) parts.last() else "",
                city = fields["city"]?.trim().orEmpty(),
                state = fields["state"]?.trim().orEmpty(),
                phone = fields["phone"]?.trim().orEmpty(),
                email = fields["email"]?.trim().orEmpty(),
                username = fields["username"]?.trim().orEmpty(),
                employer = employer,
                address = fields["address"]?.trim().orEmpty(),
                age = fields["age"]?.trim().orEmpty(),
                dob = fields["dob"]?.trim().orEmpty(),
                photoUri = fields["image"]?.trim().orEmpty(),
                intent = fields["intent"]?.trim().orEmpty(),
                locked = fields["locked"]?.equals("true", ignoreCase = true) == true,
                candidateId = fields["candidateId"]?.trim()?.takeIf { it.isNotBlank() }
            )
        }

        fun fromCandidate(candidate: CandidateProfile, base: SubjectProfile = SubjectProfile()): SubjectProfile {
            val locParts = candidate.location.split(",").map { it.trim() }
            val city = locParts.firstOrNull().orEmpty()
            val state = if (locParts.size > 1) locParts.last() else base.state
            val nameParts = candidate.name.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val allPhotos = buildList {
                candidate.photoUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
                addAll(candidate.photoUrls.filter { it.isNotBlank() })
            }.distinct()
            return base.copy(
                name = candidate.name.ifBlank { base.name },
                firstName = nameParts.firstOrNull() ?: base.firstName,
                lastName = nameParts.lastOrNull() ?: base.lastName,
                city = city.ifBlank { base.city },
                state = state.ifBlank { base.state },
                phone = candidate.phones.firstOrNull() ?: base.phone,
                email = candidate.email ?: base.email,
                address = candidate.address.ifBlank { base.address },
                age = candidate.age.ifBlank { base.age },
                dob = candidate.dob ?: base.dob,
                photoUrl = allPhotos.firstOrNull(),
                photoUrls = allPhotos,
                profileUrl = candidate.profileUrl,
                locked = true,
                candidateId = candidate.id
            )
        }
    }
}
