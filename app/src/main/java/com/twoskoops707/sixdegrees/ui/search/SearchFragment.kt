package com.twoskoops707.sixdegrees.ui.search

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    private var pendingImageUri: Uri? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val uri = pendingImageUri ?: return@registerForActivityResult
            binding.searchInput.setText(uri.toString())
            binding.searchInputLayout.hint = "Image captured"
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            binding.searchInput.setText(uri.toString())
            binding.searchInputLayout.hint = "Image selected"
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString("searchType")?.let { type -> setChipForType(type) }

        binding.btnWizard.setOnClickListener {
            findNavController().navigate(R.id.action_search_to_wizard)
        }

        binding.chipGroupType.setOnCheckedStateChangeListener { _, checkedIds ->
            val isImage = checkedIds.contains(R.id.chip_image)
            binding.searchInputLayout.hint = if (isImage) "Tap camera or gallery below" else getString(R.string.search_hint)
            if (isImage) {
                binding.searchInput.isFocusable = false
                binding.searchInput.isFocusableInTouchMode = false
                showImageButtons()
            } else {
                binding.searchInput.isFocusable = true
                binding.searchInput.isFocusableInTouchMode = true
                hideImageButtons()
            }
        }

        binding.searchButton.setOnClickListener {
            val type = getSelectedSearchType()
            val query = binding.searchInput.text?.toString()?.trim() ?: ""
            if (type == "image") {
                if (query.isBlank()) {
                    Toast.makeText(requireContext(), "Capture or select an image first", Toast.LENGTH_SHORT).show()
                } else {
                    navigateToProgress(query, "image")
                }
            } else if (query.isNotBlank()) {
                binding.searchInputLayout.error = null
                navigateToProgress(query, type)
            } else {
                binding.searchInputLayout.error = "Enter something to search"
            }
        }

        recentAdapter = RecentSearchAdapter { report ->
            val searchType = try {
                val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                moshi.adapter<Map<String, String>>(type).fromJson(report.companiesJson)?.get("search_type") ?: "person"
            } catch (_: Exception) { "person" }
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

    private fun navigateToProgress(query: String, type: String) {
        findNavController().navigate(
            R.id.action_search_to_progress,
            Bundle().apply {
                putString("query", query)
                putString("type", type)
            }
        )
    }

    private fun showImageButtons() {
        if (binding.root.findViewWithTag<View>("img_buttons") != null) return
        val ctx = requireContext()
        val btnCamera = com.google.android.material.button.MaterialButton(ctx).apply {
            tag = "img_buttons"
            text = "Camera"
            setIconResource(R.drawable.ic_camera_black_24dp)
            setOnClickListener { launchCamera() }
        }
        val btnGallery = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "Gallery"
            setIconResource(R.drawable.ic_gallery_black_24dp)
            setOnClickListener { galleryLauncher.launch("image/*") }
        }
        val container = binding.imageButtonsContainer
        container.visibility = View.VISIBLE
        container.addView(btnCamera)
        container.addView(btnGallery)
    }

    private fun hideImageButtons() {
        binding.imageButtonsContainer.visibility = View.GONE
        binding.imageButtonsContainer.removeAllViews()
    }

    private fun launchCamera() {
        val imgFile = File(requireContext().cacheDir, "sixdegrees_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", imgFile)
        pendingImageUri = uri
        cameraLauncher.launch(uri)
    }

    private fun getSelectedSearchType(): String {
        return when (binding.chipGroupType.checkedChipId) {
            R.id.chip_email -> "email"
            R.id.chip_phone -> "phone"
            R.id.chip_username -> "username"
            R.id.chip_ip -> "ip"
            R.id.chip_company -> "company"
            R.id.chip_image -> "image"
            else -> "person"
        }
    }

    private fun setChipForType(type: String) {
        val chipId = when (type) {
            "email" -> R.id.chip_email
            "phone" -> R.id.chip_phone
            "username" -> R.id.chip_username
            "ip" -> R.id.chip_ip
            "company" -> R.id.chip_company
            "image" -> R.id.chip_image
            else -> R.id.chip_person
        }
        binding.chipGroupType.check(chipId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
