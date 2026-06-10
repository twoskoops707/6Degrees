package com.twoskoops707.sixdegrees.data.osint

import java.util.concurrent.ConcurrentHashMap

/**
 * Tags report metadata with which OSINT Framework tools contributed in-app data.
 */
object OsintFrameworkReportBridge {

    const val KEY_TOOLS_USED = "osint_framework_tools_used"
    const val KEY_REPORT_LINES = "osint_framework_report_lines"
    const val KEY_EXECUTOR_COUNT = "osint_framework_executors_run"

    fun apply(
        metadata: ConcurrentHashMap<String, String>,
        frameworkTools: List<OsintToolRegistry.OsintTool>
    ) {
        val activeExecutors = OsintInAppExecutorCatalog.executorsWithReportData(metadata)
        if (activeExecutors.isEmpty()) return

        val executorIds = activeExecutors.map { it.id }.toSet()
        val matchedTools = frameworkTools.filter { tool ->
            val executor = OsintInAppExecutorCatalog.resolveExecutor(
                tool.name,
                tool.urlTemplate,
                tool.frameworkPath
            )
            executor != null && executor.id in executorIds
        }

        val toolNames = matchedTools.map { it.name }.distinct().sorted()
        if (toolNames.isEmpty()) {
            val fallback = activeExecutors.map { it.reportLabel }.distinct()
            metadata[KEY_TOOLS_USED] = fallback.joinToString(", ")
            metadata[KEY_REPORT_LINES] = fallback.joinToString("\n") { "$it: data collected in-app" }
        } else {
            metadata[KEY_TOOLS_USED] = toolNames.joinToString(", ")
            metadata[KEY_REPORT_LINES] = toolNames.joinToString("\n") { toolLine(it, metadata, frameworkTools) }
        }
        metadata[KEY_EXECUTOR_COUNT] = activeExecutors.size.toString()
    }

    private fun toolLine(
        toolName: String,
        metadata: Map<String, String>,
        frameworkTools: List<OsintToolRegistry.OsintTool>
    ): String {
        val tool = frameworkTools.firstOrNull { it.name == toolName } ?: return toolName
        val executor = OsintInAppExecutorCatalog.resolveExecutor(
            tool.name,
            tool.urlTemplate,
            tool.frameworkPath
        ) ?: return "$toolName: in-app"
        val sample = metadata.entries
            .firstOrNull { (key, value) ->
                value.isNotBlank() && (
                    executor.metadataExactKeys.contains(key) ||
                        executor.metadataPrefixes.any { key.startsWith(it) }
                    )
            }
            ?.let { "${it.key}=${it.value.take(80)}" }
        return if (sample != null) "$toolName: $sample" else "$toolName: ${executor.reportLabel}"
    }
}
