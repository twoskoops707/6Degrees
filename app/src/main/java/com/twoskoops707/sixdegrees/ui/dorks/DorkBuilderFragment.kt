package com.twoskoops707.sixdegrees.ui.dorks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.twoskoops707.sixdegrees.databinding.FragmentDorkBuilderBinding
import com.twoskoops707.sixdegrees.databinding.ItemDorkBinding
import com.twoskoops707.sixdegrees.domain.GoogleDorkLibrary
import com.twoskoops707.sixdegrees.domain.model.SubjectProfile
import java.net.URLEncoder

class DorkBuilderFragment : Fragment() {

    private var _binding: FragmentDorkBuilderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDorkBuilderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnGenerate.setOnClickListener {
            val profile = buildProfile()
            val dorks = generateDorks(profile)
            if (dorks.isEmpty()) {
                binding.tilFullName.error = "Enter at least a full name, phone, or email"
                return@setOnClickListener
            }
            binding.tilFullName.error = null
            binding.rvDorks.layoutManager = LinearLayoutManager(requireContext())
            binding.rvDorks.adapter = DorkAdapter(dorks)
            binding.resultsContainer.visibility = View.VISIBLE
        }
    }

    private fun buildProfile(): SubjectProfile {
        val name = binding.etFullName.text?.toString()?.trim().orEmpty()
        val parts = name.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return SubjectProfile(
            name = name,
            firstName = parts.firstOrNull().orEmpty(),
            lastName = if (parts.size > 1) parts.last() else "",
            city = binding.etCity.text?.toString()?.trim().orEmpty(),
            state = binding.etState.text?.toString()?.trim().orEmpty(),
            phone = binding.etPhone.text?.toString()?.trim().orEmpty(),
            email = binding.etEmail.text?.toString()?.trim().orEmpty(),
            username = binding.etUsername.text?.toString()?.trim().orEmpty(),
            employer = binding.etEmployer.text?.toString()?.trim().orEmpty(),
            address = binding.etAddress.text?.toString()?.trim().orEmpty(),
            dob = binding.etDob.text?.toString()?.trim().orEmpty()
        )
    }

    private fun generateDorks(profile: SubjectProfile): List<Pair<String, String>> {
        if (profile.name.isBlank() && profile.phone.isBlank() && profile.email.isBlank()) {
            return emptyList()
        }
        val libraryDorks = GoogleDorkLibrary.dorksForBuilder(profile)
        val extras = mutableListOf<Pair<String, String>>()

        val alias = binding.etAlias.text?.toString()?.trim().orEmpty()
        if (alias.isNotBlank()) {
            val loc = listOf(profile.city, profile.state).filter { it.isNotBlank() }
                .joinToString(" ") { "\"$it\"" }
            extras.add("Alias / Nickname" to "\"$alias\"${if (loc.isNotBlank()) " $loc" else ""} (\"real name\" OR \"aka\" OR \"also known as\")")
        }

        val relatives = binding.etRelatives.text?.toString()?.trim().orEmpty()
        if (relatives.isNotBlank() && profile.name.isNotBlank()) {
            relatives.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(3).forEach { rel ->
                extras.add("Relation: $rel" to "\"${profile.name}\" \"$rel\" (relative OR family OR spouse)")
            }
        }

        val associates = binding.etAssociates.text?.toString()?.trim().orEmpty()
        if (associates.isNotBlank() && profile.name.isNotBlank()) {
            associates.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(2).forEach { assoc ->
                extras.add("Associate: $assoc" to "\"${profile.name}\" \"$assoc\"")
            }
        }

        val keywords = binding.etKeywords.text?.toString()?.trim().orEmpty()
        if (keywords.isNotBlank() && profile.name.isNotBlank()) {
            val kw = keywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (kw.isNotEmpty()) {
                extras.add("Custom Keywords" to "\"${profile.name}\" ${kw.joinToString(" ") { "\"$it\"" }}")
            }
        }

        return (libraryDorks + extras).distinctBy { it.second }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class DorkAdapter(
        private val dorks: List<Pair<String, String>>
    ) : RecyclerView.Adapter<DorkAdapter.VH>() {

        inner class VH(val b: ItemDorkBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemDorkBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (label, query) = dorks[position]
            holder.b.tvDorkLabel.text = label
            holder.b.tvDorkQuery.text = query
            holder.b.root.setOnClickListener {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.google.com/search?q=$encoded"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        override fun getItemCount() = dorks.size
    }
}
