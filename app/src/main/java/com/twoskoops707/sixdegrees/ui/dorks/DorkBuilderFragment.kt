package com.twoskoops707.sixdegrees.ui.dorks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentDorkBuilderBinding
import com.twoskoops707.sixdegrees.databinding.ItemDorkBinding
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
            val dorks = generateDorks()
            if (dorks.isEmpty()) {
                binding.tilFullName.error = "Enter at least a full name"
                return@setOnClickListener
            }
            binding.tilFullName.error = null
            binding.rvDorks.layoutManager = LinearLayoutManager(requireContext())
            binding.rvDorks.adapter = DorkAdapter(dorks)
            binding.resultsContainer.visibility = View.VISIBLE
        }
    }

    private fun generateDorks(): List<Pair<String, String>> {
        val name = binding.etFullName.text?.toString()?.trim() ?: ""
        val alias = binding.etAlias.text?.toString()?.trim() ?: ""
        val dob = binding.etDob.text?.toString()?.trim() ?: ""
        val city = binding.etCity.text?.toString()?.trim() ?: ""
        val state = binding.etState.text?.toString()?.trim() ?: ""
        val address = binding.etAddress.text?.toString()?.trim() ?: ""
        val email = binding.etEmail.text?.toString()?.trim() ?: ""
        val phone = binding.etPhone.text?.toString()?.trim() ?: ""
        val username = binding.etUsername.text?.toString()?.trim() ?: ""
        val employer = binding.etEmployer.text?.toString()?.trim() ?: ""
        val relatives = binding.etRelatives.text?.toString()?.trim() ?: ""
        val associates = binding.etAssociates.text?.toString()?.trim() ?: ""
        val keywords = binding.etKeywords.text?.toString()?.trim() ?: ""

        if (name.isBlank()) return emptyList()

        val dorks = mutableListOf<Pair<String, String>>()
        val q = "\"$name\""
        val locPart = listOf(city, state).filter { it.isNotBlank() }.joinToString(" ") { "\"$it\"" }

        dorks.add("Broad Presence" to "$q")

        if (locPart.isNotBlank()) {
            dorks.add("With Location" to "$q $locPart")
        }

        dorks.add("Document Files" to "$q filetype:pdf OR filetype:doc OR filetype:xls OR filetype:xlsx")

        dorks.add("Credential / PII Exposure" to "$q (\"password\" OR \"passwd\" OR \"social security\" OR \"date of birth\" OR \"SSN\") -site:whitepages.com -site:spokeo.com")

        dorks.add("PII in Government Docs" to "$q (\"date of birth\" OR \"DOB\" OR \"SSN\" OR \"driver license\") site:*.gov filetype:pdf")

        dorks.add("Court & Legal Records" to "$q site:courtlistener.com OR site:pacer.gov OR site:judyrecords.com OR site:unicourt.com")

        dorks.add("Arrest & Mugshot Records" to "$q site:mugshots.com OR site:busted.com OR site:jailbase.com OR site:arrests.org")

        dorks.add("Financial Disclosures & SEC" to "$q site:sec.gov OR site:finra.org OR site:fdic.gov OR site:opencorporates.com")

        dorks.add("Property & Real Estate" to "$q (\"property\" OR \"deed\" OR \"parcel\" OR \"owner\")${if (locPart.isNotBlank()) " $locPart" else ""} site:*.gov OR intitle:assessor")

        dorks.add("Relationship Mapping" to "$q (\"married to\" OR \"husband\" OR \"wife\" OR \"partner\" OR \"son of\" OR \"daughter of\" OR \"lives with\")")

        dorks.add("News & Media Mentions" to "$q (inurl:news OR inurl:article OR site:reuters.com OR site:apnews.com OR site:nytimes.com)")

        dorks.add("Social Media Profiles" to "$q site:facebook.com OR site:instagram.com OR site:twitter.com OR site:linkedin.com -login")

        dorks.add("Forum & Community Posts" to "$q site:reddit.com OR site:quora.com OR site:medium.com OR site:disqus.com")

        dorks.add("Voter Registration" to "$q (\"voter registration\" OR \"registered voter\" OR \"voter roll\")${if (state.isNotBlank()) " \"$state\"" else ""}")

        dorks.add("Professional Licenses" to "$q (\"licensed\" OR \"license\" OR \"certification\" OR \"credential\") site:*.gov OR site:*.state.*.us")

        if (employer.isNotBlank()) {
            val emp = "\"$employer\""
            dorks.add("Employment Cross-Reference" to "$q $emp (\"employee\" OR \"director\" OR \"officer\" OR \"VP\" OR \"manager\" OR \"hired\")")
            dorks.add("Company Filings with Name" to "$q $emp site:sec.gov OR site:opencorporates.com OR site:bizapedia.com")
        }

        if (email.isNotBlank()) {
            val em = "\"$email\""
            dorks.add("Email in Pastes/Dumps" to "$em (\"password\" OR \"hash\" OR \"dump\" OR \"leak\") site:pastebin.com OR site:hastebin.com OR site:ghostbin.com")
            dorks.add("Email Breach Exposure" to "$em site:haveibeenpwned.com OR site:dehashed.com OR site:leakcheck.io")
            dorks.add("Email Account Lookups" to "$em site:truepeoplesearch.com OR site:whitepages.com OR site:spokeo.com")
        }

        if (phone.isNotBlank()) {
            val ph = "\"$phone\""
            dorks.add("Phone Number Exposure" to "$ph site:truecaller.com OR site:whitepages.com OR site:anywho.com OR site:411.com")
            dorks.add("Phone in Public Records" to "$ph (\"contact\" OR \"call\" OR \"reach\") -ads")
        }

        if (username.isNotBlank()) {
            val un = "\"$username\""
            dorks.add("Username Cross-Site" to "$un site:github.com OR site:reddit.com OR site:twitter.com OR site:instagram.com OR site:tiktok.com")
            dorks.add("Username in Documents" to "$un filetype:pdf OR filetype:doc OR filetype:txt")
        }

        if (dob.isNotBlank()) {
            dorks.add("DOB Cross-Reference" to "$q \"$dob\" (\"born\" OR \"birthday\" OR \"birth date\" OR \"age\")")
        }

        if (address.isNotBlank()) {
            dorks.add("Address in Records" to "$q \"$address\" (\"resident\" OR \"owner\" OR \"occupant\" OR \"address\")")
            dorks.add("Address Property Records" to "\"$address\" site:*.gov OR intitle:\"property tax\" OR intitle:\"parcel\"")
        }

        if (relatives.isNotBlank()) {
            val relList = relatives.split(",").map { it.trim() }.filter { it.isNotBlank() }
            relList.take(3).forEach { rel ->
                dorks.add("Relation: $rel" to "$q \"$rel\" (\"brother\" OR \"sister\" OR \"father\" OR \"mother\" OR \"spouse\" OR \"relative\" OR \"family\")")
            }
        }

        if (associates.isNotBlank()) {
            val assocList = associates.split(",").map { it.trim() }.filter { it.isNotBlank() }
            assocList.take(2).forEach { assoc ->
                dorks.add("Associate: $assoc" to "$q \"$assoc\"")
            }
        }

        if (alias.isNotBlank()) {
            dorks.add("Alias / Nickname" to "\"$alias\"${if (locPart.isNotBlank()) " $locPart" else ""} (\"real name\" OR \"aka\" OR \"also known as\" OR \"born\")")
        }

        if (keywords.isNotBlank()) {
            val kwList = keywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (kwList.isNotEmpty()) {
                dorks.add("Custom Keywords" to "$q ${kwList.joinToString(" ") { "\"$it\"" }}")
            }
        }

        dorks.add("Cached Old Profiles" to "$q cache:facebook.com OR cache:twitter.com OR cache:linkedin.com OR cache:myspace.com")

        dorks.add("PasteBin / Data Dumps" to "$q site:pastebin.com OR site:rentry.co OR site:paste.ee OR site:privatebin.net")

        dorks.add("Old Forum / BBS Archives" to "$q site:groups.google.com OR site:angelfire.com OR site:geocities.ws OR site:tripod.com")

        return dorks
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
