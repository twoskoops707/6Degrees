package com.twoskoops707.sixdegrees.data.osint

/**
 * Maps lockfale/OSINT-Framework top-level folders to SixDegrees hub category ids.
 * @see <a href="https://github.com/lockfale/osint-framework">OSINT Framework</a>
 */
object OsintFrameworkCategoryMapper {

    private val folderToCategories: Map<String, Set<String>> = mapOf(
        "Username" to setOf("username"),
        "Email Address" to setOf("email"),
        "Domain Name" to setOf("domain"),
        "Cloud Infrastructure" to setOf("domain"),
        "IP & MAC Address" to setOf("domain", "geo"),
        "Images / Videos / Docs" to setOf("image"),
        "Social Networks" to setOf("social"),
        "Instant Messaging" to setOf("social"),
        "People Search Engines" to setOf("person"),
        "Dating" to setOf("person", "social"),
        "Telephone Numbers" to setOf("phone"),
        "Public Records" to setOf("records"),
        "Compliance & Risk Intelligence" to setOf("threat", "finance"),
        "Business Records" to setOf("company"),
        "Transportation" to setOf("vehicle", "geo"),
        "Geolocation Tools / Maps" to setOf("geo"),
        "Search Engines" to setOf("tools"),
        "Online Communities" to setOf("social"),
        "Archives" to setOf("domain", "tools"),
        "Language Translation" to setOf("tools"),
        "Cyber Threat Intelligence" to setOf("threat"),
        "OpSec" to setOf("tools"),
        "Tools" to setOf("tools"),
        "Blockchain & Cryptocurrency" to setOf("finance"),
        "Malicious File Analysis" to setOf("threat"),
        "Mobile OSINT" to setOf("tools"),
        "Dark Web" to setOf("darknet"),
        "Encoding / Decoding" to setOf("tools"),
        "AI Tools" to setOf("tools"),
        "Disinformation & Media Verification" to setOf("image", "social"),
        "Documentation / Evidence Capture" to setOf("tools"),
        "Classifieds" to setOf("person"),
        "Training" to setOf("tools")
    )

    val hubCategories: List<HubCategory> = listOf(
        HubCategory("person", "Person", "Public records, people search engines, criminal and court records"),
        HubCategory("email", "Email", "Breach history, reputation, deliverability, linked accounts"),
        HubCategory("phone", "Phone", "Carrier lookup, owner ID, reverse search, spam reports"),
        HubCategory("username", "Username", "Cross-platform presence: social, gaming, forums, dating"),
        HubCategory("domain", "Domain / IP", "WHOIS, DNS, open ports, SSL certs, threat intel, archives"),
        HubCategory("company", "Company", "Corporate registry, filings, officers, tech stack, financials"),
        HubCategory("image", "Image / Face", "Reverse image and facial recognition across the web"),
        HubCategory("social", "Social Media", "Deep search across Twitter/X, Instagram, Reddit, Telegram"),
        HubCategory("vehicle", "Vehicle / VIN", "VIN decode, history reports, license plate lookup"),
        HubCategory("breach", "Breaches", "Leaked credentials, breach databases, data exposure"),
        HubCategory("darknet", "Dark Web", "Tor indexes, ransomware trackers, onion search engines"),
        HubCategory("geo", "Geolocation", "Satellite, street view, WiFi positioning, ship and flight tracking"),
        HubCategory("finance", "Financial", "SEC filings, offshore leaks, government salaries, FOIA"),
        HubCategory("records", "Public Records", "Court records, genealogy, death index, police accountability"),
        HubCategory("threat", "Threat Intel", "Malware sandboxes, IOC feeds, CVE and exploit search"),
        HubCategory("tools", "Tools", "OSINT utilities, encoders, archivers, hash crackers")
    )

    val categoryLabels: Map<String, String> = mapOf(
        "person" to "👤 People Search",
        "email" to "📧 Email",
        "phone" to "📞 Phone",
        "username" to "🔑 Username",
        "domain" to "🌐 Domain / IP",
        "company" to "🏢 Company",
        "image" to "🖼️ Image / Face",
        "social" to "💬 Social Media",
        "vehicle" to "🚗 Vehicle / VIN",
        "breach" to "🔓 Breaches",
        "darknet" to "🕸️ Dark Web",
        "geo" to "📍 Geolocation",
        "finance" to "💰 Financial",
        "records" to "📋 Public Records",
        "threat" to "⚠️ Threat Intel",
        "tools" to "🛠️ Tools"
    )

    val typeToCategories: Map<String, List<String>> = mapOf(
        "scan" to listOf("person", "social", "breach", "records", "image"),
        "person" to listOf("person", "social", "breach", "records", "image"),
        "comprehensive" to listOf("person", "social", "breach", "records", "image"),
        "image" to listOf("person", "social", "breach", "records", "image"),
        "email" to listOf("email", "breach"),
        "phone" to listOf("phone"),
        "username" to listOf("username", "social"),
        "domain" to listOf("domain", "threat", "breach"),
        "ip" to listOf("domain", "threat", "breach"),
        "company" to listOf("company", "domain", "finance"),
        "vehicle" to listOf("vehicle"),
        "vin" to listOf("vehicle"),
        "wifi" to listOf("geo"),
        "mac" to listOf("geo"),
        "ssid" to listOf("geo")
    )

    data class HubCategory(
        val id: String,
        val label: String,
        val description: String
    )

    fun categoriesForPath(path: List<String>): Set<String> {
        val top = path.firstOrNull() ?: return setOf("tools")
        return folderToCategories[top] ?: setOf("tools")
    }

    fun inputMatchesSearchType(input: String, searchType: String): Boolean {
        val normalized = input.lowercase()
        return when (searchType) {
            "email" -> normalized.contains("email") || normalized.contains("gmail")
            "phone" -> normalized.contains("phone")
            "username" -> normalized.contains("username") || normalized.contains("handle")
            "domain", "ip" -> normalized.contains("domain") || normalized.contains("ip") ||
                normalized.contains("url") || normalized.contains("website") || normalized.contains("dns")
            "company" -> normalized.contains("company") || normalized.contains("business") ||
                normalized.contains("registration")
            "vehicle", "vin" -> normalized.contains("vin") || normalized.contains("vehicle") ||
                normalized.contains("plate") || normalized.contains("license")
            "person", "scan", "comprehensive", "image" ->
                normalized.contains("person") || normalized.contains("name") || normalized.contains("entity")
            else -> false
        }
    }
}
