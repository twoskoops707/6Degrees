package com.twoskoops707.sixdegrees.ui.candidates

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import coil.load
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentCandidateSelectionBinding
import com.twoskoops707.sixdegrees.databinding.ItemCandidateCardBinding
import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import com.twoskoops707.sixdegrees.ui.common.InvestigationPipelineView
import com.twoskoops707.sixdegrees.ui.common.InvestigationStep

class CandidateSelectionFragment : Fragment() {

    private var _binding: FragmentCandidateSelectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CandidateSelectionViewModel by viewModels()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private var candidates = listOf<CandidateProfile>()
    private var reportId = ""
    private var round = 1
    private var searchQuery = ""
    private var baseFields = mapOf<String, String>()
    private val selectedPositions = mutableSetOf<Int>()
    private lateinit var adapter: CandidateAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCandidateSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        InvestigationPipelineView.bind(binding.root, InvestigationStep.RESOLVE)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val candidatesJson = arguments?.getString("candidatesJson") ?: "[]"
        reportId = arguments?.getString("reportId") ?: ""
        round = arguments?.getInt("round") ?: 1
        searchQuery = arguments?.getString("searchQuery") ?: ""
        baseFields = parseQueryFields(arguments?.getString("baseQuery").orEmpty())

        val listType = Types.newParameterizedType(List::class.java, CandidateProfile::class.java)
        candidates = try {
            moshi.adapter<List<CandidateProfile>>(listType).fromJson(candidatesJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        binding.tvCandidateQuery.text = viewModel.formatDisplayQuery(searchQuery).ifBlank { searchQuery }
        binding.tvCandidateCount.text = resources.getQuantityString(
            R.plurals.candidate_match_count,
            candidates.size,
            candidates.size
        )

        adapter = CandidateAdapter(
            candidates,
            onSelect = { index -> toggleSelection(index) },
            onConfirm = { index -> confirmCandidate(index) }
        )
        binding.rvCandidates.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CandidateSelectionFragment.adapter
            itemAnimator = null
        }

        binding.btnDeepSearchSelected.setOnClickListener {
            if (!isAdded || !isResumed) return@setOnClickListener
            val ordered = selectedPositions.sorted().mapNotNull { candidates.getOrNull(it) }
            if (ordered.isEmpty()) return@setOnClickListener
            if (ordered.size == 1) {
                confirmCandidate(selectedPositions.first())
            } else {
                startQueuedDeepSearch(ordered)
            }
        }
        updateSelectedCta()

        binding.btnNotAny.setOnClickListener {
            if (!isAdded || !isResumed) return@setOnClickListener
            val nav = findNavController()
            // popBackStack returns false if nav_search is not on the back stack (e.g. the
            // user navigated here from a deep link or history). In that case, explicitly
            // navigate to the search screen so the button always does something visible.
            if (!nav.popBackStack(R.id.nav_search, false)) {
                try {
                    nav.navigate(R.id.nav_search)
                } catch (_: Exception) {
                    nav.popBackStack()
                }
            }
        }
    }

    private fun toggleSelection(index: Int) {
        if (!isAdded || !isResumed) return
        if (candidates.getOrNull(index) == null) return
        if (!selectedPositions.add(index)) {
            selectedPositions.remove(index)
        }
        adapter.notifyItemChanged(index)
        updateSelectedCta()
    }

    private fun updateSelectedCta() {
        if (_binding == null) return
        if (selectedPositions.isEmpty()) {
            binding.btnDeepSearchSelected.visibility = View.GONE
        } else {
            binding.btnDeepSearchSelected.visibility = View.VISIBLE
            binding.btnDeepSearchSelected.text = getString(
                R.string.candidate_deep_search_selected,
                selectedPositions.size
            )
        }
    }

    private fun startQueuedDeepSearch(ordered: List<CandidateProfile>) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.nav_candidate_selection) return
        val first = ordered.first()
        val lockedQuery = viewModel.buildLockedQuery(first, baseFields)
        if (lockedQuery.isBlank()) return

        val json = try {
            val listType = Types.newParameterizedType(List::class.java, CandidateProfile::class.java)
            moshi.adapter<List<CandidateProfile>>(listType).toJson(ordered)
        } catch (_: Exception) { "[]" }

        try {
            nav.navigate(
                R.id.action_candidates_to_progress,
                Bundle().apply {
                    putString("query", lockedQuery)
                    putString("type", "comprehensive")
                    putInt("round", round + 1)
                    putString("searchQuery", searchQuery)
                    putString("reportId", reportId)
                    putString("candidatesJson", json)
                    putInt("queueIndex", 0)
                }
            )
        } catch (_: Exception) {}
    }

    private fun confirmCandidate(index: Int) {
        if (!isAdded || !isResumed) return
        val candidate = candidates.getOrNull(index) ?: return
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.nav_candidate_selection) return

        val lockedQuery = viewModel.buildLockedQuery(candidate, baseFields)
        if (lockedQuery.isBlank()) return

        try {
            nav.navigate(
                R.id.action_candidates_to_progress,
                Bundle().apply {
                    putString("query", lockedQuery)
                    putString("type", "comprehensive")
                    putInt("round", round + 1)
                    putString("searchQuery", searchQuery)
                    putString("reportId", reportId)
                }
            )
        } catch (_: Exception) {}
    }

    private fun parseQueryFields(rawQuery: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        rawQuery.split("|").forEach { part ->
            val eq = part.indexOf("=")
            if (eq > 0) {
                val k = part.substring(0, eq).trim()
                val v = part.substring(eq + 1).trim()
                if (v.isNotBlank()) result[k] = v
            }
        }
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class CandidateAdapter(
        private val items: List<CandidateProfile>,
        private val onSelect: (Int) -> Unit,
        private val onConfirm: (Int) -> Unit
    ) : RecyclerView.Adapter<CandidateAdapter.VH>() {

        inner class VH(val b: ItemCandidateCardBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemCandidateCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            val b = holder.b

            bindPhotos(b, c)
            bindIdentity(b, c)
            bindSocial(b, c)
            bindMatchScore(b, c)

            val isSelected = position in selectedPositions
            b.cbCandidateSelect.isChecked = isSelected
            val ctx = b.root.context
            b.root.strokeColor = if (isSelected) {
                androidx.core.content.ContextCompat.getColor(ctx, R.color.success)
            } else {
                androidx.core.content.ContextCompat.getColor(ctx, R.color.border)
            }
            b.root.setOnClickListener { onSelect(position) }
            b.btnThatsThem.setOnClickListener { onConfirm(position) }
        }

        private fun bindPhotos(b: ItemCandidateCardBinding, c: CandidateProfile) {
            val photos = c.allPhotoUrls()
            val primaryUrl = photos.firstOrNull()

            if (c.isCompany && !c.logoUrl.isNullOrBlank()) {
                b.ivCandidatePhotoPrimary.load(c.logoUrl) {
                    crossfade(true)
                    placeholder(R.drawable.bg_candidate_photo_placeholder)
                    error(R.drawable.ic_business)
                }
            } else if (primaryUrl != null) {
                b.ivCandidatePhotoPrimary.load(primaryUrl) {
                    crossfade(true)
                    placeholder(R.drawable.bg_candidate_photo_placeholder)
                    error(R.drawable.bg_candidate_photo_placeholder)
                }
            } else {
                b.ivCandidatePhotoPrimary.setImageResource(R.drawable.bg_candidate_photo_placeholder)
            }

            val extraPhotos = photos.drop(1).take(2)
            b.photoThumbnailsRow.isVisible = extraPhotos.isNotEmpty()
            bindThumb(b.ivPhotoThumb2, extraPhotos.getOrNull(0))
            bindThumb(b.ivPhotoThumb3, extraPhotos.getOrNull(1))
        }

        private fun bindThumb(view: com.google.android.material.imageview.ShapeableImageView, url: String?) {
            if (url.isNullOrBlank()) {
                view.isVisible = false
                return
            }
            view.isVisible = true
            view.load(url) {
                crossfade(true)
                placeholder(R.drawable.bg_candidate_photo_placeholder)
                error(R.drawable.bg_candidate_photo_placeholder)
            }
        }

        private fun bindIdentity(b: ItemCandidateCardBinding, c: CandidateProfile) {
            b.tvCandidateName.text = when {
                c.isCompany -> c.name.ifBlank { "Unknown company" }
                else -> c.name.ifBlank { "Unknown" }
            }

            val ageLoc = listOfNotNull(
                c.age.takeIf { it.isNotBlank() && !c.isCompany }?.let { "Age $it" },
                c.location.takeIf { it.isNotBlank() }
            )
            if (ageLoc.isNotEmpty()) {
                b.tvCandidateAgeLocation.text = ageLoc.joinToString(" · ")
                b.tvCandidateAgeLocation.isVisible = true
            } else {
                b.tvCandidateAgeLocation.isVisible = false
            }

            val employer = c.employerSnippet()
            if (employer != null) {
                b.tvCandidateEmployer.text = employer
                b.tvCandidateEmployer.isVisible = true
            } else {
                b.tvCandidateEmployer.isVisible = false
            }

            val phones = c.phones.filter { it.isNotBlank() }.distinct()
            if (phones.isNotEmpty()) {
                b.tvCandidatePhones.text = phones.take(3).joinToString("  ·  ")
                b.tvCandidatePhones.isVisible = true
            } else {
                b.tvCandidatePhones.isVisible = false
            }
        }

        private fun bindSocial(b: ItemCandidateCardBinding, c: CandidateProfile) {
            val hasSocial = !c.linkedinUrl.isNullOrBlank()
                || !c.facebookUrl.isNullOrBlank()
                || !c.instagramUrl.isNullOrBlank()

            b.socialIconsRow.isVisible = hasSocial
            bindSocialButton(b.btnSocialLinkedin, c.linkedinUrl)
            bindSocialButton(b.btnSocialFacebook, c.facebookUrl)
            bindSocialButton(b.btnSocialInstagram, c.instagramUrl)
        }

        private fun bindSocialButton(button: View, url: String?) {
            button.isVisible = !url.isNullOrBlank()
            if (url.isNullOrBlank()) return
            button.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: Exception) {}
            }
        }

        private fun bindMatchScore(b: ItemCandidateCardBinding, c: CandidateProfile) {
            val pct = (c.confidence * 100).toInt().coerceIn(0, 100)
            b.chipMatchScore.text = getString(R.string.candidate_match_pct, pct)
        }
    }
}
