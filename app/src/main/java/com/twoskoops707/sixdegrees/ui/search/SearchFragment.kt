package com.twoskoops707.sixdegrees.ui.search

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentSearchBinding
import com.twoskoops707.sixdegrees.ui.common.InvestigationPipelineView
import com.twoskoops707.sixdegrees.ui.common.InvestigationStep
import java.io.File

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var recentAdapter: RecentSearchAdapter
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private var currentType = "person"
    private var pendingImageUri: Uri? = null
    private var attachedImageUri: Uri? = null

    private fun updateQueryCounter() {
        val b = _binding ?: return
        if (currentType != "person") {
            b.tvQueryCounter.text = when (currentType) {
                "username" -> "3 sources"
                "domain", "ip" -> "5 sources"
                "email" -> "4 sources"
                "phone" -> "3 sources"
                "company" -> "6 sources"
                else -> "2 sources"
            }
            return
        }
        val baseCount = 18
        val hasPhone = b.inputPhone.text?.isNotBlank() == true
        val hasEmail = b.inputEmail.text?.isNotBlank() == true
        val hasUsername = b.inputUsername.text?.isNotBlank() == true
        val total = baseCount + (if (hasPhone) 1 else 0) + (if (hasEmail) 1 else 0) + (if (hasUsername) 1 else 0)
        b.tvQueryCounter.text = "$total sources"
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            attachedImageUri = pendingImageUri
            binding.tvImageAttached.text = "Photo captured"
            binding.tvImageAttached.visibility = View.VISIBLE
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraInternal()
        else Toast.makeText(requireContext(), "Camera permission required for photo search", Toast.LENGTH_SHORT).show()
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attachedImageUri = uri
            binding.tvImageAttached.text = "Photo selected"
            binding.tvImageAttached.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        InvestigationPipelineView.bind(binding.root, InvestigationStep.INTAKE)

        setupEntityTypeSelector()

        val counterWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateQueryCounter() }
        }
        binding.inputPhone.addTextChangedListener(counterWatcher)
        binding.inputEmail.addTextChangedListener(counterWatcher)
        binding.inputUsername.addTextChangedListener(counterWatcher)

        binding.btnCamera.setOnClickListener { launchCamera() }
        binding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }

        binding.searchButton.setOnClickListener { doSearch() }
        binding.btnClearFields.setOnClickListener { clearCurrentForm() }
        binding.btnWebHub.setOnClickListener {
            val bundle = Bundle().also { it.putString("query", buildQueryPreview()) }
            findNavController().navigate(R.id.action_search_to_osint_resources, bundle)
        }

        val imeSearch = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        listOf(
            binding.inputDomainValue, binding.inputEmailValue, binding.inputPhoneValue,
            binding.inputUsernameValue, binding.inputVehicleValue, binding.inputWifiValue
        ).forEach { field ->
            field.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == imeSearch) { doSearch(); true } else false
            }
        }

        recentAdapter = RecentSearchAdapter { report ->
            val searchType = try {
                val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                moshi.adapter<Map<String, String>>(type).fromJson(report.companiesJson)?.get("search_type") ?: "comprehensive"
            } catch (_: Exception) { "comprehensive" }
            findNavController().navigate(
                R.id.action_search_to_results,
                Bundle().apply {
                    putString("searchQuery", report.searchQuery)
                    putString("searchType", searchType)
                    putString("reportId", report.id)
                }
            )
        }
        binding.recentSearchesRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
        }

        viewModel.recentSearches.observe(viewLifecycleOwner) { reports ->
            recentAdapter.submitList(reports)
            binding.noRecentSearches.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loadRecentSearches()
    }

    private fun setupEntityTypeSelector() {
        fun selectType(type: String) {
            currentType = type
            attachedImageUri = null
            binding.tvImageAttached.visibility = View.GONE

            val forms = mapOf(
                "person" to binding.formPerson,
                "company" to binding.formCompany,
                "domain" to binding.formDomain,
                "email" to binding.formEmail,
                "phone" to binding.formPhone,
                "username" to binding.formUsername,
                "vehicle" to binding.formVehicle,
                "wifi" to binding.formWifi
            )
            forms.forEach { (t, form) -> form.visibility = if (t == type) View.VISIBLE else View.GONE }

            val ctx = requireContext()
            val accentColor = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, "SearchFragment")
            val strokeInactive = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutline, "SearchFragment")
            val bgInactive = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant, "SearchFragment")
            val textInactive = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, "SearchFragment")
            val textOnPrimary = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnPrimary, "SearchFragment")

            val cards = mapOf(
                "person" to binding.cardTypePerson,
                "company" to binding.cardTypeCompany,
                "domain" to binding.cardTypeDomain,
                "email" to binding.cardTypeEmail,
                "phone" to binding.cardTypePhone,
                "username" to binding.cardTypeUsername,
                "vehicle" to binding.cardTypeVehicle,
                "wifi" to binding.cardTypeWifi
            )
            cards.forEach { (t, card) ->
                val active = t == type
                card.radius = if (active) 20f * resources.displayMetrics.density else 20f * resources.displayMetrics.density
                card.setCardBackgroundColor(if (active) accentColor else bgInactive)
                card.strokeColor = if (active) android.graphics.Color.TRANSPARENT else strokeInactive
                card.strokeWidth = if (active) 0 else (1 * resources.displayMetrics.density).toInt()
                val textView = card.getChildAt(0) as? android.widget.TextView
                textView?.setTextColor(if (active) textOnPrimary else textInactive)
            }
            updateQueryCounter()
        }

        binding.cardTypePerson.setOnClickListener { selectType("person") }
        binding.cardTypeCompany.setOnClickListener { selectType("company") }
        binding.cardTypeDomain.setOnClickListener { selectType("domain") }
        binding.cardTypeEmail.setOnClickListener { selectType("email") }
        binding.cardTypePhone.setOnClickListener { selectType("phone") }
        binding.cardTypeUsername.setOnClickListener { selectType("username") }
        binding.cardTypeVehicle.setOnClickListener { selectType("vehicle") }
        binding.cardTypeWifi.setOnClickListener { selectType("wifi") }

        selectType("person")
    }

    private fun doSearch() {
        when (currentType) {
            "person" -> {
                val firstName = binding.inputFirstName.text?.toString()?.trim() ?: ""
                val middleInitial = binding.inputMiddleInitial.text?.toString()?.trim() ?: ""
                val lastName = binding.inputLastName.text?.toString()?.trim() ?: ""
                val phone = binding.inputPhone.text?.toString()?.trim() ?: ""
                val phone2 = binding.inputPhone2.text?.toString()?.trim() ?: ""
                val phone3 = binding.inputPhone3.text?.toString()?.trim() ?: ""
                val email = binding.inputEmail.text?.toString()?.trim() ?: ""
                val username = binding.inputUsername.text?.toString()?.trim() ?: ""
                val city = binding.inputCity.text?.toString()?.trim() ?: ""
                val state = binding.inputState.text?.toString()?.trim() ?: ""
                val dob = binding.inputDob.text?.toString()?.trim() ?: ""
                val address = binding.inputAddress.text?.toString()?.trim() ?: ""
                val relatives = binding.inputRelatives.text?.toString()?.trim() ?: ""
                val context = binding.inputContext.text?.toString()?.trim() ?: ""
                val imageUri = attachedImageUri

                val hasAnyField = firstName.isNotBlank() || lastName.isNotBlank() || phone.isNotBlank() ||
                        email.isNotBlank() || username.isNotBlank() || imageUri != null

                if (!hasAnyField) {
                    Toast.makeText(requireContext(), "Enter at least one field to search", Toast.LENGTH_SHORT).show()
                    return
                }

                if (imageUri != null && !hasNameOrContact(firstName, lastName, phone, email, username)) {
                    navigateToProgress(imageUri.toString(), "image")
                    return
                }

                val parts = mutableListOf<String>()
                val nameParts = listOfNotNull(
                    firstName.takeIf { it.isNotBlank() },
                    middleInitial.takeIf { it.isNotBlank() }?.let { "$it." },
                    lastName.takeIf { it.isNotBlank() }
                )
                val fullName = nameParts.joinToString(" ")
                if (fullName.isNotBlank()) parts.add("name=$fullName")
                if (phone.isNotBlank()) parts.add("phone=$phone")
                if (phone2.isNotBlank()) parts.add("phone2=$phone2")
                if (phone3.isNotBlank()) parts.add("phone3=$phone3")
                if (email.isNotBlank()) parts.add("email=$email")
                if (username.isNotBlank()) parts.add("username=$username")
                if (dob.isNotBlank()) parts.add("dob=$dob")
                if (address.isNotBlank()) parts.add("address=$address")
                if (relatives.isNotBlank()) parts.add("relatives=$relatives")
                if (context.isNotBlank()) parts.add("context=$context")
                if (imageUri != null) parts.add("image=$imageUri")
                if (city.isNotBlank()) parts.add("city=$city")
                if (state.isNotBlank()) parts.add("state=$state")
                val locationLabel = listOf(city, state).filter { it.isNotBlank() }.joinToString(", ")
                val cleanLabel = buildString {
                    append(fullName.ifBlank { email.ifBlank { phone.ifBlank { username } } })
                    if (locationLabel.isNotBlank()) append(" — $locationLabel")
                }
                navigateToProgress(parts.joinToString("|"), "scan", cleanLabel)
            }

            "company" -> {
                val name = binding.inputCompanyName.text?.toString()?.trim() ?: ""
                val domain = binding.inputCompanyDomain.text?.toString()?.trim() ?: ""
                val city = binding.inputCompanyCity.text?.toString()?.trim() ?: ""

                val query = when {
                    name.isNotBlank() -> if (city.isNotBlank()) "$name|city=$city" else name
                    domain.isNotBlank() -> if (city.isNotBlank()) "$domain|city=$city" else domain
                    else -> {
                        Toast.makeText(requireContext(), "Enter a company name or domain", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                if (domain.isNotBlank() && name.isNotBlank()) {
                    navigateToProgress("$name|domain=$domain${if (city.isNotBlank()) "|city=$city" else ""}", "company")
                } else {
                    navigateToProgress(query, "company")
                }
            }

            "domain" -> {
                val value = binding.inputDomainValue.text?.toString()?.trim() ?: ""
                if (value.isBlank()) {
                    Toast.makeText(requireContext(), "Enter a domain or IP address", Toast.LENGTH_SHORT).show()
                    return
                }
                navigateToProgress(value, if (value.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) "ip" else "domain")
            }

            "email" -> {
                val value = binding.inputEmailValue.text?.toString()?.trim() ?: ""
                if (value.isBlank()) {
                    Toast.makeText(requireContext(), "Enter an email address", Toast.LENGTH_SHORT).show()
                    return
                }
                navigateToProgress(value, "email")
            }

            "phone" -> {
                val value = binding.inputPhoneValue.text?.toString()?.trim() ?: ""
                if (value.isBlank()) {
                    Toast.makeText(requireContext(), "Enter a phone number", Toast.LENGTH_SHORT).show()
                    return
                }
                navigateToProgress(value, "phone")
            }

            "username" -> {
                val value = binding.inputUsernameValue.text?.toString()?.trim() ?: ""
                if (value.isBlank()) {
                    Toast.makeText(requireContext(), "Enter a username", Toast.LENGTH_SHORT).show()
                    return
                }
                navigateToProgress(value, "username")
            }

            "vehicle" -> {
                val value = binding.inputVehicleValue.text?.toString()?.trim() ?: ""
                if (value.isBlank()) {
                    Toast.makeText(requireContext(), "Enter a VIN or vehicle info", Toast.LENGTH_SHORT).show()
                    return
                }
                val searchType = if (value.length == 17 && value.all { it.isLetterOrDigit() }) "vin" else "vehicle"
                navigateToProgress(value, searchType)
            }

            "wifi" -> {
                val value = binding.inputWifiValue.text?.toString()?.trim() ?: ""
                if (value.isBlank()) {
                    Toast.makeText(requireContext(), "Enter a WiFi SSID or MAC address", Toast.LENGTH_SHORT).show()
                    return
                }
                val searchType = if (value.matches(Regex("[0-9A-Fa-f:]{17}"))) "mac" else "wifi"
                navigateToProgress(value, searchType)
            }
        }
    }

    private fun buildQueryPreview(): String {
        return when (currentType) {
            "person" -> listOf(
                binding.inputFirstName.text, binding.inputLastName.text,
                binding.inputCity.text, binding.inputState.text
            ).filter { !it.isNullOrBlank() }.joinToString(" ")
            "email" -> binding.inputEmailValue.text?.toString() ?: ""
            "phone" -> binding.inputPhoneValue.text?.toString() ?: ""
            "username" -> binding.inputUsernameValue.text?.toString() ?: ""
            "domain", "ip" -> binding.inputDomainValue.text?.toString() ?: ""
            "company" -> binding.inputCompanyName.text?.toString() ?: ""
            "vehicle" -> binding.inputVehicleValue.text?.toString() ?: ""
            else -> ""
        }.trim()
    }

    private fun clearCurrentForm() {
        when (currentType) {
            "person" -> {
                binding.inputFirstName.text?.clear()
                binding.inputMiddleInitial.text?.clear()
                binding.inputLastName.text?.clear()
                binding.inputPhone.text?.clear()
                binding.inputPhone2.text?.clear()
                binding.inputPhone3.text?.clear()
                binding.inputEmail.text?.clear()
                binding.inputUsername.text?.clear()
                binding.inputCity.text?.clear()
                binding.inputState.text?.clear()
                binding.inputDob.text?.clear()
                binding.inputAddress.text?.clear()
                binding.inputRelatives.text?.clear()
                binding.inputContext.text?.clear()
                attachedImageUri = null
                binding.tvImageAttached.visibility = View.GONE
            }
            "company" -> {
                binding.inputCompanyName.text?.clear()
                binding.inputCompanyDomain.text?.clear()
                binding.inputCompanyCity.text?.clear()
            }
            "domain" -> binding.inputDomainValue.text?.clear()
            "email" -> binding.inputEmailValue.text?.clear()
            "phone" -> binding.inputPhoneValue.text?.clear()
            "username" -> binding.inputUsernameValue.text?.clear()
            "vehicle" -> binding.inputVehicleValue.text?.clear()
            "wifi" -> binding.inputWifiValue.text?.clear()
        }
    }

    private fun hasNameOrContact(first: String, last: String, phone: String, email: String, username: String) =
        first.isNotBlank() || last.isNotBlank() || phone.isNotBlank() || email.isNotBlank() || username.isNotBlank()

    private fun navigateToProgress(query: String, type: String, displayName: String = "") {
        findNavController().navigate(
            R.id.action_search_to_progress,
            Bundle().apply {
                putString("query", query)
                putString("type", type)
                putString("searchQuery", displayName.ifBlank {
                    query.split("|").joinToString(", ") { part ->
                        val eq = part.indexOf('=')
                        if (eq != -1) part.substring(eq + 1).trim() else part.trim()
                    }.replace(Regex(",\\s*,"), ",").trim().trimEnd(',')
                })
            }
        )
    }

    private fun launchCamera() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
                launchCameraInternal()
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraInternal() {
        val imgFile = File(requireContext().cacheDir, "sixdegrees_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", imgFile)
        pendingImageUri = uri
        cameraLauncher.launch(uri)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
