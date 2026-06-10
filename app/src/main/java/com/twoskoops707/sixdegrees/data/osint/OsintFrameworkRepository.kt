package com.twoskoops707.sixdegrees.data.osint

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * OSINT Framework catalog limited to free, workable tools backed by in-app executors that feed dossiers.
 */
class OsintFrameworkRepository private constructor(
    private val templateOverrides: Map<String, String>
) {

    data class FrameworkTool(
        val id: String,
        val name: String,
        val description: String,
        val urlTemplate: String,
        val categories: Set<String>,
        val frameworkPath: List<String>,
        val inputHint: String,
        val isQueryable: Boolean,
        val executorId: OsintInAppExecutorCatalog.ExecutorId
    )

    private var cachedTools: List<FrameworkTool>? = null

    fun loadTools(context: Context): List<FrameworkTool> {
        cachedTools?.let { return it }
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val frameworkNodes = mutableListOf<FrameworkNode>()
        walkNodes(JSONObject(json).optJSONArray("children") ?: JSONArray(), emptyList(), frameworkNodes)

        val matched = mutableListOf<FrameworkTool>()
        val coveredExecutors = mutableSetOf<OsintInAppExecutorCatalog.ExecutorId>()

        for (node in frameworkNodes) {
            val executor = OsintInAppExecutorCatalog.resolveExecutor(node.name, node.url, node.path) ?: continue
            coveredExecutors.add(executor.id)
            matched += node.toTool(executor, templateOverrides)
        }

        // Ensure core in-app runners appear even when framework only lists CLI (T) variants.
        for (executor in OsintInAppExecutorCatalog.EXECUTORS) {
            if (executor.id !in coveredExecutors &&
                executor.id in setOf(
                    OsintInAppExecutorCatalog.ExecutorId.USERNAME_SCAN,
                    OsintInAppExecutorCatalog.ExecutorId.EMAIL_REGISTRATION
                )
            ) {
                matched += syntheticTool(executor)
            }
        }

        cachedTools = matched.distinctBy { it.id }.sortedBy { it.name.lowercase() }
        return cachedTools!!
    }

    fun toolCount(context: Context): Int = loadTools(context).size

    private data class FrameworkNode(
        val name: String,
        val url: String,
        val path: List<String>,
        val description: String,
        val inputHint: String
    )

    private fun FrameworkNode.toTool(
        executor: OsintInAppExecutorCatalog.ExecutorDef,
        overrides: Map<String, String>
    ): FrameworkTool {
        val normalized = OsintUrlBuilder.normalizeToolName(name)
        val template = overrides[normalized] ?: url
        val categories = OsintFrameworkCategoryMapper.categoriesForPath(path).ifEmpty { executor.categories }
        return FrameworkTool(
            id = "${executor.id.name.lowercase()}_${normalized}_${url.hashCode()}",
            name = name,
            description = "In-app via ${executor.reportLabel}. ${description.ifBlank { executor.catalogDescription }}".trim(),
            urlTemplate = template,
            categories = categories,
            frameworkPath = path,
            inputHint = inputHint,
            isQueryable = OsintUrlBuilder.isQueryableTemplate(template),
            executorId = executor.id
        )
    }

    private fun syntheticTool(executor: OsintInAppExecutorCatalog.ExecutorDef): FrameworkTool =
        FrameworkTool(
            id = "synthetic_${executor.id.name.lowercase()}",
            name = executor.catalogName,
            description = executor.catalogDescription,
            urlTemplate = "",
            categories = executor.categories,
            frameworkPath = listOf("SixDegrees In-App"),
            inputHint = "",
            isQueryable = false,
            executorId = executor.id
        )

    private fun walkNodes(nodes: JSONArray, path: List<String>, out: MutableList<FrameworkNode>) {
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            val name = node.optString("name", "")
            val type = node.optString("type", "")
            val childPath = if (name.isNotBlank()) path + name else path

            if (type == "url" && OsintFrameworkToolFilter.isFreeAndWorkable(node)) {
                val url = node.optString("url", "").trim()
                val description = node.optString("description", "")
                    .ifBlank { node.optString("bestFor", "") }
                    .ifBlank { node.optString("input", "") }
                    .ifBlank { childPath.drop(1).joinToString(" · ") }
                out += FrameworkNode(
                    name = name,
                    url = url,
                    path = childPath,
                    description = description,
                    inputHint = node.optString("input", "")
                )
            }

            val children = node.optJSONArray("children")
            if (children != null && children.length() > 0) {
                walkNodes(children, childPath, out)
            }
        }
    }

    companion object {
        const val ASSET_PATH = "osint-framework/arf.json"
        const val FRAMEWORK_ATTRIBUTION =
            "Free OSINT Framework tools with in-app executors that populate your dossier (lockfale/OSINT-Framework)"

        @Volatile
        private var instance: OsintFrameworkRepository? = null

        fun getInstance(templateOverrides: Map<String, String> = emptyMap()): OsintFrameworkRepository {
            if (instance == null || templateOverrides.isNotEmpty()) {
                instance = OsintFrameworkRepository(templateOverrides)
            }
            return instance!!
        }

        fun resetForTests() {
            instance = null
        }
    }
}
