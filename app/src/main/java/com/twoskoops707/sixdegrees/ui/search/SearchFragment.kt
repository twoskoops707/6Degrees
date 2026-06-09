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
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.AppSettings
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

    private var pendingImageUri: Uri? = null
    private var attachedImageUri: Uri? = null
    private var selectedIntent: InvestigationIntent? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            attachedImageUri = pendingImageUri
            binding.tvImageAttached.text = getString(R.string.intake_photo_attached)
            binding.tvImageAttached.isVisible = true
            binding.chipPhoto.isChecked = true
            binding.cardPhoto.isVisible = true
            updateQueryCounter()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraInternal()
        else Toast.makeText(requireContext(), R.string.intake_camera_permission, Toast.LENGTH_SHORT).show()
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attachedImageUri = uri
            binding.tvImageAttached.text = getString(R.string.intake_photo_selected)
            binding.tvImageAttached.isVisible = true
            binding.chipPhoto.isChecked = true
            binding.cardPhoto.isVisible = true
            updateQueryCounter()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        InvestigationPipelineView.bind(binding.root, InvestigationStep.INTAKE)

        setupFieldChips()
        setupIntentChips()
        setupTextWatchers()
        setupActions()
        setupRecentSearches()
        updateQueryCounter()
        applyInvestigatorModeUi()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) applyInvestigatorModeUi()
    }

    private fun applyInvestigatorModeUi() {
        val investigator = AppSettings.isInvestigatorMode(requireContext())
        binding.intentSection.isVisible = investigator
        binding.tvQueryCounter.isVisible = investigator
        binding.btnWebHub.isVisible = investigator
        binding.pipelineInclude.root.isVisible = investigator
        if (!investigator) {
            binding.tvQueryCounter.text = getString(R.string.intake_sources_simple)
        } else {
            updateQueryCounter()
        }
    }

    private fun setupFieldChips() {
        val chipToCard = mapOf(
            binding.chipName to binding.cardName,
            binding.chipPhoto to binding.cardPhoto,
            binding.chipEmail to binding.cardEmail,
            binding.chipUsername to binding.cardUsername,
            binding.chipLocation to binding.cardLocation
        )

        chipToCard.forEach { (chip, card) ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                card.isVisible = isChecked
            }
        }
    }

    private fun setupIntentChips() {
        val intentChips = mapOf(
            binding.chipIntentFirstDate to InvestigationIntent.FIRST_DATE,
            binding.chipIntentMeeting to InvestigationIntent.MEETING_NEW,
            binding.chipIntentVerify to InvestigationIntent.VERIFY_IDENTITY,
            binding.chipIntentFraud to InvestigationIntent.FRAUD
        )

        intentChips.forEach { (chip, intent) ->
            chip.setOnCheckedChangeListener { button, isChecked ->
                if (isChecked) {
                    selectedIntent = intent
                    intentChips.keys.filter { it != button }.forEach { other ->
                        if (other.isChecked) other.isChecked = false
                    }
                } else if (selectedIntent == intent) {
                    selectedIntent = null
                }
            }
        }
    }

    private fun setupTextWatchers() {
        listOf(
            binding.inputPhone,
            binding.inputFirstName,
            binding.inputLastName,
            binding.inputEmail,
            binding.inputUsername,
            binding.inputCity,
            binding.inputState
        ).forEach { field ->
            field.doAfterTextChanged { updateQueryCounter() }
        }
    }

    private fun setupActions() {
        binding.btnCamera.setOnClickListener { launchCamera() }
        binding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.searchButton.setOnClickListener { startInvestigation() }
        binding.btnClearFields.setOnClickListener { clearForm() }
        binding.btnWebHub.setOnClickListener {
            val bundle = Bundle().also { it.putString("query", buildQueryPreview()) }
            findNavController().navigate(R.id.action_search_to_osint_resources, bundle)
        }

        binding.inputPhone.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            ) {
                startInvestigation()
                true
            } else false
        }
    }

    private fun setupRecentSearches() {
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
            binding.noRecentSearches.isVisible = reports.isEmpty()
        }

        viewModel.loadRecentSearches()
    }

    private fun collectForm(): IntakeForm = IntakeForm(
        phone = binding.inputPhone.text?.toString()?.trim().orEmpty(),
        firstName = binding.inputFirstName.text?.toString()?.trim().orEmpty(),
        lastName = binding.inputLastName.text?.toString()?.trim().orEmpty(),
        email = binding.inputEmail.text?.toString()?.trim().orEmpty(),
        username = binding.inputUsername.text?.toString()?.trim().orEmpty(),
        city = binding.inputCity.text?.toString()?.trim().orEmpty(),
        state = binding.inputState.text?.toString()?.trim().orEmpty(),
        imageUri = attachedImageUri?.toString(),
        intent = selectedIntent
    )

    private fun updateQueryCounter() {
        val count = viewModel.countSources(collectForm())
        binding.tvQueryCounter.text = getString(R.string.intake_sources_count, count)
    }

    private fun startInvestigation() {
        val form = collectForm()
        val validation = viewModel.validate(form)
        if (!validation.isValid) {
            Toast.makeText(requireContext(), validation.message, Toast.LENGTH_SHORT).show()
            return
        }

        val result = viewModel.buildQuery(form) ?: return
        findNavController().navigate(
            R.id.action_search_to_progress,
            Bundle().apply {
                putString("query", result.query)
                putString("type", result.type)
                putString("searchQuery", result.displayLabel)
                result.intent?.let { putString("intent", it.key) }
            }
        )
    }

    private fun buildQueryPreview(): String {
        val form = collectForm()
        return form.phone.ifBlank {
            listOf(form.firstName, form.lastName, form.city, form.state)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }
    }

    private fun clearForm() {
        binding.inputPhone.text?.clear()
        binding.inputFirstName.text?.clear()
        binding.inputLastName.text?.clear()
        binding.inputEmail.text?.clear()
        binding.inputUsername.text?.clear()
        binding.inputCity.text?.clear()
        binding.inputState.text?.clear()

        attachedImageUri = null
        binding.tvImageAttached.isVisible = false
        selectedIntent = null

        listOf(
            binding.chipName,
            binding.chipPhoto,
            binding.chipEmail,
            binding.chipUsername,
            binding.chipLocation,
            binding.chipIntentFirstDate,
            binding.chipIntentMeeting,
            binding.chipIntentVerify,
            binding.chipIntentFraud
        ).forEach { (it as Chip).isChecked = false }

        binding.cardName.isVisible = false
        binding.cardPhoto.isVisible = false
        binding.cardEmail.isVisible = false
        binding.cardUsername.isVisible = false
        binding.cardLocation.isVisible = false

        updateQueryCounter()
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
