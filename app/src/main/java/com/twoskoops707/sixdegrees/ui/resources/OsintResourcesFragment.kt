package com.twoskoops707.sixdegrees.ui.resources

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentOsintResourcesBinding

class OsintResourcesFragment : Fragment() {

    private var _binding: FragmentOsintResourcesBinding? = null
    private val binding get() = _binding!!

    private data class OsintTool(
        val name: String,
        val description: String,
        val buildUrl: (query: String) -> String,
        val categories: Set<String>
    )

    private data class OsintCategory(
        val id: String,
        val label: String,
        val icon: String,
        val description: String
    )

    private val categories = listOf(
        OsintCategory("person",   "Person",      "👤", "Public records, people search engines, criminal & court records"),
        OsintCategory("email",    "Email",        "📧", "Breach history, reputation, deliverability, linked accounts"),
        OsintCategory("phone",    "Phone",        "📞", "Carrier lookup, owner ID, reverse search, spam reports"),
        OsintCategory("username", "Username",     "🔑", "Cross-platform presence — social, gaming, forums, dating"),
        OsintCategory("domain",   "Domain / IP",  "🌐", "WHOIS, DNS, open ports, SSL certs, threat intel, archives"),
        OsintCategory("company",  "Company",      "🏢", "Corporate registry, filings, officers, tech stack, financials"),
        OsintCategory("image",    "Image / Face", "🖼️", "Reverse image & facial recognition across the web"),
        OsintCategory("social",   "Social Media", "💬", "Deep search across Twitter/X, Instagram, Reddit, Telegram")
    )

    private val allTools = listOf(

        // ── PERSON ─────────────────────────────────────────────────────────────
        OsintTool("FastPeopleSearch", "Free US people search — addresses, phones, relatives",
            { q -> "https://www.fastpeoplesearch.com/name/${q.replace(" ", "-")}" },
            setOf("person")),
        OsintTool("TruePeopleSearch", "Free full-profile people search with address history",
            { q -> "https://www.truepeoplesearch.com/results?name=${Uri.encode(q)}" },
            setOf("person")),
        OsintTool("WhitePages", "US name, phone, and address lookup",
            { q -> "https://www.whitepages.com/name/${q.replace(" ", "-")}" },
            setOf("person")),
        OsintTool("Spokeo", "Aggregates social, public, and contact data",
            { q -> "https://www.spokeo.com/${q.replace(" ", "+")}" },
            setOf("person")),
        OsintTool("BeenVerified", "Background check — employment, criminal, social",
            { q -> "https://www.beenverified.com/f/search/person?q=${Uri.encode(q)}" },
            setOf("person")),
        OsintTool("Intelius", "Comprehensive background reports and identity verification",
            { q -> "https://www.intelius.com/people-search/name/${q.replace(" ", "+")}" },
            setOf("person")),
        OsintTool("CourtListener", "US federal & state court records and PACER filings",
            { q -> "https://www.courtlistener.com/?q=${Uri.encode(q)}&type=p" },
            setOf("person")),
        OsintTool("JailBase", "US arrest and booking records by name",
            { q -> "https://www.jailbase.com/search/#as_sfid=${Uri.encode(q)}" },
            setOf("person")),
        OsintTool("PublicRecordsOnline", "Aggregated public records by US state",
            { _ -> "https://www.publicrecordsonline.com/" },
            setOf("person")),
        OsintTool("FamilySearch", "Genealogy and historical records — ancestors and relatives",
            { q -> "https://www.familysearch.org/search/record/results?q.givenName=${Uri.encode(q.substringBefore(" "))}&q.surname=${Uri.encode(q.substringAfterLast(" "))}" },
            setOf("person")),
        OsintTool("LinkedIn (People)", "Professional profile and employment history",
            { q -> "https://www.linkedin.com/search/results/people/?keywords=${Uri.encode(q)}" },
            setOf("person")),
        OsintTool("PeekYou", "Links social profiles, web presence, and contact data",
            { q -> "https://www.peekyou.com/${q.replace(" ", "_")}" },
            setOf("person")),

        // ── EMAIL ───────────────────────────────────────────────────────────────
        OsintTool("HaveIBeenPwned", "Check email against 12B+ breached accounts",
            { q -> "https://haveibeenpwned.com/account/${Uri.encode(q)}" },
            setOf("email")),
        OsintTool("Hunter.io", "Find email addresses tied to a domain",
            { q -> "https://hunter.io/email-verifier/${Uri.encode(q)}" },
            setOf("email", "company")),
        OsintTool("Epieos", "Reverse email: Google account info, social profiles",
            { q -> "https://epieos.com/?q=${Uri.encode(q)}&t=email" },
            setOf("email")),
        OsintTool("EmailRep.io", "Email reputation score, risk flags, and breach status",
            { q -> "https://emailrep.io/${Uri.encode(q)}" },
            setOf("email")),
        OsintTool("LeakCheck.io", "Breach database with plaintext password exposure check",
            { q -> "https://leakcheck.io/api/public?check=${Uri.encode(q)}" },
            setOf("email")),
        OsintTool("Gravatar", "Avatar and profile linked to an email address",
            { q -> "https://en.gravatar.com/${q.trim().lowercase().replace(" ", "")}" },
            setOf("email")),
        OsintTool("Mail.tm / TempMail", "Identify disposable email providers",
            { _ -> "https://temp-mail.org/" },
            setOf("email")),

        // ── PHONE ───────────────────────────────────────────────────────────────
        OsintTool("TrueCaller", "Global phone number owner lookup and spam database",
            { q -> "https://www.truecaller.com/search/us/${q.replace(Regex("[^0-9]"), "")}" },
            setOf("phone")),
        OsintTool("WhoCalledMe", "US reverse phone lookup with caller reports",
            { q -> "https://www.whocalledme.com/PhoneNumber/${q.replace(Regex("[^0-9]"), "")}" },
            setOf("phone")),
        OsintTool("CallerID Test", "Free reverse phone lookup aggregator",
            { q -> "https://calleridtest.com/phonenumber/${q.replace(Regex("[^0-9]"), "")}" },
            setOf("phone")),
        OsintTool("800notes", "US caller reports and spam phone database",
            { q -> "https://800notes.com/Phone.aspx/${q.replace(Regex("[^0-9]"), "")}" },
            setOf("phone")),
        OsintTool("PhoneInfoga", "International phone OSINT — carrier, region, reputation",
            { q -> "https://demo.phoneinfoga.crvx.fr/#/numbers/${Uri.encode(q)}/run" },
            setOf("phone")),
        OsintTool("Numverify", "Phone carrier, line type, and validity check",
            { q -> "https://numverify.com/" },
            setOf("phone")),

        // ── USERNAME ────────────────────────────────────────────────────────────
        OsintTool("WhatsMyName", "Check username across 500+ sites instantly",
            { q -> "https://whatsmyname.app/?q=${Uri.encode(q)}" },
            setOf("username")),
        OsintTool("Namecheckr", "Username availability and profile finder across platforms",
            { q -> "https://www.namecheckr.com/${Uri.encode(q)}" },
            setOf("username")),
        OsintTool("NameCheckup", "Check username on 100+ social networks",
            { q -> "https://namecheckup.com/${Uri.encode(q)}" },
            setOf("username")),
        OsintTool("Instant Username Search", "Real-time username search across social media",
            { q -> "https://instantusername.com/#${Uri.encode(q)}" },
            setOf("username")),
        OsintTool("Sherlock (Termux)", "Command-line username hunter across 400+ sites",
            { q -> "https://github.com/sherlock-project/sherlock" },
            setOf("username")),
        OsintTool("Maigret (Termux)", "Advanced username OSINT with site analysis",
            { q -> "https://github.com/soxoj/maigret" },
            setOf("username")),
        OsintTool("IDCrawl", "People search using username, email, or name",
            { q -> "https://www.idcrawl.com/${Uri.encode(q)}" },
            setOf("username", "person")),

        // ── DOMAIN / IP ─────────────────────────────────────────────────────────
        OsintTool("Shodan", "Internet-connected device and open-port scanner",
            { q -> "https://www.shodan.io/search?query=${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("Censys", "Internet-wide host and certificate search",
            { q -> "https://search.censys.io/search?resource=hosts&q=${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("VirusTotal", "Scan domain/IP/URL for malware and threat indicators",
            { q -> "https://www.virustotal.com/gui/domain/${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("DNSDumpster", "DNS recon — subdomains, MX, TXT, host map",
            { _ -> "https://dnsdumpster.com/" },
            setOf("domain")),
        OsintTool("SecurityTrails", "Historical DNS, WHOIS, and subdomain enumeration",
            { q -> "https://securitytrails.com/domain/${Uri.encode(q)}/dns" },
            setOf("domain")),
        OsintTool("URLScan.io", "Website screenshot, links, and request analysis",
            { q -> "https://urlscan.io/search/#domain:${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("AbuseIPDB", "IP reputation — reported abuse, attacks, spam",
            { q -> "https://www.abuseipdb.com/check/${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("GreyNoise", "Internet scanner noise vs targeted attack detection",
            { q -> "https://viz.greynoise.io/ip/${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("AlienVault OTX", "Threat intelligence — IOCs, pulse feeds, reputation",
            { q -> "https://otx.alienvault.com/indicator/domain/${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("ThreatCrowd", "Domain / IP threat intelligence and passive DNS",
            { q -> "https://www.threatcrowd.org/domain.php?domain=${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("Robtex", "BGP routing, passive DNS, and IP intelligence",
            { q -> "https://www.robtex.com/dns-lookup/${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("crt.sh", "SSL/TLS certificate transparency log search",
            { q -> "https://crt.sh/?q=${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("Wayback Machine", "Archived web snapshots — deleted pages, old content",
            { q -> "https://web.archive.org/web/*/${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("FOFA", "Chinese internet asset search engine (IoT, services)",
            { q -> "https://fofa.info/result?qbase64=${android.util.Base64.encodeToString(q.toByteArray(), android.util.Base64.NO_WRAP)}" },
            setOf("domain")),
        OsintTool("ZoomEye", "Cyberspace search engine — devices, services, vulnerabilities",
            { q -> "https://www.zoomeye.org/searchResult?q=${Uri.encode(q)}" },
            setOf("domain")),
        OsintTool("HackerTarget", "Network tools — reverse IP, DNS lookup, port scan",
            { q -> "https://hackertarget.com/ip-tools/" },
            setOf("domain")),
        OsintTool("IPinfo.io", "IP geolocation, ASN, company, and hosting info",
            { q -> "https://ipinfo.io/${Uri.encode(q)}" },
            setOf("domain")),

        // ── COMPANY ─────────────────────────────────────────────────────────────
        OsintTool("OpenCorporates", "Global corporate registry — officers, filings, addresses",
            { q -> "https://opencorporates.com/companies?q=${Uri.encode(q)}" },
            setOf("company")),
        OsintTool("Crunchbase", "Startup and corporate funding, acquisitions, and team",
            { q -> "https://www.crunchbase.com/textsearch?q=${Uri.encode(q)}" },
            setOf("company")),
        OsintTool("SEC EDGAR", "US public company filings — 10-K, 8-K, ownership",
            { q -> "https://efts.sec.gov/LATEST/search-index?q=${Uri.encode(q)}&dateRange=custom" },
            setOf("company")),
        OsintTool("LinkedIn (Company)", "Employee count, leadership, and job postings",
            { q -> "https://www.linkedin.com/search/results/companies/?keywords=${Uri.encode(q)}" },
            setOf("company")),
        OsintTool("BuiltWith", "Website technology stack and framework detection",
            { q -> "https://builtwith.com/${Uri.encode(q)}" },
            setOf("company", "domain")),
        OsintTool("Hunter.io (Domain)", "Company email pattern and employee email finder",
            { q -> "https://hunter.io/domain-search/${Uri.encode(q)}" },
            setOf("company")),
        OsintTool("Glassdoor", "Employee reviews, salaries, and company insights",
            { q -> "https://www.glassdoor.com/Search/results.htm?keyword=${Uri.encode(q)}" },
            setOf("company")),
        OsintTool("SimilarWeb", "Website traffic, audience, and competitor analysis",
            { q -> "https://www.similarweb.com/website/${Uri.encode(q)}" },
            setOf("company", "domain")),

        // ── IMAGE / FACE ────────────────────────────────────────────────────────
        OsintTool("Google Lens", "Reverse image search — objects, places, text, faces",
            { _ -> "https://lens.google.com/" },
            setOf("image")),
        OsintTool("TinEye", "Reverse image search — exact and modified matches",
            { _ -> "https://tineye.com/" },
            setOf("image")),
        OsintTool("Yandex Images", "Russian reverse image search — excellent for faces",
            { _ -> "https://yandex.com/images/" },
            setOf("image")),
        OsintTool("FaceCheck.id", "Facial recognition reverse image search",
            { _ -> "https://facecheck.id/" },
            setOf("image")),
        OsintTool("Search4Faces", "Face search across Russian social networks (VK, OK)",
            { _ -> "https://search4faces.com/" },
            setOf("image")),
        OsintTool("PimEyes", "AI-powered facial recognition across the public web",
            { _ -> "https://pimeyes.com/en" },
            setOf("image")),
        OsintTool("Bing Visual Search", "Microsoft reverse image with scene detection",
            { _ -> "https://www.bing.com/visualsearch" },
            setOf("image")),

        // ── SOCIAL MEDIA ────────────────────────────────────────────────────────
        OsintTool("Social Searcher", "Real-time public post search across all platforms",
            { q -> "https://www.social-searcher.com/social-buzz/?q5=${Uri.encode(q)}" },
            setOf("social")),
        OsintTool("Twitter/X Advanced", "Advanced tweet and account search operators",
            { q -> "https://twitter.com/search?q=${Uri.encode(q)}&f=live" },
            setOf("social", "username")),
        OsintTool("Instagram Search", "Instagram profile and hashtag search",
            { q -> "https://www.instagram.com/${Uri.encode(q)}/" },
            setOf("social", "username")),
        OsintTool("Reddit Search", "Reddit post and user search via Pullpush",
            { q -> "https://www.reddit.com/search/?q=${Uri.encode(q)}&sort=new" },
            setOf("social")),
        OsintTool("TGStat", "Telegram channel and message search",
            { q -> "https://tgstat.com/search?q=${Uri.encode(q)}" },
            setOf("social")),
        OsintTool("Snapchat Maps", "Public Snapchat geotagged stories on map",
            { _ -> "https://map.snapchat.com/" },
            setOf("social")),
        OsintTool("YouTube Search", "Video and channel search — metadata and descriptions",
            { q -> "https://www.youtube.com/results?search_query=${Uri.encode(q)}" },
            setOf("social")),
        OsintTool("VK Search", "Russian social network profile and post search",
            { q -> "https://vk.com/search?c[q]=${Uri.encode(q)}&c[section]=people" },
            setOf("social", "username"))
    )

    private val selectedTools = mutableSetOf<OsintTool>()
    private val checkBoxMap = mutableMapOf<OsintTool, CheckBox>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOsintResourcesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val prefilledQuery = arguments?.getString("query") ?: ""
        if (prefilledQuery.isNotBlank()) {
            binding.inputHubQuery.setText(prefilledQuery)
        }

        buildResourceCards()

        binding.btnSelectAll.setOnClickListener {
            checkBoxMap.forEach { (tool, cb) ->
                cb.isChecked = true
                selectedTools.add(tool)
            }
        }

        binding.btnClearSelection.setOnClickListener {
            checkBoxMap.forEach { (tool, cb) ->
                cb.isChecked = false
            }
            selectedTools.clear()
        }

        binding.btnLaunchSelected.setOnClickListener {
            val query = binding.inputHubQuery.text?.toString()?.trim() ?: ""
            if (selectedTools.isEmpty()) {
                Toast.makeText(requireContext(), "Select at least one tool first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (query.isBlank()) {
                Toast.makeText(requireContext(), "Enter a target query above", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            launchSelectedTools(query)
        }

        binding.inputHubQuery.setOnEditorActionListener { _, _, _ ->
            val query = binding.inputHubQuery.text?.toString()?.trim() ?: ""
            if (selectedTools.isNotEmpty() && query.isNotBlank()) launchSelectedTools(query)
            true
        }
    }

    private fun buildResourceCards() {
        val container = binding.resourcesContainer
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        categories.forEach { category ->
            val tools = allTools.filter { category.id in it.categories }
            if (tools.isEmpty()) return@forEach

            val sectionLabel = TextView(ctx).apply {
                text = "${category.icon}  ${category.label.uppercase()}"
                textSize = 10f
                letterSpacing = 0.14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(16f)
                lp.bottomMargin = dp(6f)
                layoutParams = lp
            }
            container.addView(sectionLabel)

            val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(4f)
                layoutParams = lp
                setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
                radius = dp(12f).toFloat()
                strokeColor = ContextCompat.getColor(ctx, R.color.border)
                strokeWidth = ctx.resources.displayMetrics.density.toInt()
                cardElevation = 0f
            }

            val cardContent = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
            }

            tools.forEachIndexed { index, tool ->
                val row = buildToolRow(tool, dp(16f))
                cardContent.addView(row)
                if (index < tools.lastIndex) {
                    cardContent.addView(buildDivider())
                }
            }

            card.addView(cardContent)
            container.addView(card)
        }
    }

    private fun buildToolRow(tool: OsintTool, paddingPx: Int): View {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(paddingPx, dp(12f), paddingPx, dp(12f))
            isClickable = true
            isFocusable = true
            background = android.util.TypedValue().also { v ->
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
            }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
        }

        val cb = CheckBox(ctx).apply {
            isChecked = tool in selectedTools
            buttonTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.accent_cyan))
            val lp = LinearLayout.LayoutParams(dp(24f), dp(24f))
            lp.marginEnd = dp(12f)
            layoutParams = lp
            setOnCheckedChangeListener { _, checked ->
                if (checked) selectedTools.add(tool) else selectedTools.remove(tool)
            }
        }
        checkBoxMap[tool] = cb

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameTv = TextView(ctx).apply {
            text = tool.name
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        }

        val descTv = TextView(ctx).apply {
            text = tool.description
            textSize = 11f
            isSingleLine = false
            maxLines = 2
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }

        textCol.addView(nameTv)
        textCol.addView(descTv)

        val launchBtn = TextView(ctx).apply {
            text = "↗"
            textSize = 18f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_dim))
            setPadding(dp(8f), 0, 0, 0)
        }

        row.addView(cb)
        row.addView(textCol)
        row.addView(launchBtn)

        row.setOnClickListener {
            val query = binding.inputHubQuery.text?.toString()?.trim() ?: ""
            openUrl(tool.buildUrl(query.ifBlank { "example" }))
        }

        return row
    }

    private fun launchSelectedTools(query: String) {
        var launched = 0
        selectedTools.toList().forEach { tool ->
            try {
                val url = tool.buildUrl(query)
                openUrl(url)
                launched++
            } catch (_: Exception) { }
        }
        Toast.makeText(requireContext(), "Launched $launched tool${if (launched != 1) "s" else ""} — check your browser tabs", Toast.LENGTH_LONG).show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "No browser found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildDivider(): View {
        val ctx = requireContext()
        return View(ctx).apply {
            val density = ctx.resources.displayMetrics.density
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).also {
                it.marginStart = (16 * density).toInt()
                it.marginEnd = (16 * density).toInt()
            }
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.border))
            alpha = 0.5f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(query: String) = OsintResourcesFragment().apply {
            arguments = Bundle().also { it.putString("query", query) }
        }
    }
}
