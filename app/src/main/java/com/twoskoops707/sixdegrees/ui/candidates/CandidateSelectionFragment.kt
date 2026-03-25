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

        val maxSelect = if (round == 1) 2 else 1
        val roundTotal = 3

        binding.tvRoundLabel.text = "ROUND $round OF $roundTotal"
        val rawSq = arguments?.getString("searchQuery") ?: ""
        binding.tvCandidateQuery.text = if (rawSq.contains("=")) {
            rawSq.split("|").joinToString(", ") { part ->
                val eq = part.indexOf('=')
                if (eq != -1) part.substring(eq + 1).trim() else part.trim()
            }.replace(Regex(",\\s*,"), ",").trim().trimEnd(',')
        } else rawSq
        binding.tvSelectInstructions.text = if (round == 1)
            "Select the 1-2 best matches to investigate further"
        else
            "Select the single best match for a deep-dive report"

        adapter = CandidateAdapter(candidates) { index ->
            viewModel.toggleSelection(index, maxSelect)
        }
        binding.rvCandidates.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CandidateSelectionFragment.adapter
            itemAnimator = null
        }

        lifecycleScope.launch {
            viewModel.selected.collect { selected ->
                if (_binding == null) return@collect
                adapter.updateSelection(selected)
                binding.btnInvestigate.isEnabled = selected.isNotEmpty()
                binding.btnInvestigate.alpha = if (selected.isNotEmpty()) 1f else 0.4f
            }
        }

        binding.btnInvestigate.setOnClickListener {
            if (!isAdded || !isResumed) return@setOnClickListener
            val refinedQuery = viewModel.buildRefinedQuery(candidates)
            if (refinedQuery.isBlank()) return@setOnClickListener
            val nextRound = round + 1
            findNavController().navigate(
                R.id.action_candidates_to_progress,
                Bundle().apply {
                    putString("query", refinedQuery)
                    putString("type", "comprehensive")
                    putInt("round", nextRound)
                    putString("searchQuery", arguments?.getString("searchQuery") ?: "")
                }
            )
        }

        binding.btnSkipToResults.setOnClickListener {
            if (!isAdded || !isResumed) return@setOnClickListener
            findNavController().navigate(
                R.id.action_candidates_to_results,
                Bundle().apply {
                    putString("searchQuery", arguments?.getString("searchQuery") ?: "")
                    putString("searchType", "comprehensive")
                    putString("reportId", reportId)
                }
            )
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

            // --- Photo / Logo ---
            if (isCompany) {
                holder.b.ivCandidatePhoto.visibility = View.GONE
                holder.b.ivCompanyLogo.visibility = View.VISIBLE
                if (!c.logoUrl.isNullOrBlank()) {
                    holder.b.ivCompanyLogo.load(c.logoUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_business)
                        error(R.drawable.ic_business)
                    }
                } else {
                    holder.b.ivCompanyLogo.setImageResource(R.drawable.ic_business)
                }
            } else {
                holder.b.ivCandidatePhoto.visibility = View.VISIBLE
                holder.b.ivCompanyLogo.visibility = View.GONE
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

            // --- Name ---
            holder.b.tvCandidateName.text = if (isCompany) c.name.ifBlank { "Unknown Company" } else c.name.ifBlank { "Unknown" }

            // --- Age / Location ---
            if (isCompany) {
                holder.b.tvCandidateAgeLocation.visibility = View.GONE
            } else {
                holder.b.tvCandidateAgeLocation.visibility = View.VISIBLE
                val ageLoc = listOfNotNull(
                    c.age.takeIf { it.isNotBlank() }?.let { "Age $it" },
                    c.location.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                holder.b.tvCandidateAgeLocation.text = ageLoc
            }

            // --- DOB ---
            if (!c.fullDOB.isNullOrBlank() && !isCompany) {
                holder.b.tvCandidateDob.text = "🎂 ${c.fullDOB}"
                holder.b.tvCandidateDob.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateDob.visibility = View.GONE
            }

            // --- Company info chips ---
            if (isCompany) {
                holder.b.companyInfoRow.visibility = View.VISIBLE
                holder.b.chipCompanyDomain.text = c.domain ?: ""
                holder.b.chipCompanyDomain.visibility = if (!c.domain.isNullOrBlank()) View.VISIBLE else View.GONE
                holder.b.chipCompanyIndustry.text = c.industry ?: ""
                holder.b.chipCompanyIndustry.visibility = if (!c.industry.isNullOrBlank()) View.VISIBLE else View.GONE
            } else {
                holder.b.companyInfoRow.visibility = View.GONE
            }

            // --- Phones ---
            val allPhones = c.phones
            if (allPhones.isNotEmpty() && !isCompany) {
                holder.b.tvCandidatePhone.text = "☎ " + allPhones.joinToString("  ·  ")
                holder.b.tvCandidatePhone.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidatePhone.visibility = View.GONE
            }

            // --- Emails ---
            if (c.allEmails.isNotEmpty() && !isCompany) {
                holder.b.tvCandidateEmail.text = "✉ " + c.allEmails.joinToString(", ")
                holder.b.tvCandidateEmail.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateEmail.visibility = View.GONE
            }

            // --- Usernames ---
            if (c.usernames.isNotEmpty() && !isCompany) {
                holder.b.tvCandidateUsernames.text = "@ " + c.usernames.joinToString("  ·  ")
                holder.b.tvCandidateUsernames.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateUsernames.visibility = View.GONE
            }

            // --- Address ---
            if (c.address.isNotBlank()) {
                val prefix = if (isCompany) "🏢" else "📍"
                holder.b.tvCandidateAddress.text = "$prefix ${c.address}"
                holder.b.tvCandidateAddress.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateAddress.visibility = View.GONE
            }

            // --- AKAs ---
            if (c.akasNicknames.isNotEmpty() && !isCompany) {
                holder.b.tvCandidateAkas.text = "🔖 AKA: " + c.akasNicknames.joinToString(", ")
                holder.b.tvCandidateAkas.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateAkas.visibility = View.GONE
            }

            // --- Employment ---
            if (c.employmentHistory.isNotEmpty()) {
                holder.b.tvCandidateEmployment.text = "💼 " + c.employmentHistory.take(2).joinToString("  |  ")
                holder.b.tvCandidateEmployment.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateEmployment.visibility = View.GONE
            }

            // --- Education ---
            if (c.educationHistory.isNotEmpty()) {
                holder.b.tvCandidateEducation.text = "🎓 " + c.educationHistory.take(2).joinToString("  |  ")
                holder.b.tvCandidateEducation.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateEducation.visibility = View.GONE
            }

            // --- Property ---
            if (c.propertyRecords.isNotEmpty()) {
                holder.b.tvCandidateProperty.text = "🏠 " + c.propertyRecords.take(2).joinToString("  |  ")
                holder.b.tvCandidateProperty.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateProperty.visibility = View.GONE
            }

            // --- Vehicles ---
            if (c.vehicleInfo.isNotEmpty()) {
                holder.b.tvCandidateVehicles.text = "🚗 " + c.vehicleInfo.take(2).joinToString("  |  ")
                holder.b.tvCandidateVehicles.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateVehicles.visibility = View.GONE
            }

            // --- Legal ---
            if (c.legalRecords.isNotEmpty()) {
                holder.b.tvCandidateLegal.text = "⚠ " + c.legalRecords.take(3).joinToString("  ·  ")
                holder.b.tvCandidateLegal.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateLegal.visibility = View.GONE
            }

            // --- Financial ---
            if (c.financialFlags.isNotEmpty()) {
                holder.b.tvCandidateFinancial.text = "💳 " + c.financialFlags.take(3).joinToString("  ·  ")
                holder.b.tvCandidateFinancial.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateFinancial.visibility = View.GONE
            }

            // --- Relatives ---
            if (c.relatives.isNotEmpty() && !isCompany) {
                holder.b.tvCandidateRelatives.text = "👥 " + c.relatives.take(4).joinToString(", ")
                holder.b.tvCandidateRelatives.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateRelatives.visibility = View.GONE
            }

            // --- Social ---
            if (c.socialProfiles.isNotEmpty() && !isCompany) {
                holder.b.tvCandidateSocial.text = "◎ " + c.socialProfiles.take(5).joinToString("  ·  ")
                holder.b.tvCandidateSocial.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateSocial.visibility = View.GONE
            }

            // --- Languages / Political ---
            val langPolParts = mutableListOf<String>()
            if (c.languages.isNotEmpty()) langPolParts.add("Lang: " + c.languages.joinToString(", "))
            if (!c.politicalAffiliation.isNullOrBlank()) langPolParts.add("Political: ${c.politicalAffiliation}")
            if (langPolParts.isNotEmpty() && !isCompany) {
                holder.b.tvCandidateLangPolitical.text = langPolParts.joinToString("  ·  ")
                holder.b.tvCandidateLangPolitical.visibility = View.VISIBLE
            } else {
                holder.b.tvCandidateLangPolitical.visibility = View.GONE
            }

            // --- Confidence ---
            val confidencePct = (c.confidence * 100).toInt().coerceIn(0, 100)
            holder.b.progressConfidence.progress = confidencePct
            holder.b.tvConfidencePct.text = "$confidencePct% match"

            // --- Source chip ---
            holder.b.chipCandidateSource.text = c.source.take(14)

            // --- Selection state ---
            holder.b.ivSelectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            val strokeColor = if (isSelected)
                ContextCompat.getColor(requireContext(), R.color.success)
            else
                ContextCompat.getColor(requireContext(), R.color.border)
            (holder.itemView as? com.google.android.material.card.MaterialCardView)?.strokeColor = strokeColor
            if (isSelected) {
                (holder.itemView as? com.google.android.material.card.MaterialCardView)?.strokeWidth = 2
            } else {
                (holder.itemView as? com.google.android.material.card.MaterialCardView)?.strokeWidth = 1
            }

            holder.itemView.setOnClickListener { onToggle(position) }
        }
    }
}
