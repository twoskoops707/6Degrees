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



data class IntakeForm(

    val phone: String = "",

    val firstName: String = "",

    val lastName: String = "",

    val email: String = "",

    val username: String = "",

    val city: String = "",

    val state: String = "",

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

        val hasAnyField = form.phone.isNotBlank() ||

            form.firstName.isNotBlank() ||

            form.lastName.isNotBlank() ||

            form.email.isNotBlank() ||

            form.username.isNotBlank() ||

            form.city.isNotBlank() ||

            form.state.isNotBlank() ||

            form.imageUri != null ||

            SubjectIntakeParser.looksLikeFreeform(form.phone)



        return if (hasAnyField) {

            IntakeValidation(isValid = true)

        } else {

            IntakeValidation(

                isValid = false,

                message = "Add at least one detail to start"

            )

        }

    }



    fun buildQuery(form: IntakeForm): IntakeQueryResult? {

        if (!validate(form).isValid) return null



        val imageUri = form.imageUri

        val profile = resolveProfile(form)

        val hasNameOrContact = profile.name.isNotBlank() ||

            profile.phone.isNotBlank() ||

            profile.email.isNotBlank() ||

            profile.username.isNotBlank()



        if (imageUri != null && !hasNameOrContact) {

            return IntakeQueryResult(

                query = imageUri,

                type = "image",

                displayLabel = "Photo search",

                intent = form.intent

            )

        }



        val parts = mutableListOf<String>()

        if (profile.name.isNotBlank()) parts.add("name=${profile.name}")

        if (profile.phone.isNotBlank()) parts.add("phone=${profile.phone}")

        if (profile.email.isNotBlank()) parts.add("email=${profile.email}")

        if (profile.username.isNotBlank()) parts.add("username=${profile.username}")

        if (profile.city.isNotBlank()) parts.add("city=${profile.city}")

        if (profile.state.isNotBlank()) parts.add("state=${profile.state}")

        if (imageUri != null) parts.add("image=$imageUri")

        form.intent?.let { parts.add("intent=${it.key}") }



        val locationLabel = listOf(profile.city, profile.state)

            .filter { it.isNotBlank() }

            .joinToString(", ")

        val displayLabel = buildString {

            append(profile.name.ifBlank { profile.email.ifBlank { profile.phone.ifBlank { profile.username } } })

            if (locationLabel.isNotBlank()) append(" — $locationLabel")

        }.ifBlank { profile.phone.ifBlank { "Investigation" } }



        val searchType = resolveSearchType(form, profile)



        return IntakeQueryResult(

            query = parts.joinToString("|"),

            type = searchType,

            displayLabel = displayLabel,

            intent = form.intent

        )

    }



    private fun resolveProfile(form: IntakeForm): SubjectProfile {

        val fromForm = SubjectProfile(

            name = listOf(form.firstName, form.lastName).filter { it.isNotBlank() }.joinToString(" "),

            firstName = form.firstName,

            lastName = form.lastName,

            city = form.city,

            state = form.state,

            phone = form.phone.replace(Regex("[^0-9+]"), "").let { digits ->

                if (digits.length == 10 || digits.length == 11) digits.takeLast(10) else form.phone.trim()

            },

            email = form.email.trim(),

            username = form.username.trim()

        )

        if (SubjectIntakeParser.looksLikeFreeform(form.phone)) {

            val parsed = SubjectIntakeParser.parseFreeformText(form.phone)

            return SubjectIntakeParser.mergeWithForm(parsed, mapOf(
                "name" to fromForm.name,
                "firstName" to fromForm.firstName,
                "lastName" to fromForm.lastName,
                "city" to fromForm.city,
                "state" to fromForm.state,
                "email" to fromForm.email,
                "username" to fromForm.username
            ))

        }

        return fromForm

    }



    private fun resolveSearchType(form: IntakeForm, profile: SubjectProfile): String {

        val hasName = profile.name.isNotBlank() || form.firstName.isNotBlank() || form.lastName.isNotBlank()

        return when {
            profile.phone.isNotBlank() && !hasName && profile.email.isBlank() && profile.username.isBlank() -> "phone"
            profile.email.isNotBlank() && !hasName && profile.phone.isBlank() && profile.username.isBlank() -> "email"
            profile.username.isNotBlank() && !hasName && profile.phone.isBlank() && profile.email.isBlank() -> "username"
            else -> "scan"
        }

    }

}

