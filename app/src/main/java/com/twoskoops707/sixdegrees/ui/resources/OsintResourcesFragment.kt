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
import com.google.android.material.chip.Chip
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentOsintResourcesBinding

class OsintResourcesFragment : Fragment() {

    private var _binding: FragmentOsintResourcesBinding? = null
    private val binding get() = _binding!!

    private var currentFilter = "all"

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
        OsintCategory("person",   "Person",        "", "Public records, people search engines, criminal and court records"),
        OsintCategory("email",    "Email",         "", "Breach history, reputation, deliverability, linked accounts"),
        OsintCategory("phone",    "Phone",         "", "Carrier lookup, owner ID, reverse search, spam reports"),
        OsintCategory("username", "Username",      "", "Cross-platform presence: social, gaming, forums, dating"),
        OsintCategory("domain",   "Domain / IP",   "", "WHOIS, DNS, open ports, SSL certs, threat intel, archives"),
        OsintCategory("company",  "Company",       "", "Corporate registry, filings, officers, tech stack, financials"),
        OsintCategory("image",    "Image / Face",  "", "Reverse image and facial recognition across the web"),
        OsintCategory("social",   "Social Media",  "", "Deep search across Twitter/X, Instagram, Reddit, Telegram"),
        OsintCategory("vehicle",  "Vehicle / VIN", "", "VIN decode, history reports, license plate lookup"),
        OsintCategory("breach",   "Breaches",      "", "Leaked credentials, breach databases, data exposure"),
        OsintCategory("darknet",  "Dark Web",      "", "Tor indexes, ransomware trackers, onion search engines"),
        OsintCategory("geo",      "Geolocation",   "", "Satellite, street view, WiFi positioning, ship and flight tracking"),
        OsintCategory("finance",  "Financial",     "", "SEC filings, offshore leaks, government salaries, FOIA"),
        OsintCategory("records",  "Public Records","", "Court records, genealogy, death index, police accountability"),
        OsintCategory("threat",   "Threat Intel",  "", "Malware sandboxes, IOC feeds, CVE and exploit search"),
        OsintCategory("tools",    "Tools",         "", "OSINT utilities, encoders, archivers, hash crackers")
    )

    private fun buildUrl(template: String, query: String): String {
        val first = Uri.encode(query.substringBefore(" ").trim())
        val last = Uri.encode(query.substringAfterLast(" ").trim())
        return template
            .replace("{q-encoded}", Uri.encode(query))
            .replace("{q-hyphen}", query.replace(" ", "-"))
            .replace("{q-plus}", query.replace(" ", "+"))
            .replace("{q-underscore}", query.replace(" ", "_"))
            .replace("{q-digits}", query.replace(Regex("[^0-9+]"), ""))
            .replace("{q-raw}", query)
            .replace("{first}", first)
            .replace("{last}", last)
    }

    private val allTools = listOf(

        // ── PERSON ─────────────────────────────────────────────────────────────
        OsintTool("FastPeopleSearch", "Free US people search",
            { q -> buildUrl("https://www.fastpeoplesearch.com/name/{q-hyphen}", q) },
            setOf("person")),
        OsintTool("TruePeopleSearch", "Full profile with address history",
            { q -> buildUrl("https://www.truepeoplesearch.com/results?name={q-encoded}", q) },
            setOf("person")),
        OsintTool("WhitePages", "US name/phone/address lookup",
            { q -> buildUrl("https://www.whitepages.com/name/{q-hyphen}", q) },
            setOf("person")),
        OsintTool("Spokeo", "Social + public + contact aggregator",
            { q -> buildUrl("https://www.spokeo.com/{q-plus}", q) },
            setOf("person")),
        OsintTool("PeekYou", "Social profiles linked to real identity",
            { q -> buildUrl("https://www.peekyou.com/{q-underscore}", q) },
            setOf("person")),
        OsintTool("BeenVerified", "Background check — employment, criminal",
            { q -> buildUrl("https://www.beenverified.com/f/search/person?q={q-encoded}", q) },
            setOf("person")),
        OsintTool("IDCrawl", "People search via name/username/email",
            { q -> buildUrl("https://www.idcrawl.com/{q-encoded}", q) },
            setOf("person", "username")),
        OsintTool("WebMii", "Web presence and social profile linker",
            { q -> buildUrl("https://webmii.com/people?n={q-encoded}", q) },
            setOf("person")),
        OsintTool("FamilyTree Now", "Genealogy + relatives + address history",
            { q -> buildUrl("https://www.familytreenow.com/search/genealogy/results?fn={first}&ln={last}", q) },
            setOf("person")),
        OsintTool("ThatsThem", "Reverse lookup — name, phone, email, IP",
            { q -> buildUrl("https://thatsthem.com/name/{q-hyphen}", q) },
            setOf("person")),
        OsintTool("ZabaSearch", "Free US people search",
            { q -> buildUrl("https://www.zabasearch.com/people/{q-hyphen}/", q) },
            setOf("person")),
        OsintTool("Radaris", "Deep people search aggregator",
            { q -> buildUrl("https://radaris.com/p/{first}/{last}/", q) },
            setOf("person")),
        OsintTool("CourtListener", "US federal and state court records",
            { q -> buildUrl("https://www.courtlistener.com/?q={q-encoded}&type=p", q) },
            setOf("person", "records")),
        OsintTool("JudyRecords", "US court case records by name",
            { q -> buildUrl("https://www.judyrecords.com/search?search={q-encoded}", q) },
            setOf("person", "records")),
        OsintTool("Intelius", "Comprehensive background reports",
            { q -> buildUrl("https://www.intelius.com/people-search/name/{q-plus}", q) },
            setOf("person")),
        OsintTool("LinkedIn People", "Professional profiles and employment",
            { q -> buildUrl("https://www.linkedin.com/search/results/people/?keywords={q-encoded}", q) },
            setOf("person", "company")),
        OsintTool("NSOPW", "National sex offender public registry",
            { q -> buildUrl("https://www.nsopw.gov/Search/Results?firstName={first}&lastName={last}", q) },
            setOf("person", "records")),
        OsintTool("OpenSanctions", "Sanctions, PEPs, and crime databases",
            { q -> buildUrl("https://www.opensanctions.org/search/?q={q-encoded}", q) },
            setOf("person", "threat")),
        OsintTool("Pipl", "Deep web person search engine",
            { q -> buildUrl("https://pipl.com/search/?q={q-encoded}", q) },
            setOf("person")),
        OsintTool("411.com", "US white pages reverse lookup",
            { q -> buildUrl("https://www.411.com/name/{q-hyphen}", q) },
            setOf("person")),

        // ── EMAIL ───────────────────────────────────────────────────────────────
        OsintTool("HaveIBeenPwned", "Check 12B+ breached accounts",
            { q -> buildUrl("https://haveibeenpwned.com/account/{q-encoded}", q) },
            setOf("email", "breach")),
        OsintTool("Epieos", "Reverse email: Google account, social profiles",
            { q -> buildUrl("https://epieos.com/?q={q-encoded}&t=email", q) },
            setOf("email")),
        OsintTool("Hunter.io", "Email verifier and deliverability check",
            { q -> buildUrl("https://hunter.io/email-verifier/{q-encoded}", q) },
            setOf("email", "company")),
        OsintTool("EmailRep.io", "Email reputation, risk flags, breach status",
            { q -> buildUrl("https://emailrep.io/{q-encoded}", q) },
            setOf("email")),
        OsintTool("Intelligence X", "Deep web email search and breach data",
            { q -> buildUrl("https://intelx.io/?s={q-encoded}", q) },
            setOf("email", "breach", "darknet")),
        OsintTool("DeHashed", "Breach database search",
            { q -> buildUrl("https://dehashed.com/search?query={q-encoded}", q) },
            setOf("email", "breach")),
        OsintTool("Holehe", "Check email registration across 120+ sites",
            { _ -> "https://github.com/megadose/holehe" },
            setOf("email")),
        OsintTool("Phonebook.cz", "Email, domain, and URL search engine",
            { q -> buildUrl("https://phonebook.cz/?q={q-encoded}&t=2", q) },
            setOf("email", "domain")),
        OsintTool("Email-format.com", "Find email formats for any company",
            { q -> buildUrl("https://www.email-format.com/i/search/?q={q-encoded}", q) },
            setOf("email", "company")),
        OsintTool("MXToolbox", "Email header analyzer and deliverability",
            { _ -> "https://mxtoolbox.com/EmailHeaders.aspx" },
            setOf("email", "domain")),
        OsintTool("Snusbase", "Breach database with indexed passwords",
            { _ -> "https://snusbase.com/" },
            setOf("email", "breach")),
        OsintTool("LeakCheck.io", "Leaked credential checker",
            { _ -> "https://leakcheck.io/" },
            setOf("email", "breach")),
        OsintTool("Cybernews Leak Check", "Personal data leak checker",
            { _ -> "https://cybernews.com/personal-data-leak-check/" },
            setOf("email", "breach")),
        OsintTool("GHunt", "OSINT on Google accounts via email",
            { _ -> "https://github.com/mxrch/GHunt" },
            setOf("email")),
        OsintTool("Tovi", "Email lookup and social recovery",
            { _ -> "https://tovi.io/" },
            setOf("email")),

        // ── PHONE ───────────────────────────────────────────────────────────────
        OsintTool("TrueCaller", "Global phone owner lookup + spam DB",
            { q -> buildUrl("https://www.truecaller.com/search/us/{q-digits}", q) },
            setOf("phone")),
        OsintTool("WhoCalledMe", "US reverse phone + caller reports",
            { q -> buildUrl("https://www.whocalledme.com/PhoneNumber/{q-digits}", q) },
            setOf("phone")),
        OsintTool("800notes", "Caller ID and spam phone reports",
            { q -> buildUrl("https://800notes.com/Phone.aspx/{q-digits}", q) },
            setOf("phone")),
        OsintTool("PhoneInfoga", "International phone OSINT tool",
            { q -> buildUrl("https://demo.phoneinfoga.crvx.fr/#/numbers/{q-encoded}/run", q) },
            setOf("phone")),
        OsintTool("SpyDialer", "Free reverse phone lookup",
            { _ -> "https://www.spydialer.com/default.aspx" },
            setOf("phone")),
        OsintTool("NumLooker", "Reverse phone lookup aggregator",
            { q -> buildUrl("https://www.numlooker.com/{q-digits}", q) },
            setOf("phone")),
        OsintTool("CallerID Test", "Free phone number lookup",
            { q -> buildUrl("https://calleridtest.com/phonenumber/{q-digits}", q) },
            setOf("phone")),
        OsintTool("SYNC.me", "Phone book and social profile linker",
            { _ -> "https://sync.me/" },
            setOf("phone")),
        OsintTool("Numverify", "Phone carrier and line type check",
            { _ -> "https://numverify.com/" },
            setOf("phone")),
        OsintTool("OpenCelliD", "Cell tower geolocation database",
            { _ -> "https://opencellid.org/" },
            setOf("phone", "geo")),
        OsintTool("CarrierLookup", "Phone carrier and number portability",
            { _ -> "https://www.carrierlookup.com/" },
            setOf("phone")),
        OsintTool("AnyWho", "Reverse phone lookup",
            { q -> buildUrl("https://www.anywho.com/reverse-lookup/{q-digits}", q) },
            setOf("phone")),

        // ── USERNAME ────────────────────────────────────────────────────────────
        OsintTool("WhatsMyName", "Check username across 500+ sites",
            { q -> buildUrl("https://whatsmyname.app/?q={q-encoded}", q) },
            setOf("username")),
        OsintTool("NameCheckup", "Username on 100+ social networks",
            { q -> buildUrl("https://namecheckup.com/{q-encoded}", q) },
            setOf("username")),
        OsintTool("Namecheckr", "Cross-platform username finder",
            { q -> buildUrl("https://www.namecheckr.com/{q-encoded}", q) },
            setOf("username")),
        OsintTool("Instant Username", "Real-time username search",
            { _ -> "https://instantusername.com/" },
            setOf("username")),
        OsintTool("KnowEm", "Username availability on 150+ networks",
            { q -> buildUrl("https://knowem.com/checkusernames.php?u={q-encoded}", q) },
            setOf("username")),
        OsintTool("CheckUsernames", "Check social media username availability",
            { q -> buildUrl("https://checkusernames.com/?q={q-encoded}", q) },
            setOf("username")),
        OsintTool("Twitter/X Profile", "Direct profile lookup",
            { q -> buildUrl("https://twitter.com/{q-raw}", q) },
            setOf("username", "social")),
        OsintTool("Instagram Profile", "Direct profile lookup",
            { q -> buildUrl("https://www.instagram.com/{q-raw}/", q) },
            setOf("username", "social")),
        OsintTool("Reddit Profile", "User profile and post history",
            { q -> buildUrl("https://www.reddit.com/user/{q-raw}", q) },
            setOf("username", "social")),
        OsintTool("TikTok Profile", "Direct profile lookup",
            { q -> buildUrl("https://www.tiktok.com/@{q-raw}", q) },
            setOf("username", "social")),
        OsintTool("GitHub Profile", "Developer profile and repos",
            { q -> buildUrl("https://github.com/{q-raw}", q) },
            setOf("username")),
        OsintTool("Twitch Profile", "Streamer profile and stats",
            { q -> buildUrl("https://www.twitch.tv/{q-raw}", q) },
            setOf("username", "social")),
        OsintTool("Steam Search", "Gaming profile search",
            { q -> buildUrl("https://steamcommunity.com/search/users/#text={q-encoded}", q) },
            setOf("username")),
        OsintTool("Discord Lookup", "Discord user ID and profile",
            { _ -> "https://discord.id/" },
            setOf("username", "social")),
        OsintTool("Telegram Search", "Telegram username and channel search",
            { q -> buildUrl("https://tgstat.com/search?q={q-encoded}", q) },
            setOf("username", "social")),

        // ── DOMAIN / IP ─────────────────────────────────────────────────────────
        OsintTool("Shodan", "Internet-connected device and port scanner",
            { q -> buildUrl("https://www.shodan.io/search?query={q-encoded}", q) },
            setOf("domain")),
        OsintTool("Censys", "Internet-wide host and cert search",
            { q -> buildUrl("https://search.censys.io/search?resource=hosts&q={q-encoded}", q) },
            setOf("domain")),
        OsintTool("VirusTotal", "Malware and threat scan for domains/IPs",
            { q -> buildUrl("https://www.virustotal.com/gui/domain/{q-raw}", q) },
            setOf("domain", "threat")),
        OsintTool("DNSDumpster", "DNS recon — subdomains, MX, TXT",
            { _ -> "https://dnsdumpster.com/" },
            setOf("domain")),
        OsintTool("SecurityTrails", "Historical DNS, WHOIS, subdomains",
            { q -> buildUrl("https://securitytrails.com/domain/{q-raw}/dns", q) },
            setOf("domain")),
        OsintTool("URLScan.io", "Website screenshot and request analysis",
            { q -> buildUrl("https://urlscan.io/search/#domain:{q-raw}", q) },
            setOf("domain")),
        OsintTool("AbuseIPDB", "IP abuse reports and reputation",
            { q -> buildUrl("https://www.abuseipdb.com/check/{q-raw}", q) },
            setOf("domain", "threat")),
        OsintTool("GreyNoise", "Internet scanner noise detection",
            { q -> buildUrl("https://viz.greynoise.io/ip/{q-raw}", q) },
            setOf("domain", "threat")),
        OsintTool("AlienVault OTX", "Threat intelligence and IOC feeds",
            { q -> buildUrl("https://otx.alienvault.com/indicator/domain/{q-raw}", q) },
            setOf("domain", "threat")),
        OsintTool("Robtex", "BGP routing and passive DNS",
            { q -> buildUrl("https://www.robtex.com/dns-lookup/{q-raw}", q) },
            setOf("domain")),
        OsintTool("crt.sh", "SSL certificate transparency logs",
            { q -> buildUrl("https://crt.sh/?q={q-encoded}", q) },
            setOf("domain")),
        OsintTool("Wayback Machine", "Archived web snapshots",
            { q -> buildUrl("https://web.archive.org/web/*/{q-raw}", q) },
            setOf("domain", "tools")),
        OsintTool("IPinfo.io", "IP geolocation, ASN, company info",
            { q -> buildUrl("https://ipinfo.io/{q-raw}", q) },
            setOf("domain", "geo")),
        OsintTool("ViewDNS.info", "Reverse IP, DNS history, ping",
            { q -> buildUrl("https://viewdns.info/reverseip/?host={q-raw}&t=1", q) },
            setOf("domain")),
        OsintTool("HackerTarget", "Network tools and reverse IP",
            { q -> buildUrl("https://hackertarget.com/reverse-ip-lookup/?q={q-raw}", q) },
            setOf("domain")),
        OsintTool("LeakIX", "Exposed services and data leak search",
            { q -> buildUrl("https://leakix.net/search?scope=leak&q={q-encoded}", q) },
            setOf("domain", "breach")),
        OsintTool("ZoomEye", "Cyberspace device and service search",
            { q -> buildUrl("https://www.zoomeye.org/searchResult?q={q-encoded}", q) },
            setOf("domain")),
        OsintTool("Netcraft", "Site report and technology fingerprint",
            { q -> buildUrl("https://sitereport.netcraft.com/?url={q-encoded}", q) },
            setOf("domain")),
        OsintTool("BuiltWith", "Technology stack detection",
            { q -> buildUrl("https://builtwith.com/{q-raw}", q) },
            setOf("domain", "company")),
        OsintTool("SimilarWeb", "Traffic and competitor analysis",
            { q -> buildUrl("https://www.similarweb.com/website/{q-raw}", q) },
            setOf("domain", "company")),
        OsintTool("Who.is", "WHOIS lookup",
            { q -> buildUrl("https://who.is/whois/{q-raw}", q) },
            setOf("domain")),
        OsintTool("MXToolbox DNS", "DNS lookup, blacklist check",
            { q -> buildUrl("https://mxtoolbox.com/SuperTool.aspx?action=a%3a{q-raw}&run=toolpage", q) },
            setOf("domain")),
        OsintTool("PublicWWW", "Source code search for technologies",
            { q -> buildUrl("https://publicwww.com/websites/{q-encoded}/", q) },
            setOf("domain")),

        // ── COMPANY ─────────────────────────────────────────────────────────────
        OsintTool("OpenCorporates", "Global corporate registry, officers, filings",
            { q -> buildUrl("https://opencorporates.com/companies?q={q-encoded}", q) },
            setOf("company")),
        OsintTool("Crunchbase", "Startup funding, acquisitions, team",
            { q -> buildUrl("https://www.crunchbase.com/textsearch?q={q-encoded}", q) },
            setOf("company")),
        OsintTool("SEC EDGAR", "US public company filings, 10-K, 8-K",
            { q -> buildUrl("https://efts.sec.gov/LATEST/search-index?q={q-encoded}", q) },
            setOf("company", "finance")),
        OsintTool("LinkedIn Company", "Employee count, leadership, job posts",
            { q -> buildUrl("https://www.linkedin.com/search/results/companies/?keywords={q-encoded}", q) },
            setOf("company")),
        OsintTool("Hunter.io Domain", "Company email pattern and employees",
            { q -> buildUrl("https://hunter.io/domain-search/{q-raw}", q) },
            setOf("company", "email")),
        OsintTool("Glassdoor", "Employee reviews and salary data",
            { q -> buildUrl("https://www.glassdoor.com/Search/results.htm?keyword={q-encoded}", q) },
            setOf("company")),
        OsintTool("Companies House", "UK company registry",
            { q -> buildUrl("https://find-and-update.company-information.service.gov.uk/search?q={q-encoded}", q) },
            setOf("company")),
        OsintTool("Corporation Wiki", "US corporate connections and officers",
            { q -> buildUrl("https://www.corporationwiki.com/search/results?term={q-encoded}", q) },
            setOf("company")),
        OsintTool("LittleSis", "Power network — who knows who",
            { q -> buildUrl("https://littlesis.org/search?q={q-encoded}", q) },
            setOf("company", "finance")),
        OsintTool("ICIJ Offshore Leaks", "Panama Papers, FinCEN, Pandora",
            { q -> buildUrl("https://offshoreleaks.icij.org/search?q={q-encoded}", q) },
            setOf("company", "finance")),
        OsintTool("GovSalaries", "Government employee salary search",
            { q -> buildUrl("https://govsalaries.com/search?s={q-encoded}", q) },
            setOf("company", "finance")),
        OsintTool("OpenPayrolls", "US public employee pay",
            { q -> buildUrl("https://openpayrolls.com/search?term={q-encoded}", q) },
            setOf("company", "finance")),
        OsintTool("Wappalyzer", "Technology and framework detection",
            { q -> buildUrl("https://www.wappalyzer.com/lookup/{q-raw}/", q) },
            setOf("company", "domain")),
        OsintTool("WHOXY", "Reverse WHOIS — who owns what",
            { q -> buildUrl("https://www.whoxy.com/whois-history/{q-raw}.htm", q) },
            setOf("company", "domain")),

        // ── IMAGE / FACE ────────────────────────────────────────────────────────
        OsintTool("Google Lens", "Reverse image search",
            { _ -> "https://lens.google.com/" },
            setOf("image")),
        OsintTool("TinEye", "Reverse image — exact and modified",
            { _ -> "https://tineye.com/" },
            setOf("image")),
        OsintTool("Yandex Images", "Best reverse image for faces",
            { _ -> "https://yandex.com/images/" },
            setOf("image")),
        OsintTool("FaceCheck.id", "Facial recognition reverse image",
            { _ -> "https://facecheck.id/" },
            setOf("image")),
        OsintTool("Search4Faces", "Face search across VK and OK",
            { _ -> "https://search4faces.com/" },
            setOf("image")),
        OsintTool("PimEyes", "AI-powered facial recognition search",
            { _ -> "https://pimeyes.com/en" },
            setOf("image")),
        OsintTool("Bing Visual Search", "Scene and object recognition",
            { _ -> "https://www.bing.com/visualsearch" },
            setOf("image")),
        OsintTool("FotoForensics", "JPEG error level analysis",
            { _ -> "https://fotoforensics.com/" },
            setOf("image")),
        OsintTool("Forensically", "Digital image forensics toolkit",
            { _ -> "https://29a.ch/photo-forensics/" },
            setOf("image")),
        OsintTool("ExifData", "EXIF metadata viewer",
            { _ -> "https://www.exifdata.com/" },
            setOf("image")),
        OsintTool("Jimpl", "Online EXIF data extractor",
            { _ -> "https://jimpl.com/" },
            setOf("image")),
        OsintTool("Image Raider", "Reverse search across multiple engines",
            { _ -> "https://infringement.report/" },
            setOf("image")),

        // ── SOCIAL MEDIA ────────────────────────────────────────────────────────
        OsintTool("Social Searcher", "Real-time public post search",
            { q -> buildUrl("https://www.social-searcher.com/social-buzz/?q5={q-encoded}", q) },
            setOf("social")),
        OsintTool("Twitter/X Advanced", "Advanced tweet search",
            { q -> buildUrl("https://twitter.com/search?q={q-encoded}&f=live", q) },
            setOf("social")),
        OsintTool("Who Posted What", "Facebook post search",
            { _ -> "https://www.whopostedwhat.com/" },
            setOf("social")),
        OsintTool("Reddit Search", "Reddit via Pushshift",
            { q -> buildUrl("https://camas.unddit.com/", q) },
            setOf("social")),
        OsintTool("TikTok Search", "TikTok user and hashtag search",
            { q -> buildUrl("https://www.tiktok.com/search?q={q-encoded}", q) },
            setOf("social")),
        OsintTool("YouTube Search", "Video and channel search",
            { q -> buildUrl("https://www.youtube.com/results?search_query={q-encoded}", q) },
            setOf("social")),
        OsintTool("Telegram TGStat", "Telegram channel and message search",
            { q -> buildUrl("https://tgstat.com/search?q={q-encoded}", q) },
            setOf("social")),
        OsintTool("Snapchat Maps", "Public geotagged Snapchat stories",
            { _ -> "https://map.snapchat.com/" },
            setOf("social", "geo")),
        OsintTool("VK Search", "Russian social network search",
            { q -> buildUrl("https://vk.com/search?c[q]={q-encoded}&c[section]=people", q) },
            setOf("social")),
        OsintTool("Discord DISBOARD", "Discord server directory",
            { q -> buildUrl("https://disboard.org/search?keyword={q-encoded}", q) },
            setOf("social")),
        OsintTool("Twitch Search", "Streamer and clip search",
            { q -> buildUrl("https://www.twitch.tv/search?term={q-encoded}", q) },
            setOf("social")),
        OsintTool("Reddit Investigator", "User behavior and karma analysis",
            { _ -> "https://www.redditinvestigator.com/" },
            setOf("social")),
        OsintTool("Nitter", "Twitter without tracking",
            { q -> buildUrl("https://nitter.net/search?q={q-encoded}", q) },
            setOf("social")),
        OsintTool("SocialGrep", "Reddit and social media search",
            { q -> buildUrl("https://socialgrep.com/search?query={q-encoded}", q) },
            setOf("social")),

        // ── VEHICLE / VIN ───────────────────────────────────────────────────────
        OsintTool("VINDecoderz", "VIN decode — make, model, specs",
            { q -> buildUrl("https://www.vindecoderz.com/EN/check-lookup/{q-raw}", q) },
            setOf("vehicle")),
        OsintTool("FAXVIN", "VIN check and vehicle history",
            { q -> buildUrl("https://www.faxvin.com/{q-raw}", q) },
            setOf("vehicle")),
        OsintTool("VehicleHistory", "VIN report — accidents, title, odometer",
            { q -> buildUrl("https://www.vehiclehistory.com/vin-report/{q-raw}", q) },
            setOf("vehicle")),
        OsintTool("AutoCheck", "Experian vehicle history report",
            { q -> buildUrl("https://www.autocheck.com/vehiclehistory/autocheck/en/vehiclecheck?vin={q-raw}", q) },
            setOf("vehicle")),
        OsintTool("VINCheck NICB", "National Insurance Crime Bureau VIN check",
            { _ -> "https://www.nicb.org/vincheck" },
            setOf("vehicle")),
        OsintTool("VINcheck.info", "Free VIN check and history",
            { _ -> "https://www.vincheck.info/" },
            setOf("vehicle")),
        OsintTool("Plate Lookup (ThatsThem)", "License plate to owner lookup",
            { q -> buildUrl("https://thatsthem.com/license-plate/{q-raw}", q) },
            setOf("vehicle")),
        OsintTool("Poctra", "European vehicle history",
            { q -> buildUrl("https://poctra.com/{q-raw}", q) },
            setOf("vehicle")),
        OsintTool("SearchQuarry", "License plate lookup",
            { _ -> "https://www.searchquarry.com/" },
            setOf("vehicle")),

        // ── BREACHES ────────────────────────────────────────────────────────────
        OsintTool("Ashley Madison Check", "Ashley Madison breach lookup",
            { _ -> "https://ashley.cynic.al/" },
            setOf("breach")),
        OsintTool("DDoSecrets", "Public interest leaks and datasets",
            { q -> buildUrl("https://ddosecrets.com/wiki/Special:Search?search={q-encoded}&ns0=1", q) },
            setOf("breach", "darknet")),
        OsintTool("Scylla.sh", "Public breach data aggregator",
            { _ -> "https://scylla.sh/" },
            setOf("breach")),
        OsintTool("Breach Directory", "Username/email breach search",
            { _ -> "https://breachdirectory.org/" },
            setOf("breach")),

        // ── DARK WEB ────────────────────────────────────────────────────────────
        OsintTool("Ahmia", "Dark web search engine (Tor index)",
            { q -> buildUrl("https://ahmia.fi/search/?q={q-encoded}", q) },
            setOf("darknet")),
        OsintTool("DarkTracer", "Ransomware victim and dark web monitor",
            { _ -> "https://darktracer.io/" },
            setOf("darknet", "threat")),
        OsintTool("RansomWatch", "Ransomware group leak site tracker",
            { _ -> "https://ransomwatch.telemetry.ltd/" },
            setOf("darknet", "threat")),
        OsintTool("OnionSearch", "Multi-engine dark web search",
            { q -> buildUrl("https://onionsearchengine.com/search.php?search={q-encoded}", q) },
            setOf("darknet")),
        OsintTool("Onion.live", "Dark web site search and monitor",
            { _ -> "https://onion.live/" },
            setOf("darknet")),
        OsintTool("Darknetlive", "Dark web news and site directory",
            { _ -> "https://darknetlive.com/" },
            setOf("darknet")),

        // ── GEOLOCATION ─────────────────────────────────────────────────────────
        OsintTool("Google Maps", "Satellite and street level mapping",
            { q -> buildUrl("https://www.google.com/maps/search/{q-encoded}", q) },
            setOf("geo")),
        OsintTool("Yandex Maps", "Russian mapping — excellent satellite",
            { q -> buildUrl("https://yandex.com/maps/?text={q-encoded}", q) },
            setOf("geo")),
        OsintTool("OpenStreetMap", "Open source collaborative map",
            { q -> buildUrl("https://www.openstreetmap.org/search?query={q-encoded}", q) },
            setOf("geo")),
        OsintTool("Mapillary", "Street-level crowdsourced imagery",
            { _ -> "https://www.mapillary.com/" },
            setOf("geo")),
        OsintTool("Wikimapia", "Map with user-added POI data",
            { q -> buildUrl("https://wikimapia.org/#lang=en&q={q-encoded}", q) },
            setOf("geo")),
        OsintTool("WiGLE", "WiFi network geolocation database",
            { _ -> "https://wigle.net/search#" },
            setOf("geo")),
        OsintTool("GeoNames", "Geographic database with 11M+ places",
            { q -> buildUrl("https://www.geonames.org/search.html?q={q-encoded}", q) },
            setOf("geo")),
        OsintTool("SunCalc", "Sun position and shadow analysis",
            { _ -> "https://www.suncalc.org/" },
            setOf("geo")),
        OsintTool("Satellites.pro", "Multi-provider satellite imagery",
            { _ -> "https://satellites.pro/" },
            setOf("geo")),
        OsintTool("FlightAware", "Live and historical flight tracking",
            { q -> buildUrl("https://flightaware.com/live/flight/{q-raw}", q) },
            setOf("geo")),
        OsintTool("MarineTraffic", "Live ship and vessel tracking",
            { _ -> "https://www.marinetraffic.com/" },
            setOf("geo")),

        // ── FINANCIAL ───────────────────────────────────────────────────────────
        OsintTool("SEC EDGAR Full Text", "Securities filings full text search",
            { q -> buildUrl("https://efts.sec.gov/LATEST/search-index?q={q-encoded}&dateRange=custom", q) },
            setOf("finance")),
        OsintTool("PACER", "US federal court records",
            { _ -> "https://pacer.uscourts.gov/" },
            setOf("finance", "records")),
        OsintTool("MuckRock", "FOIA request database",
            { q -> buildUrl("https://www.muckrock.com/search/?q={q-encoded}", q) },
            setOf("finance", "records")),
        OsintTool("PPP Loan Database", "COVID PPP loan recipients",
            { q -> buildUrl("https://ppp.directory/search?q={q-encoded}", q) },
            setOf("finance")),
        OsintTool("Nonprofit Explorer", "Nonprofit IRS 990 filings",
            { q -> buildUrl("https://projects.propublica.org/nonprofits/search?q={q-encoded}", q) },
            setOf("finance")),
        OsintTool("Tradint Research", "Trade intelligence tool",
            { _ -> "https://tradint.io/" },
            setOf("finance")),

        // ── PUBLIC RECORDS ──────────────────────────────────────────────────────
        OsintTool("FamilySearch", "Genealogy and historical records",
            { q -> buildUrl("https://www.familysearch.org/search/record/results?q.givenName={first}&q.surname={last}", q) },
            setOf("records")),
        OsintTool("SSDI Search", "Social Security Death Index",
            { q -> buildUrl("https://www.familysearch.org/search/collection/1202798?q.givenName={first}&q.surname={last}", q) },
            setOf("records")),
        OsintTool("FindAGrave", "Cemetery and death records",
            { q -> buildUrl("https://www.findagrave.com/memorial/search?q={q-encoded}", q) },
            setOf("records")),
        OsintTool("OpenOversight", "Police misconduct and badge lookup",
            { _ -> "https://openoversight.com/" },
            setOf("records")),

        // ── THREAT INTELLIGENCE ─────────────────────────────────────────────────
        OsintTool("VirusTotal Scan", "Multi-engine malware and URL scanner",
            { q -> buildUrl("https://www.virustotal.com/gui/search/{q-encoded}", q) },
            setOf("threat")),
        OsintTool("Hybrid Analysis", "Dynamic malware sandbox analysis",
            { q -> buildUrl("https://www.hybrid-analysis.com/search?query={q-encoded}", q) },
            setOf("threat")),
        OsintTool("Any.run", "Interactive online malware sandbox",
            { _ -> "https://app.any.run/" },
            setOf("threat")),
        OsintTool("Pulsedive", "Threat intelligence and IOC enrichment",
            { q -> buildUrl("https://pulsedive.com/search/?q={q-encoded}", q) },
            setOf("threat")),
        OsintTool("Cisco Talos", "Threat intelligence and IP/domain reputation",
            { q -> buildUrl("https://talosintelligence.com/reputation_center/lookup?search={q-encoded}", q) },
            setOf("threat")),
        OsintTool("ThreatMiner", "IOC and threat data mining",
            { q -> buildUrl("https://www.threatminer.org/host.php?q={q-encoded}", q) },
            setOf("threat")),
        OsintTool("Shodan CVE", "CVE exploit and vulnerable host search",
            { q -> buildUrl("https://www.shodan.io/search?query=vuln:{q-raw}", q) },
            setOf("threat")),
        OsintTool("Sploitus", "Exploit and vulnerability search",
            { q -> buildUrl("https://sploitus.com/?query={q-encoded}", q) },
            setOf("threat")),
        OsintTool("CheckPhish", "Phishing URL and site detection",
            { q -> buildUrl("https://checkphish.ai/scan/{q-encoded}", q) },
            setOf("threat")),

        // ── TOOLS ───────────────────────────────────────────────────────────────
        OsintTool("CyberChef", "Encoding, decoding, and data analysis",
            { _ -> "https://gchq.github.io/CyberChef/" },
            setOf("tools")),
        OsintTool("OSINT Framework", "Visual map of all OSINT resources",
            { _ -> "https://osintframework.com/" },
            setOf("tools")),
        OsintTool("IntelTechniques", "Michael Bazzell's OSINT search tools",
            { _ -> "https://inteltechniques.com/tools/" },
            setOf("tools")),
        OsintTool("Archive.ph", "Save and share web page snapshots",
            { _ -> "https://archive.ph/" },
            setOf("tools")),
        OsintTool("ExplainShell", "Explain any shell command",
            { q -> buildUrl("https://explainshell.com/explain?cmd={q-encoded}", q) },
            setOf("tools")),
        OsintTool("CrackStation", "Hash cracking and reverse lookup",
            { _ -> "https://crackstation.net/" },
            setOf("tools")),
        OsintTool("Hashes.com", "Hash database and reverse lookup",
            { _ -> "https://hashes.com/en/decrypt/hash" },
            setOf("tools")),
        OsintTool("URL Decoder/Encoder", "URL encode and decode tool",
            { _ -> "https://meyerweb.com/eric/tools/dencoder/" },
            setOf("tools")),
        OsintTool("What Is My IP", "Your current IP and geolocation",
            { _ -> "https://whatismyipaddress.com/" },
            setOf("tools"))
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

        val prefilledCategory = arguments?.getString("filterCategory") ?: ""
        if (prefilledCategory.isNotBlank() && categories.any { it.id == prefilledCategory }) {
            currentFilter = prefilledCategory
        }

        buildCategoryChips()
        buildResourceCards()

        binding.btnSelectAll.setOnClickListener {
            checkBoxMap.forEach { (tool, cb) ->
                cb.isChecked = true
                selectedTools.add(tool)
            }
        }

        binding.btnClearSelection.setOnClickListener {
            checkBoxMap.forEach { (_, cb) ->
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

    private fun buildCategoryChips() {
        val ctx = requireContext()
        val chipGroup = binding.chipContainer
        chipGroup.isSingleSelection = true
        chipGroup.removeAllViews()

        val allChip = Chip(ctx).apply {
            text = "All"
            tag = "all"
            isCheckable = true
            isChecked = currentFilter == "all"
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    currentFilter = "all"
                    rebuildCards()
                }
            }
        }
        chipGroup.addView(allChip)

        categories.forEach { category ->
            val chip = Chip(ctx).apply {
                text = "${category.icon} ${category.label}"
                tag = category.id
                isCheckable = true
                isChecked = currentFilter == category.id
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        currentFilter = category.id
                        rebuildCards()
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun rebuildCards() {
        selectedTools.clear()
        checkBoxMap.clear()
        binding.resourcesContainer.removeAllViews()
        buildResourceCards()
    }

    private fun buildResourceCards() {
        val container = binding.resourcesContainer
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val visibleCategories = if (currentFilter == "all") {
            categories
        } else {
            categories.filter { it.id == currentFilter }
        }

        visibleCategories.forEach { category ->
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
