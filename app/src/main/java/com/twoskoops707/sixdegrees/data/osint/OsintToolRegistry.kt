package com.twoskoops707.sixdegrees.data.osint

import android.content.Context
import com.twoskoops707.sixdegrees.data.BlockedSourceCache

object OsintToolRegistry {

    fun domainFromUrl(url: String): String = BlockedSourceCache.domainFromUrl(url)

    fun isUrlBlocked(context: Context, url: String): Boolean =
        BlockedSourceCache(context).isUrlBlocked(url)

    data class OsintTool(
        val id: String,
        val name: String,
        val description: String,
        val urlTemplate: String,
        val categories: Set<String>,
        val frameworkPath: List<String> = emptyList(),
        val inputHint: String = "",
        val isQueryable: Boolean = false
    )

    fun buildUrl(template: String, query: String): String =
        OsintUrlBuilder.buildUrl(template, query)

    private const val RELEVANT_TOOLS_PER_CATEGORY = 20

    @Volatile
    private var cachedTools: List<OsintTool>? = null

    fun ensureLoaded(context: Context): List<OsintTool> {
        cachedTools?.let { return it }
        synchronized(this) {
            cachedTools?.let { return it }
            val repo = OsintFrameworkRepository.getInstance(searchTemplateOverrides())
            val loaded = repo.loadTools(context.applicationContext).map { it.toRegistryTool() }
            cachedTools = loaded
            return loaded
        }
    }

    val allTools: List<OsintTool>
        get() = cachedTools.orEmpty()

    fun toolsForCategory(context: Context, categoryId: String): List<OsintTool> =
        ensureLoaded(context).filter { categoryId in it.categories }

    fun searchTools(context: Context, query: String, categoryId: String? = null): List<OsintTool> {
        val needle = query.trim().lowercase()
        return ensureLoaded(context).filter { tool ->
            (categoryId == null || categoryId == "all" || categoryId in tool.categories) &&
                (needle.isBlank() ||
                    tool.name.lowercase().contains(needle) ||
                    tool.description.lowercase().contains(needle) ||
                    tool.frameworkPath.any { it.lowercase().contains(needle) })
        }
    }

    fun relevantTools(context: Context, type: String): LinkedHashMap<String, List<OsintTool>> {
        val categories = OsintFrameworkCategoryMapper.typeToCategories[type] ?: listOf("person", "social")
        return rankToolsForSearch(ensureLoaded(context), categories, type)
    }

    /** Back-compat for callers without Context — returns empty until [ensureLoaded]. */
    fun relevantTools(type: String): LinkedHashMap<String, List<OsintTool>> {
        val categories = OsintFrameworkCategoryMapper.typeToCategories[type] ?: listOf("person", "social")
        val tools = cachedTools.orEmpty()
        if (tools.isEmpty()) return LinkedHashMap()
        return rankToolsForSearch(tools, categories, type)
    }

    private fun rankToolsForSearch(
        tools: List<OsintTool>,
        categories: List<String>,
        searchType: String
    ): LinkedHashMap<String, List<OsintTool>> {
        val result = LinkedHashMap<String, List<OsintTool>>()
        val seen = mutableSetOf<String>()
        for (cat in categories) {
            val label = OsintFrameworkCategoryMapper.categoryLabels[cat] ?: cat
            val section = tools
                .filter { cat in it.categories && it.name !in seen }
                .sortedWith(
                    compareByDescending<OsintTool> { it.isQueryable }
                        .thenByDescending {
                            OsintFrameworkCategoryMapper.inputMatchesSearchType(it.inputHint, searchType)
                        }
                        .thenBy { it.name.lowercase() }
                )
                .take(RELEVANT_TOOLS_PER_CATEGORY)
            if (section.isNotEmpty()) {
                section.forEach { seen.add(it.name) }
                result[label] = section
            }
        }
        return result
    }

    fun resetForTests() {
        cachedTools = null
        OsintFrameworkRepository.resetForTests()
    }

    private fun OsintFrameworkRepository.FrameworkTool.toRegistryTool() = OsintTool(
        id = id,
        name = name,
        description = description,
        urlTemplate = urlTemplate,
        categories = categories,
        frameworkPath = frameworkPath,
        inputHint = inputHint,
        isQueryable = isQueryable
    )

    /**
     * Search URL templates for high-value tools. Keys are normalized names from [OsintUrlBuilder.normalizeToolName].
     * Framework static URLs are replaced when a better query template exists.
     */
    private fun searchTemplateOverrides(): Map<String, String> = mapOf(
        "fastpeoplesearch" to "https://www.fastpeoplesearch.com/name/{q-hyphen}",
        "truepeoplesearch" to "https://www.truepeoplesearch.com/results?name={q-encoded}",
        "whitepages" to "https://www.whitepages.com/name/{q-hyphen}",
        "spokeo" to "https://www.spokeo.com/{q-plus}",
        "peekyou" to "https://www.peekyou.com/{q-underscore}",
        "beenverified" to "https://www.beenverified.com/f/search/person?q={q-encoded}",
        "idcrawl" to "https://www.idcrawl.com/{q-encoded}",
        "webmii" to "https://webmii.com/people?n={q-encoded}",
        "familytreenow" to "https://www.familytreenow.com/search/genealogy/results?fn={first}&ln={last}",
        "thatsthem" to "https://thatsthem.com/name/{q-hyphen}",
        "zabasearch" to "https://www.zabasearch.com/people/{q-hyphen}/",
        "radaris" to "https://radaris.com/p/{first}/{last}/",
        "courtlistener" to "https://www.courtlistener.com/?q={q-encoded}&type=p",
        "judyrecords" to "https://www.judyrecords.com/search?search={q-encoded}",
        "intelius" to "https://www.intelius.com/people-search/name/{q-plus}",
        "linkedinpeople" to "https://www.linkedin.com/search/results/people/?keywords={q-encoded}",
        "nsopw" to "https://www.nsopw.gov/Search/Results?firstName={first}&lastName={last}",
        "opensanctions" to "https://www.opensanctions.org/search/?q={q-encoded}",
        "pipl" to "https://pipl.com/search/?q={q-encoded}",
        "411com" to "https://www.411.com/name/{q-hyphen}",
        "haveibeenpwned" to "https://haveibeenpwned.com/account/{q-encoded}",
        "epieosemailtool" to "https://epieos.com/?q={q-encoded}&t=email",
        "hunter" to "https://hunter.io/email-verifier/{q-encoded}",
        "emailrepio" to "https://emailrep.io/{q-encoded}",
        "intelligencex" to "https://intelx.io/?s={q-encoded}",
        "dehashed" to "https://dehashed.com/search?query={q-encoded}",
        "phonebookcz" to "https://phonebook.cz/?q={q-encoded}&t=2",
        "emailformat" to "https://www.email-format.com/i/search/?q={q-encoded}",
        "truecaller" to "https://www.truecaller.com/search/us/{q-digits}",
        "whocalledme" to "https://www.whocalledme.com/PhoneNumber/{q-digits}",
        "800notes" to "https://800notes.com/Phone.aspx/{q-digits}",
        "phoneinfoga" to "https://demo.phoneinfoga.crvx.fr/#/numbers/{q-encoded}/run",
        "numlooker" to "https://www.numlooker.com/{q-digits}",
        "calleridtest" to "https://calleridtest.com/phonenumber/{q-digits}",
        "anywho" to "https://www.anywho.com/reverse-lookup/{q-digits}",
        "whatsmyname" to "https://whatsmyname.app/?q={q-encoded}",
        "namecheckup" to "https://namecheckup.com/{q-encoded}",
        "namecheckr" to "https://www.namecheckr.com/{q-encoded}",
        "knowem" to "https://knowem.com/checkusernames.php?u={q-encoded}",
        "checkusernames" to "https://checkusernames.com/?q={q-encoded}",
        "twitterxprofile" to "https://twitter.com/{q-raw}",
        "instagramprofile" to "https://www.instagram.com/{q-raw}/",
        "redditprofile" to "https://www.reddit.com/user/{q-raw}",
        "tiktokprofile" to "https://www.tiktok.com/@{q-raw}",
        "githubprofile" to "https://github.com/{q-raw}",
        "twitchprofile" to "https://www.twitch.tv/{q-raw}",
        "shodan" to "https://www.shodan.io/search?query={q-encoded}",
        "censys" to "https://search.censys.io/search?resource=hosts&q={q-encoded}",
        "virustotal" to "https://www.virustotal.com/gui/domain/{q-raw}",
        "securitytrails" to "https://securitytrails.com/domain/{q-raw}/dns",
        "urlscanio" to "https://urlscan.io/search/#domain:{q-raw}",
        "abuseipdb" to "https://www.abuseipdb.com/check/{q-raw}",
        "greynoise" to "https://viz.greynoise.io/ip/{q-raw}",
        "alienvaultotx" to "https://otx.alienvault.com/indicator/domain/{q-raw}",
        "robtex" to "https://www.robtex.com/dns-lookup/{q-raw}",
        "crtsh" to "https://crt.sh/?q={q-encoded}",
        "waybackmachine" to "https://web.archive.org/web/*/{q-raw}",
        "ipinfoio" to "https://ipinfo.io/{q-raw}",
        "viewdnsinfo" to "https://viewdns.info/reverseip/?host={q-raw}&t=1",
        "hackertarget" to "https://hackertarget.com/reverse-ip-lookup/?q={q-raw}",
        "leakix" to "https://leakix.net/search?scope=leak&q={q-encoded}",
        "zoomeye" to "https://www.zoomeye.org/searchResult?q={q-encoded}",
        "netcraft" to "https://sitereport.netcraft.com/?url={q-encoded}",
        "builtwith" to "https://builtwith.com/{q-raw}",
        "similarweb" to "https://www.similarweb.com/website/{q-raw}",
        "whois" to "https://who.is/whois/{q-raw}",
        "mxtoolboxdns" to "https://mxtoolbox.com/SuperTool.aspx?action=a%3a{q-raw}&run=toolpage",
        "publicwww" to "https://publicwww.com/websites/{q-encoded}/",
        "opencorporates" to "https://opencorporates.com/companies?q={q-encoded}",
        "crunchbase" to "https://www.crunchbase.com/textsearch?q={q-encoded}",
        "secedgar" to "https://efts.sec.gov/LATEST/search-index?q={q-encoded}",
        "linkedincompany" to "https://www.linkedin.com/search/results/companies/?keywords={q-encoded}",
        "glassdoor" to "https://www.glassdoor.com/Search/results.htm?keyword={q-encoded}",
        "companieshouse" to "https://find-and-update.company-information.service.gov.uk/search?q={q-encoded}",
        "corporationwiki" to "https://www.corporationwiki.com/search/results?term={q-encoded}",
        "littlesis" to "https://littlesis.org/search?q={q-encoded}",
        "icijoffshoreleaks" to "https://offshoreleaks.icij.org/search?q={q-encoded}",
        "socialsearcher" to "https://www.social-searcher.com/social-buzz/?q5={q-encoded}",
        "twitterxadvanced" to "https://twitter.com/search?q={q-encoded}&f=live",
        "tiktoksearch" to "https://www.tiktok.com/search?q={q-encoded}",
        "youtubesearch" to "https://www.youtube.com/results?search_query={q-encoded}",
        "tgstat" to "https://tgstat.com/search?q={q-encoded}",
        "vksearch" to "https://vk.com/search?c[q]={q-encoded}&c[section]=people",
        "disboard" to "https://disboard.org/search?keyword={q-encoded}",
        "twitchsearch" to "https://www.twitch.tv/search?term={q-encoded}",
        "nitter" to "https://nitter.net/search?q={q-encoded}",
        "socialgrep" to "https://socialgrep.com/search?query={q-encoded}",
        "vindecoderz" to "https://www.vindecoderz.com/EN/check-lookup/{q-raw}",
        "faxvin" to "https://www.faxvin.com/{q-raw}",
        "vehiclehistory" to "https://www.vehiclehistory.com/vin-report/{q-raw}",
        "autocheck" to "https://www.autocheck.com/vehiclehistory/autocheck/en/vehiclecheck?vin={q-raw}",
        "ddosecrets" to "https://ddosecrets.com/wiki/Special:Search?search={q-encoded}&ns0=1",
        "ahmia" to "https://ahmia.fi/search/?q={q-encoded}",
        "onionsearch" to "https://onionsearchengine.com/search.php?search={q-encoded}",
        "googlemaps" to "https://www.google.com/maps/search/{q-encoded}",
        "yandexmaps" to "https://yandex.com/maps/?text={q-encoded}",
        "openstreetmap" to "https://www.openstreetmap.org/search?query={q-encoded}",
        "wikimapia" to "https://wikimapia.org/#lang=en&q={q-encoded}",
        "geonames" to "https://www.geonames.org/search.html?q={q-encoded}",
        "flightaware" to "https://flightaware.com/live/flight/{q-raw}",
        "muckrock" to "https://www.muckrock.com/search/?q={q-encoded}",
        "ppploandatabase" to "https://ppp.directory/search?q={q-encoded}",
        "nonprofitexplorer" to "https://projects.propublica.org/nonprofits/search?q={q-encoded}",
        "familysearch" to "https://www.familysearch.org/search/record/results?q.givenName={first}&q.surname={last}",
        "findagrave" to "https://www.findagrave.com/memorial/search?q={q-encoded}",
        "hybridanalysis" to "https://www.hybrid-analysis.com/search?query={q-encoded}",
        "pulsedive" to "https://pulsedive.com/search/?q={q-encoded}",
        "threatminer" to "https://www.threatminer.org/host.php?q={q-encoded}",
        "sploitus" to "https://sploitus.com/?query={q-encoded}",
        "usersearch" to "https://usersearch.org/results_normal/?q={q-encoded}",
        "zlookup" to "https://www.zlookup.com/phone-lookup/{q-digits}",
        "allpeople" to "https://allpeople.com/search?ss={q-encoded}",
        "yasni" to "https://www.yasni.com/{first}/check+{last}",
        "nuwber" to "https://nuwber.com/name/{q-hyphen}",
        "192com" to "https://www.192.com/atoz/people/{q-hyphen}/",
        "canada411" to "https://www.canada411.ca/search/?stype=si&what={q-encoded}",
        "namechk" to "https://namechk.com/{q-raw}",
        "analyzeid" to "https://analyzeid.com/username/?u={q-raw}",
        "archivetoday" to "https://archive.ph/?run=1&url={q-encoded}",
        "infobel" to "https://www.infobel.com/en/world/search?q={q-encoded}",
        "xlek" to "https://www.xlek.com/result.php?fname={first}&lname={last}",
        "ufindname" to "https://ufind.name/search?q={q-encoded}",
        "gofindwho" to "https://gofindwho.com/people/{q-hyphen}",
        "validnumber" to "https://validnumber.com/phone/{q-digits}",
        "reversephonecheck" to "https://www.reversephonecheck.com/{q-digits}",
        "calltracer" to "https://calltracer.io/search?q={q-digits}",
        "oldphonebook" to "http://www.oldphonebook.com/search.php?name={q-encoded}",
        "explainShell" to "https://explainshell.com/explain?cmd={q-encoded}",
        "emailreputation" to "https://emailrep.io/{q-encoded}",
        "hudsonrock" to "https://www.hudsonrock.com/threat-intelligence-cybercrime-tools?search={q-encoded}",
        "breachvip" to "https://breach.vip/?query={q-encoded}",
        "osintindustries" to "https://www.osint.industries/?query={q-encoded}"
    )
}
