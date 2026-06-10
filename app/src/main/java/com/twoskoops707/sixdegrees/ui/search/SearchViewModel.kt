package com.twoskoops707.sixdegrees.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import com.twoskoops707.sixdegrees.data.repository.OsintRepository
import com.twoskoops707.sixdegrees.domain.SubjectIntakeParser
import com.twoskoops707.sixdegrees.domain.SubjectSearchOrchestrator
import com.twoskoops707.sixdegrees.domain.model.SubjectProfile
import kotlinx.coroutines.launch

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val reportId: String) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

enum class InvestigationIntent(val key: String) {
    FIRST_DATE("first_date"),
    MEETING_NEW("meeting_new"),
    VERIFY_IDENTITY("verify_identity"),
    FRAUD("fraud")
}

enum class IntakeSearchType(val key: String) {
    PERSON("scan"),
    COMPANY("company"),
    VEHICLE("vehicle"),
    DOMAIN("domain"),
    PHONE("phone"),
    EMAIL("email"),
    USERNAME("username"),
    PHOTO("image")
}

data class IntakeForm(
    val searchType: IntakeSearchType = IntakeSearchType.PERSON,
    val freeform: String = "",
    val phone: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val username: String = "",
    val city: String = "",
    val state: String = "",
    val address: String = "",
    val company: String = "",
    val companyDomain: String = "",
    val vehicleVin: String = "",
    val domainIp: String = "",
    val imageUri: String? = null,
    val intent: InvestigationIntent? = null
)

data class IntakeValidation(
    val isValid: Boolean,
    val message: String = ""
)

data class IntakeQueryResult(
    val query: String,
    val type: String,
    val displayLabel: String,
    val intent: InvestigationIntent?
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = OsintRepository(app)

    private val _searchState = MutableLiveData<SearchUiState>(SearchUiState.Idle)
    val searchState: LiveData<SearchUiState> = _searchState

    private val _recentSearches = MutableLiveData<List<OsintReportEntity>>(emptyList())
    val recentSearches: LiveData<List<OsintReportEntity>> = _recentSearches

    fun loadRecentSearches() {
        viewModelScope.launch {
            _recentSearches.value = repository.getRecentReports(10)
        }
    }

    fun resetState() {
        _searchState.value = SearchUiState.Idle
    }

    fun countSources(form: IntakeForm): Int {
        val profile = resolveProfile(form)
        val type = resolveSearchType(form, profile)
        val phase = SubjectSearchOrchestrator.resolvePhase(type, 1, profile)
        return SubjectSearchOrchestrator.estimatedSourceCount(type, phase, profile)
    }

    fun validate(form: IntakeForm): IntakeValidation {
        val valid = when (form.searchType) {
            IntakeSearchType.PERSON -> {
                form.freeform.isNotBlank() ||
                    form.phone.isNotBlank() ||
                    form.firstName.isNotBlank() ||
                    form.lastName.isNotBlank() ||
                    form.email.isNotBlank() ||
                    form.username.isNotBlank() ||
                    form.city.isNotBlank() ||
                    form.state.isNotBlank() ||
                    form.address.isNotBlank() ||
                    form.imageUri != null ||
                    SubjectIntakeParser.looksLikeFreeform(form.freeform)
            }
            IntakeSearchType.COMPANY -> form.company.isNotBlank() || form.companyDomain.isNotBlank()
            IntakeSearchType.VEHICLE -> form.vehicleVin.trim().length >= 11
            IntakeSearchType.DOMAIN -> form.domainIp.isNotBlank()
            IntakeSearchType.PHONE -> form.phone.isNotBlank()
            IntakeSearchType.EMAIL -> form.email.isNotBlank()
            IntakeSearchType.USERNAME -> form.username.isNotBlank()
            IntakeSearchType.PHOTO -> form.imageUri != null
        }
        return if (valid) {
            IntakeValidation(isValid = true)
        } else {
            IntakeValidation(
                isValid = false,
                message = when (form.searchType) {
                    IntakeSearchType.VEHICLE -> "Enter a valid 11+ character VIN"
                    IntakeSearchType.PHOTO -> "Add a photo to search"
                    else -> "Add at least one detail to start"
                }
            )
        }
    }

    fun buildQuery(form: IntakeForm): IntakeQueryResult? {
        if (!validate(form).isValid) return null

        return when (form.searchType) {
            IntakeSearchType.PHOTO -> {
                val uri = form.imageUri ?: return null
                IntakeQueryResult(
                    query = uri,
                    type = "image",
                    displayLabel = "Photo search",
                    intent = form.intent
                )
            }
            IntakeSearchType.COMPANY -> buildCompanyQuery(form)
            IntakeSearchType.VEHICLE -> buildVehicleQuery(form)
            IntakeSearchType.DOMAIN -> buildDomainQuery(form)
            IntakeSearchType.PHONE -> buildPhoneQuery(form)
            IntakeSearchType.EMAIL -> buildEmailQuery(form)
            IntakeSearchType.USERNAME -> buildUsernameQuery(form)
            IntakeSearchType.PERSON -> buildPersonQuery(form)
        }
    }

    private fun buildCompanyQuery(form: IntakeForm): IntakeQueryResult {
        val parts = mutableListOf<String>()
        form.company.trim().takeIf { it.isNotBlank() }?.let { parts.add("name=$it") }
        form.companyDomain.trim().takeIf { it.isNotBlank() }?.let { parts.add("domain=$it") }
        form.intent?.let { parts.add("intent=${it.key}") }
        val label = form.company.ifBlank { form.companyDomain }.ifBlank { "Company report" }
        return IntakeQueryResult(parts.joinToString("|"), "company", label, form.intent)
    }

    private fun buildVehicleQuery(form: IntakeForm): IntakeQueryResult {
        val vin = form.vehicleVin.trim().uppercase().filter { it.isLetterOrDigit() }
        val parts = mutableListOf("name=$vin")
        form.intent?.let { parts.add("intent=${it.key}") }
        return IntakeQueryResult(parts.joinToString("|"), "vehicle", vin, form.intent)
    }

    private fun buildDomainQuery(form: IntakeForm): IntakeQueryResult {
        val target = form.domainIp.trim()
        val key = if (target.contains('.') && !target.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) "domain" else "ip"
        val parts = mutableListOf("$key=$target")
        form.intent?.let { parts.add("intent=${it.key}") }
        return IntakeQueryResult(parts.joinToString("|"), if (key == "ip") "ip" else "domain", target, form.intent)
    }

    private fun buildPhoneQuery(form: IntakeForm): IntakeQueryResult {
        val phone = normalizePhone(form.phone)
        val parts = mutableListOf("phone=$phone")
        form.intent?.let { parts.add("intent=${it.key}") }
        return IntakeQueryResult(parts.joinToString("|"), "phone", phone, form.intent)
    }

    private fun buildEmailQuery(form: IntakeForm): IntakeQueryResult {
        val email = form.email.trim()
        val parts = mutableListOf("email=$email")
        form.intent?.let { parts.add("intent=${it.key}") }
        return IntakeQueryResult(parts.joinToString("|"), "email", email, form.intent)
    }

    private fun buildUsernameQuery(form: IntakeForm): IntakeQueryResult {
        val username = form.username.trim().removePrefix("@")
        val parts = mutableListOf("username=$username")
        form.intent?.let { parts.add("intent=${it.key}") }
        return IntakeQueryResult(parts.joinToString("|"), "username", "@$username", form.intent)
    }

    private fun buildPersonQuery(form: IntakeForm): IntakeQueryResult {
        val profile = resolveProfile(form)
        val imageUri = form.imageUri
        val parts = mutableListOf<String>()
        if (profile.name.isNotBlank()) parts.add("name=${profile.name}")
        if (profile.phone.isNotBlank()) parts.add("phone=${profile.phone}")
        if (profile.email.isNotBlank()) parts.add("email=${profile.email}")
        if (profile.username.isNotBlank()) parts.add("username=${profile.username}")
        if (profile.city.isNotBlank()) parts.add("city=${profile.city}")
        if (profile.state.isNotBlank()) parts.add("state=${profile.state}")
        if (profile.address.isNotBlank()) parts.add("address=${profile.address}")
        if (imageUri != null) parts.add("image=$imageUri")
        form.intent?.let { parts.add("intent=${it.key}") }

        val locationLabel = listOf(profile.address, profile.city, profile.state)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        val displayLabel = buildString {
            val primary = profile.name.ifBlank {
                profile.email.ifBlank { profile.username.ifBlank { profile.phone } }
            }
            append(primary)
            if (locationLabel.isNotBlank()) append(" — $locationLabel")
        }.ifBlank { "Background report" }

        return IntakeQueryResult(
            query = parts.joinToString("|"),
            type = resolveSearchType(form, profile),
            displayLabel = displayLabel,
            intent = form.intent
        )
    }

    private fun resolveProfile(form: IntakeForm): SubjectProfile {
        val freeformSource = form.freeform.ifBlank { form.phone }
        val fromForm = SubjectProfile(
            name = listOf(form.firstName, form.lastName).filter { it.isNotBlank() }.joinToString(" "),
            firstName = form.firstName,
            lastName = form.lastName,
            city = form.city,
            state = form.state,
            address = form.address,
            phone = normalizePhone(form.phone),
            email = form.email.trim(),
            username = form.username.trim()
        )
        if (SubjectIntakeParser.looksLikeFreeform(freeformSource)) {
            val parsed = SubjectIntakeParser.parseFreeformText(freeformSource)
            return SubjectIntakeParser.mergeWithForm(parsed, mapOf(
                "name" to fromForm.name,
                "firstName" to fromForm.firstName,
                "lastName" to fromForm.lastName,
                "city" to fromForm.city,
                "state" to fromForm.state,
                "address" to fromForm.address,
                "email" to fromForm.email,
                "username" to fromForm.username
            ))
        }
        return fromForm
    }

    private fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9+]"), "")
        return if (digits.length == 10 || digits.length == 11) digits.takeLast(10) else raw.trim()
    }

    private fun resolveSearchType(form: IntakeForm, profile: SubjectProfile): String {
        if (form.searchType != IntakeSearchType.PERSON) return form.searchType.key
        val hasName = profile.name.isNotBlank() || form.firstName.isNotBlank() || form.lastName.isNotBlank()
        val fieldCount = listOf(
            hasName, profile.phone.isNotBlank(), profile.email.isNotBlank(),
            profile.username.isNotBlank(), profile.city.isNotBlank(), profile.state.isNotBlank(),
            profile.address.isNotBlank(), form.imageUri != null
        ).count { it }
        return when {
            fieldCount <= 1 && profile.phone.isNotBlank() && !hasName -> "phone"
            fieldCount <= 1 && profile.email.isNotBlank() && !hasName -> "email"
            fieldCount <= 1 && profile.username.isNotBlank() && !hasName -> "username"
            else -> "scan"
        }
    }
}
