package com.twoskoops707.sixdegrees.ui.candidates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch

class CandidateSelectionFragment : Fragment() {

    private var _binding: FragmentCandidateSelectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CandidateSelectionViewModel by viewModels()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private var candidates = listOf<CandidateProfile>()
    private var reportId = ""
    private var round = 1
    private lateinit var adapter: CandidateAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCandidateSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val candidatesJson = arguments?.getString("candidatesJson") ?: "[]"
        reportId = arguments?.getString("reportId") ?: ""
        round = arguments?.getInt("round") ?: 1

        val listType = Types.newParameterizedType(List::class.java, CandidateProfile::class.java)
        candidates = try {
            moshi.adapter<List<CandidateProfile>>(listType).fromJson(candidatesJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val maxSelect = when (round) { 1 -> 4; 2 -> 2; else -> 1 }
        val roundTotal = 3

        binding.tvRoundLabel.text = "ROUND $round OF $roundTotal"
        val rawSq = arguments?.getString("searchQuery") ?: ""
        binding.tvCandidateQuery.text = if (rawSq.contains("=")) {
            rawSq.split("|").joinToString(", ") { part ->
                val eq = part.indexOf('=')
                if (eq != -1) part.substring(eq + 1).trim() else part.trim()
            }.replace(Regex(",\\s*,"), ",").trim().trimEnd(',')
        } else rawSq
        binding.tvSelectInstructions.text = when (round) {
            1 -> "Select up to 4 people who could be your subject"
            2 -> "Select 1-2 best matches for a full investigation"
            else -> "Select the single best match for a deep-dive report"
        }

        adapter = CandidateAdapter(candidates) { index ->
            viewModel.toggleSelection(index, maxSelect)
        }
        binding.rvCandidates.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CandidateSelectionFragment.adapter
            itemAnimator = null
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selected.collect { selected ->
                if (_binding == null) return@collect
                adapter.updateSelection(selected)
                binding.btnInvestigate.isEnabled = selected.isNotEmpty()
                binding.btnInvestigate.alpha = if (selected.isNotEmpty()) 1f else 0.4f
            }
        }

        binding.btnInvestigate.setOnClickListener {
            if (!isAdded || !isResumed) return@setOnClickListener
            val nav = findNavController()
            if (nav.currentDestination?.id != R.id.nav_candidate_selection) return@setOnClickListener
            val refinedQuery = viewModel.buildRefinedQuery(candidates)
            if (refinedQuery.isBlank()) return@setOnClickListener
            val nextRound = round + 1
            try {
                nav.navigate(
                    R.id.action_candidates_to_progress,
                    Bundle().apply {
                        putString("query", refinedQuery)
                        putString("type", "comprehensive")
                        putInt("round", nextRound)
                        putString("searchQuery", arguments?.getString("searchQuery") ?: "")
                    }
                )
            } catch (_: Exception) {}
        }

        binding.btnSkipToResults.setOnClickListener {
            if (!isAdded || !isResumed) return@setOnClickListener
            val nav = findNavController()
            if (nav.currentDestination?.id != R.id.nav_candidate_selection) return@setOnClickListener
            try {
                nav.navigate(
                    R.id.action_candidates_to_results,
                    Bundle().apply {
                        putString("searchQuery", arguments?.getString("searchQuery") ?: "")
                        putString("searchType", "comprehensive")
                        putString("reportId", reportId)
                    }
                )
            } catch (_: Exception) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class CandidateAdapter(
        private val items: List<CandidateProfile>,
        private val onToggle: (Int) -> Unit
    ) : RecyclerView.Adapter<CandidateAdapter.VH>() {

        private var selectedIndices = setOf<Int>()

        inner class VH(val b: ItemCandidateCardBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemCandidateCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        fun updateSelection(indices: Set<Int>) {
            val old = selectedIndices
            selectedIndices = indices
            (old + indices).forEach { notifyItemChanged(it) }
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            val isSelected = selectedIndices.contains(position)
            val isCompany = c.isCompany

            // Show photo or company logo
            if (isCompany) {
                holder.b.ivCandidatePhoto.visibility = View.GONE
                if (!c.logoUrl.isNullOrBlank()) {
                    holder.b.ivCandidatePhoto.visibility = View.GONE
                    // Use existing ImageView for company logo — will load via load()
                    holder.b.ivCandidatePhoto.load(c.logoUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_business)
                        error(R.drawable.ic_business)
                    }
                } else {
                    holder.b.ivCandidatePhoto.setImageResource(R.drawable.ic_business)
                }
            } else {
                holder.b.ivCandidatePhoto.visibility = View.VISIBLE
                if (!c.photoUrl.isNullOrBlank()) {
                    holder.b.ivCandidatePhoto.load(c.photoUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_person_placeholder)
                        error(R.drawable.ic_person_placeholder)
                    }
                } else {
                    holder.b.ivCandidatePhoto.setImageResource(R.drawable.ic_person_placeholder)
                }
            }

            holder.b.tvCandidateName.text = if (isCompany) c.name.ifBlank { "Unknown Company" } else c.name.ifBlank { "Unknown" }

            if (isCompany) {
                // Company mode — show domain and industry
                holder.b.tvCandidateAgeLocation.visibility = View.GONE
                holder.b.tvCandidatePhone.visibility = View.GONE
            } else {
                // Person mode — show age, location, phone
                holder.b.tvCandidateAgeLocation.visibility = View.GONE
                holder.b.companyInfoRow.visibility = View.GONE
                val ageLocParts = listOfNotNull(
                    c.age.takeIf { it.isNotBlank() }?.let { "Age $it" },
                    c.location.takeIf { it.isNotBlank() }
                )
                if (ageLocParts.isNotEmpty()) {
                    holder.b.tvCandidateAgeLocation.text = ageLocParts.joinToString(" · ")
                    holder.b.tvCandidateAgeLocation.visibility = View.VISIBLE
                }
                val phones = c.phones.take(2)
                if (phones.isNotEmpty()) {
                    holder.b.tvCandidatePhone.text = phones.joinToString(" · ")
                    holder.b.tvCandidatePhone.visibility = View.VISIBLE
                } else {
                    holder.b.tvCandidatePhone.visibility = View.GONE
                }
            }

            if (c.address.isNotBlank()) {
                holder.b.tvCandidateAddress.text = if (isCompany) "🏢 ${c.address}" else "📍 ${c.address}"
                holder.b.tvCandidateAddress.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateAddress.visibility = View.GONE
            }

            if (c.relatives.isNotEmpty() && !isCompany) {
                holder.b.tvCandidateRelatives.text = "👥 ${c.relatives.take(3).joinToString(", ")}"
                holder.b.tvCandidateRelatives.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateRelatives.visibility = View.GONE
            }

            // DOB
            if (c.dob.isNullOrBlank()) {
                holder.b.tvCandidateDob.visibility = View.GONE
            } else {
                holder.b.tvCandidateDob.text = "🎂 ${c.dob}"
                holder.b.tvCandidateDob.visibility = View.VISIBLE
            }

            // AKAs
            if (c.akas.isEmpty()) {
                holder.b.tvCandidateAkas.visibility = View.GONE
            } else {
                holder.b.tvCandidateAkas.text = "↔ ${c.akas.joinToString(", ")}"
                holder.b.tvCandidateAkas.visibility = View.VISIBLE
            }

            // Email
            if (c.email.isNullOrBlank()) {
                holder.b.tvCandidateEmail.visibility = View.GONE
            } else {
                holder.b.tvCandidateEmail.text = "✉ ${c.email}"
                holder.b.tvCandidateEmail.visibility = View.VISIBLE
            }

            // Political affiliation
            if (c.politicalAffiliation.isNullOrBlank()) {
                holder.b.tvCandidatePolitical.visibility = View.GONE
            } else {
                holder.b.tvCandidatePolitical.text = "🗳 ${c.politicalAffiliation}"
                holder.b.tvCandidatePolitical.visibility = View.VISIBLE
            }

            // Company domain/industry chips
            if (isCompany) {
                holder.b.companyInfoRow.visibility = View.VISIBLE
                if (!c.domain.isNullOrBlank()) {
                    holder.b.chipCompanyDomain.text = c.domain
                    holder.b.chipCompanyDomain.visibility = View.VISIBLE
                } else {
                    holder.b.chipCompanyDomain.visibility = View.GONE
                }
                if (!c.industry.isNullOrBlank()) {
                    holder.b.chipCompanyIndustry.text = c.industry
                    holder.b.chipCompanyIndustry.visibility = View.VISIBLE
                } else {
                    holder.b.chipCompanyIndustry.visibility = View.GONE
                }
            } else {
                holder.b.companyInfoRow.visibility = View.GONE
            }

            val confidencePct = (c.confidence * 100).toInt().coerceIn(0, 100)
            holder.b.progressConfidence.progress = confidencePct
            holder.b.tvConfidencePct.text = "$confidencePct%"

            holder.b.chipCandidateSource.text = c.source.take(12)

            holder.b.ivSelectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE

            val card = holder.itemView as? com.google.android.material.card.MaterialCardView
            if (isSelected) {
                card?.strokeColor = requireContext().getColor(android.R.color.transparent).let {
                    val tv = android.util.TypedValue()
                    requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                    tv.data
                }
                card?.strokeWidth = 3
            } else {
                card?.strokeColor = ContextCompat.getColor(requireContext(), R.color.border)
                card?.strokeWidth = 1
            }

            holder.itemView.setOnClickListener { onToggle(position) }
        }
    }
}
