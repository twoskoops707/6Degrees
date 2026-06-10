package com.twoskoops707.sixdegrees.domain

import com.twoskoops707.sixdegrees.domain.model.Employment

/**
 * Extracts structured connections, career timeline, and imagery from flat OSINT metadata.
 * Powers the "pocket private eye" dossier: associates, job transitions, corporate links.
 */
object SubjectConnectionEngine {

    data class TimelineEvent(
        val kind: String,
        val title: String,
        val detail: String,
        val era: String,
        val source: String,
        val sortOrder: Int
    )

    data class ConnectionEdge(
        val relation: String,
        val target: String,
        val detail: String,
        val source: String,
        val pivotType: String,
        val pivotQuery: String
    )

    data class ImageRef(
        val url: String,
        val label: String,
        val source: String
    )

    private val JOB_AT_REGEX = Regex(
        """(?i)^(.+?)\s+at\s+(.+?)(?:\s+\((current|until\s+[\d/.\-]+)\))?\s*$"""
    )
    private val OFFICER_ROLE_REGEX = Regex(
        """^(.+?)\s*[-–—|]\s*(.+?)\s*[-–—|]\s*(.+)$"""
    )
    private val YEAR_REGEX = Regex("""\b(19|20)\d{2}\b""")

    fun sync(metadata: MutableMap<String, String>) {
        val employment = parseEmployment(metadata)
        val timeline = buildTimeline(metadata, employment)
        val connections = buildConnections(metadata, employment)
        val images = collectImages(metadata)

        if (employment.isNotEmpty()) {
            metadata["structured_employment_json"] = employmentToJson(employment)
            inferCareerTransition(metadata, employment)
        }
        if (timeline.isNotEmpty()) {
            metadata["subject_timeline_json"] = timelineToJson(timeline)
            metadata["subject_timeline"] = timeline.take(12).joinToString("\n") { event ->
                "${event.era}\t${event.title}\t${event.detail}"
            }
        }
        if (connections.isNotEmpty()) {
            metadata["connection_edges_json"] = connectionsToJson(connections)
            metadata["connection_summary"] = connections.take(20).joinToString("\n") { edge ->
                "${edge.relation}: ${edge.target} — ${edge.detail}"
            }
        }
        if (images.isNotEmpty()) {
            metadata["imagery_urls"] = images.joinToString("|") { "${it.label}::${it.url}::${it.source}" }
            if (metadata["profile_photo_url"].isNullOrBlank()) {
                metadata["profile_photo_url"] = images.first().url
            }
        }
    }

    fun parseEmployment(metadata: Map<String, String>): List<Employment> {
        val jobs = linkedMapOf<String, Employment>()

        fun addJob(title: String, company: String, source: String, isCurrent: Boolean, endDate: String? = null) {
            val co = company.trim().trimEnd('.', ',')
            val role = title.trim()
            if (co.length < 2) return
            val key = "${co.lowercase()}|${role.lowercase()}"
            if (jobs.containsKey(key)) return
            jobs[key] = Employment(
                companyName = co,
                jobTitle = role.ifBlank { "Employee" },
                startDate = null,
                endDate = endDate,
                isCurrent = isCurrent
            )
        }

        listOf("pipl_employment", "pdl_employment").forEach { key ->
            metadata[key]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                parseJobLine(line)?.let { (title, company, current, end) ->
                    addJob(title, company, key.removeSuffix("_employment"), current, end)
                }
            }
        }

        metadata["pdl_job_title"]?.takeIf { it.isNotBlank() }?.let { title ->
            metadata["pdl_company"]?.takeIf { it.isNotBlank() }?.let { company ->
                addJob(title, company, "pdl", isCurrent = true)
            }
        }
        metadata["clearbit_person_title"]?.takeIf { it.isNotBlank() }?.let { title ->
            metadata["clearbit_person_company"]?.takeIf { it.isNotBlank() }?.let { company ->
                addJob(title, company, "clearbit", isCurrent = true)
            }
        }

        metadata["officer_details"]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
            OFFICER_ROLE_REGEX.find(line.trim())?.let { m ->
                val role = m.groupValues[2].trim()
                val company = m.groupValues[3].trim()
                addJob(role, company, "sec", isCurrent = true)
            }
        }

        metadata["corpwiki_person_companies"]?.lines()?.filter { it.isNotBlank() }?.forEach { co ->
            addJob("", co.trim(), "opencorporates", isCurrent = false)
        }

        metadata["wikidata_employers"]?.split(",", " | ")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?.forEach { co -> addJob("", co, "wikidata", isCurrent = false) }

        return jobs.values.toList()
    }

    fun employmentToJson(jobs: List<Employment>): String {
        if (jobs.isEmpty()) return "[]"
        return jobs.joinToString(prefix = "[", postfix = "]") { job ->
            buildString {
                append("{")
                append("\"companyName\":").append(jsonString(job.companyName)).append(',')
                append("\"jobTitle\":").append(jsonString(job.jobTitle)).append(',')
                append("\"startDate\":").append(job.startDate?.let { jsonString(it) } ?: "null").append(',')
                append("\"endDate\":").append(job.endDate?.let { jsonString(it) } ?: "null").append(',')
                append("\"isCurrent\":").append(job.isCurrent)
                append("}")
            }
        }
    }

    fun parseEmploymentFromJson(json: String): List<Employment> {
        if (json.isBlank() || json == "[]") return emptyList()
        return parseJsonObjects(json).mapNotNull { obj ->
            val company = obj["companyName"].orEmpty()
            if (company.isBlank()) return@mapNotNull null
            Employment(
                companyName = company,
                jobTitle = obj["jobTitle"] ?: "Employee",
                startDate = obj["startDate"]?.takeIf { it != "null" && it.isNotBlank() },
                endDate = obj["endDate"]?.takeIf { it != "null" && it.isNotBlank() },
                isCurrent = obj["isCurrent"]?.toBooleanStrictOrNull() == true
            )
        }
    }

    fun buildTimeline(metadata: Map<String, String>, employment: List<Employment>): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()

        employment.forEach { job ->
            val era = when {
                job.isCurrent -> "Present"
                job.endDate != null -> job.endDate
                else -> "Past"
            }
            val sort = when {
                job.isCurrent -> 0
                job.endDate?.let { YEAR_REGEX.find(it)?.value?.toIntOrNull() } != null ->
                    10000 - (YEAR_REGEX.find(job.endDate!!)!!.value.toInt())
                else -> 5000
            }
            val title = job.companyName
            val detail = buildString {
                if (job.jobTitle.isNotBlank() && job.jobTitle != "Employee") append(job.jobTitle)
                if (job.isCurrent) {
                    if (isNotEmpty()) append(" · ")
                    append("Current role")
                } else if (job.endDate != null) {
                    if (isNotEmpty()) append(" · ")
                    append("Ended ${job.endDate}")
                }
            }.ifBlank { "Employment record" }
            events.add(TimelineEvent("employment", title, detail, era, "Records", sort))
        }

        extractAddresses(metadata).take(8).forEachIndexed { index, addr ->
            events.add(TimelineEvent("location", addr, "Known address", "—", "Records", 3000 + index))
        }

        metadata["sec_filings_count"]?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
            val entities = metadata["sec_person_entities"]?.take(80).orEmpty()
            events.add(
                TimelineEvent(
                    "corporate",
                    "SEC filings ($count)",
                    entities.ifBlank { "Corporate disclosure activity on record" },
                    "Corporate",
                    "SEC EDGAR",
                    2000
                )
            )
        }

        return events.sortedBy { it.sortOrder }
    }

    fun buildConnections(metadata: Map<String, String>, employment: List<Employment>): List<ConnectionEdge> {
        val edges = linkedSetOf<String>()
        val result = mutableListOf<ConnectionEdge>()

        fun add(edge: ConnectionEdge) {
            val key = "${edge.relation}|${edge.pivotType}|${edge.pivotQuery.lowercase()}"
            if (edges.add(key)) result.add(edge)
        }

        extractRelatives(metadata).forEach { name ->
            add(ConnectionEdge(
                relation = "Associate",
                target = name,
                detail = "Family or associate link",
                source = inferRelativeSource(metadata, name),
                pivotType = "person",
                pivotQuery = name
            ))
        }

        employment.forEach { job ->
            add(ConnectionEdge(
                relation = if (job.isCurrent) "Current employer" else "Past employer",
                target = job.companyName,
                detail = job.jobTitle.ifBlank { "Employment link" },
                source = "Employment records",
                pivotType = "company",
                pivotQuery = job.companyName
            ))
        }

        metadata["sec_person_entities"]?.split(",")?.map { it.trim() }?.filter { it.length > 2 }
            ?.forEach { entity ->
                add(ConnectionEdge(
                    relation = "SEC affiliation",
                    target = entity,
                    detail = "Corporate filing entity",
                    source = "SEC EDGAR",
                    pivotType = "company",
                    pivotQuery = entity
                ))
            }

        extractPhones(metadata).take(6).forEach { phone ->
            add(ConnectionEdge(
                relation = "Linked phone",
                target = phone,
                detail = "Tap to run reverse lookup",
                source = "Contact records",
                pivotType = "phone",
                pivotQuery = phone
            ))
        }

        extractEmails(metadata).take(6).forEach { email ->
            add(ConnectionEdge(
                relation = "Linked email",
                target = email,
                detail = "Tap to investigate email",
                source = "Contact records",
                pivotType = "email",
                pivotQuery = email
            ))
        }

        metadata["officer_details"]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
            OFFICER_ROLE_REGEX.find(line.trim())?.let { m ->
                val person = m.groupValues[1].trim()
                val company = m.groupValues[3].trim()
                if (person.contains(" ") && person.length > 4) {
                    add(ConnectionEdge(
                        relation = "Corporate officer",
                        target = person,
                        detail = "${m.groupValues[2].trim()} at $company",
                        source = "Corporate filings",
                        pivotType = "person",
                        pivotQuery = person
                    ))
                }
            }
        }

        metadata["career_transition"]?.takeIf { it.isNotBlank() }?.let { transition ->
            add(ConnectionEdge(
                relation = "Career inference",
                target = transition,
                detail = "Inferred from employment timeline",
                source = "SixDegrees",
                pivotType = "person",
                pivotQuery = metadata["person_name"] ?: metadata["search_query"] ?: ""
            ))
        }

        return result
    }

    fun collectImages(metadata: Map<String, String>): List<ImageRef> {
        val seen = linkedSetOf<String>()
        val images = mutableListOf<ImageRef>()

        fun add(url: String, label: String, source: String) {
            val trimmed = url.trim()
            if (!trimmed.startsWith("http") || trimmed.length < 12) return
            if (!seen.add(trimmed.lowercase())) return
            images.add(ImageRef(trimmed, label, source))
        }

        metadata["profile_photo_url"]?.let { add(it, "Profile photo", "Primary") }
        metadata["tt_image_url"]?.let { add(it, "People-search photo", "ThatsThem") }

        metadata.keys.filter { it.startsWith("candidate_") && it.endsWith("_photo_urls") }
            .sorted()
            .forEach { key ->
                val idx = key.removePrefix("candidate_").removeSuffix("_photo_urls")
                metadata[key]?.split("|")?.filter { it.startsWith("http") }?.forEachIndexed { i, url ->
                    add(url, "Candidate $idx photo ${i + 1}", "Disambiguation")
                }
            }

        metadata["search_profile_links"]?.lines()?.filter { it.startsWith("http") }?.take(4)?.forEach { url ->
            val label = when {
                url.contains("linkedin") -> "LinkedIn profile image"
                url.contains("facebook") -> "Facebook profile"
                url.contains("instagram") -> "Instagram profile"
                else -> "Social profile"
            }
            add(url, label, "Social scan")
        }

        return images
    }

    fun parseConnectionsFromJson(json: String): List<ConnectionEdge> =
        parseJsonObjects(json).mapNotNull { obj ->
            val query = obj["pivotQuery"].orEmpty()
            if (query.isBlank()) return@mapNotNull null
            ConnectionEdge(
                relation = obj["relation"].orEmpty(),
                target = obj["target"].orEmpty(),
                detail = obj["detail"].orEmpty(),
                source = obj["source"].orEmpty(),
                pivotType = obj["pivotType"] ?: "person",
                pivotQuery = query
            )
        }

    fun parseTimelineFromJson(json: String): List<TimelineEvent> =
        parseJsonObjects(json).map { obj ->
            TimelineEvent(
                kind = obj["kind"].orEmpty(),
                title = obj["title"].orEmpty(),
                detail = obj["detail"].orEmpty(),
                era = obj["era"].orEmpty(),
                source = obj["source"].orEmpty(),
                sortOrder = obj["sortOrder"]?.toIntOrNull() ?: 5000
            )
        }

    private fun parseJobLine(line: String): Quad<String, String, Boolean, String?>? {
        val trimmed = line.trim()
        JOB_AT_REGEX.matchEntire(trimmed)?.let { m ->
            val suffix = m.groupValues[3].trim()
            val current = suffix.equals("current", ignoreCase = true)
            val end = if (suffix.startsWith("until", ignoreCase = true)) {
                suffix.removePrefix("until").trim().ifBlank { null }
            } else null
            return Quad(m.groupValues[1].trim(), m.groupValues[2].trim(), current, end)
        }
        if (trimmed.contains(" at ", ignoreCase = true)) {
            val parts = trimmed.split(" at ", ignoreCase = true, limit = 2)
            if (parts.size == 2) {
                val company = parts[1].substringBefore(" (").trim()
                val current = trimmed.contains("(current", ignoreCase = true)
                val end = Regex("""\(until\s+([^)]+)\)""", RegexOption.IGNORE_CASE)
                    .find(trimmed)?.groupValues?.get(1)?.trim()
                return Quad(parts[0].trim(), company, current, end)
            }
        }
        return null
    }

    private fun inferCareerTransition(metadata: MutableMap<String, String>, jobs: List<Employment>) {
        val current = jobs.filter { it.isCurrent }.map { it.companyName }.distinct()
        val past = jobs.filter { !it.isCurrent }.map { it.companyName }.distinct()
        if (current.isEmpty() || past.isEmpty()) return
        val from = past.first()
        val to = current.first()
        if (from != to) {
            metadata["career_transition"] = "Career path: $from → $to"
        }
    }

    private fun timelineToJson(events: List<TimelineEvent>): String {
        if (events.isEmpty()) return "[]"
        return events.joinToString(prefix = "[", postfix = "]") { event ->
            buildString {
                append("{")
                append("\"kind\":").append(jsonString(event.kind)).append(',')
                append("\"title\":").append(jsonString(event.title)).append(',')
                append("\"detail\":").append(jsonString(event.detail)).append(',')
                append("\"era\":").append(jsonString(event.era)).append(',')
                append("\"source\":").append(jsonString(event.source)).append(',')
                append("\"sortOrder\":").append(event.sortOrder)
                append("}")
            }
        }
    }

    private fun connectionsToJson(connections: List<ConnectionEdge>): String {
        if (connections.isEmpty()) return "[]"
        return connections.joinToString(prefix = "[", postfix = "]") { edge ->
            buildString {
                append("{")
                append("\"relation\":").append(jsonString(edge.relation)).append(',')
                append("\"target\":").append(jsonString(edge.target)).append(',')
                append("\"detail\":").append(jsonString(edge.detail)).append(',')
                append("\"source\":").append(jsonString(edge.source)).append(',')
                append("\"pivotType\":").append(jsonString(edge.pivotType)).append(',')
                append("\"pivotQuery\":").append(jsonString(edge.pivotQuery))
                append("}")
            }
        }
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun parseJsonObjects(json: String): List<Map<String, String>> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        return splitTopLevelObjects(inner).mapNotNull { parseJsonObject(it) }
    }

    private fun splitTopLevelObjects(inner: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = 0
        inner.forEachIndexed { index, char ->
            when (char) {
                '{' -> if (depth++ == 0) start = index
                '}' -> if (--depth == 0) objects.add(inner.substring(start, index + 1))
            }
        }
        return objects
    }

    private fun parseJsonObject(raw: String): Map<String, String>? {
        val body = raw.trim().removePrefix("{").removeSuffix("}").trim()
        if (body.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        var i = 0
        while (i < body.length) {
            val keyStart = body.indexOf('"', i)
            if (keyStart < 0) break
            val keyEnd = body.indexOf('"', keyStart + 1)
            if (keyEnd < 0) break
            val key = body.substring(keyStart + 1, keyEnd)
            val colon = body.indexOf(':', keyEnd)
            if (colon < 0) break
            var valueStart = colon + 1
            while (valueStart < body.length && body[valueStart].isWhitespace()) valueStart++
            if (valueStart >= body.length) break
            val (value, nextIndex) = when (body[valueStart]) {
                '"' -> {
                    val sb = StringBuilder()
                    var j = valueStart + 1
                    while (j < body.length) {
                        when (val c = body[j]) {
                            '\\' -> {
                                if (j + 1 < body.length) {
                                    sb.append(
                                        when (body[j + 1]) {
                                            'n' -> '\n'
                                            '"' -> '"'
                                            '\\' -> '\\'
                                            else -> body[j + 1]
                                        }
                                    )
                                    j += 2
                                } else j++
                            }
                            '"' -> {
                                j++
                                break
                            }
                            else -> {
                                sb.append(c)
                                j++
                            }
                        }
                    }
                    sb.toString() to j
                }
                else -> {
                    val end = body.indexOf(',', valueStart).let { if (it < 0) body.length else it }
                    body.substring(valueStart, end).trim() to end
                }
            }
            map[key] = value
            i = body.indexOf(',', nextIndex).let { if (it < 0) body.length else it + 1 }
        }
        return map
    }

    private fun extractRelatives(metadata: Map<String, String>): LinkedHashSet<String> {
        val set = linkedSetOf<String>()
        listOf(
            "search_relatives", "pipl_relatives", "pdl_associates", "tps_relatives",
            "411_relatives", "zaba_relatives", "radaris_relatives", "nuwber_relatives", "corpwiki_associates"
        ).forEach { key ->
            metadata[key]?.split(",")?.map { it.trim() }?.filter { it.length > 3 }?.forEach { set.add(it) }
        }
        return set
    }

    private fun extractPhones(metadata: Map<String, String>): List<String> {
        val set = linkedSetOf<String>()
        listOf("person_phone", "pipl_phone", "pipl_phones", "pdl_phones", "search_phones")
            .forEach { key ->
                metadata[key]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) }
            }
        return set.toList()
    }

    private fun extractEmails(metadata: Map<String, String>): List<String> {
        val set = linkedSetOf<String>()
        listOf("pipl_email", "pipl_emails", "pdl_emails", "clearbit_person_email")
            .forEach { key ->
                metadata[key]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { set.add(it) }
            }
        return set.toList()
    }

    private fun extractAddresses(metadata: Map<String, String>): List<String> {
        val set = linkedSetOf<String>()
        listOf("search_addresses", "pipl_addresses", "pdl_address", "voter_addresses")
            .forEach { key ->
                metadata[key]?.split(" | ", ",")?.map { it.trim() }?.filter { it.length > 8 }?.forEach { set.add(it) }
            }
        return set.toList()
    }

    private fun inferRelativeSource(metadata: Map<String, String>, name: String): String {
        listOf("pipl_relatives" to "Pipl", "pdl_associates" to "People Data Labs", "tps_relatives" to "TruePeopleSearch")
            .forEach { (key, source) ->
                if (metadata[key]?.contains(name, ignoreCase = true) == true) return source
            }
        return "Records"
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D?)
}
