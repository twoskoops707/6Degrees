package com.twoskoops707.sixdegrees.ui.search

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            attachedImageUri = pendingImageUri
            binding.tvImageAttached.text = "Photo captured"
            binding.tvImageAttached.visibility = View.VISIBLE
        }
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

        setupEntityTypeSelector()

        binding.btnCamera.setOnClickListener { launchCamera() }
        binding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }

        binding.searchButton.setOnClickListener { doSearch() }
        binding.btnClearFields.setOnClickListener { clearCurrentForm() }

        binding.inputDomainValue.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                doSearch(); true
            } else false
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
        val colorPrimary = com.google.android.material.R.attr.colorPrimary
        val bgDark = "#0A0F1E"

        fun selectType(type: String) {
            currentType = type
            attachedImageUri = null
            binding.tvImageAttached.visibility = View.GONE

            val personActive = type == "person"
            val companyActive = type == "company"
            val domainActive = type == "domain"

            binding.formPerson.visibility = if (personActive) View.VISIBLE else View.GONE
            binding.formCompany.visibility = if (companyActive) View.VISIBLE else View.GONE
            binding.formDomain.visibility = if (domainActive) View.VISIBLE else View.GONE

            val tv = android.util.TypedValue()
            requireContext().theme.resolveAttribute(colorPrimary, tv, true)
            val accentColor = tv.data
            val bgColor = android.graphics.Color.parseColor(bgDark)

            binding.cardTypePerson.setCardBackgroundColor(if (personActive) accentColor else bgColor)
            binding.cardTypeCompany.setCardBackgroundColor(if (companyActive) accentColor else bgColor)
            binding.cardTypeDomain.setCardBackgroundColor(if (domainActive) accentColor else bgColor)

            val strokeInactive = ContextCompat.getColor(requireContext(), R.color.border)
            binding.cardTypePerson.strokeColor = if (personActive) android.graphics.Color.TRANSPARENT else strokeInactive
            binding.cardTypeCompany.strokeColor = if (companyActive) android.graphics.Color.TRANSPARENT else strokeInactive
            binding.cardTypeDomain.strokeColor = if (domainActive) android.graphics.Color.TRANSPARENT else strokeInactive
        }

        binding.cardTypePerson.setOnClickListener { selectType("person") }
        binding.cardTypeCompany.setOnClickListener { selectType("company") }
        binding.cardTypeDomain.setOnClickListener { selectType("domain") }

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
                navigateToProgress(parts.joinToString("|"), "comprehensive")
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
        }
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
            "domain" -> {
                binding.inputDomainValue.text?.clear()
            }
        }
    }

    private fun hasNameOrContact(first: String, last: String, phone: String, email: String, username: String) =
        first.isNotBlank() || last.isNotBlank() || phone.isNotBlank() || email.isNotBlank() || username.isNotBlank()

    private fun navigateToProgress(query: String, type: String) {
        findNavController().navigate(
            R.id.action_search_to_progress,
            Bundle().apply {
                putString("query", query)
                putString("type", type)
            }
        )
    }

    private fun launchCamera() {
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
