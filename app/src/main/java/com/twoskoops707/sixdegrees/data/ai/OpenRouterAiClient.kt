package com.twoskoops707.sixdegrees.data.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenRouter chat-completions client for structured OSINT dossier synthesis.
 * Uses free Llama 3.3 70B by default; requires user-provided API key.
 * Sign up: https://openrouter.ai/keys
 */
object OpenRouterAiClient {

    private const val CHAT_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val DEFAULT_MODEL = "meta-llama/llama-3.3-70b-instruct:free"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """You are an OSINT intelligence analyst synthesizing collected open-source data into a structured dossier.
STRICT RULES (ai_facts_only=true):
- ONLY cite facts explicitly present in the provided metadata keys and values.
- NEVER invent, guess, or infer phone numbers, addresses, emails, names, employers, or ages.
- If a field has no backing data in the input, do not mention it at all.
- Do not use outside knowledge about the subject.
- Flag likely false positives (common names, mismatched locations, unverified social profiles).
- Be concise and analytical; no filler.
- Respond with valid JSON only, no markdown fences."""

    private const val JSON_SCHEMA_HINT = """Return JSON with exactly these keys:
{
  "executive_summary": "2-4 sentence overview",
  "key_findings": ["finding 1", "finding 2"],
  "confidence": "high|medium|low",
  "confidence_rationale": "why this confidence level",
  "false_positive_notes": ["possible false match reason"],
  "recommended_next_steps": ["actionable follow-up"]
}"""

    fun generateReport(apiKey: String, contextPrompt: String, model: String = DEFAULT_MODEL): OsintAiReport? {
        val userPrompt = buildString {
            appendLine(contextPrompt.trim())
            appendLine()
            appendLine(JSON_SCHEMA_HINT)
        }
        val content = query(apiKey, model, userPrompt, useJsonMode = true) ?: return null
        return OsintAiReport.fromJson(content, "openrouter")
            ?: OsintAiReport.fromPlainText(content, "openrouter").takeIf { it.executiveSummary.isNotBlank() }
    }

    private const val SUGGESTED_SEARCHES_SCHEMA = """Return JSON with exactly this shape:
{
  "suggested_searches": [
    {"label": "short action label", "query": "specific DuckDuckGo search query"}
  ]
}
Rules:
- Suggest 3-5 highly targeted follow-up searches based on gaps in the collected OSINT data.
- Each query must be specific to this subject (include name, city, employer, username when known).
- Do not repeat searches already covered by listed sources.
- Output query strings only — not URLs.
- Respond with valid JSON only, no markdown fences."""

    /**
     * Suggests follow-up DuckDuckGo queries from investigation context (no autonomous scraping).
     */
    fun generateSuggestedSearches(
        apiKey: String,
        contextPrompt: String,
        model: String = DEFAULT_MODEL
    ): List<AiSuggestedSearch>? {
        val userPrompt = buildString {
            appendLine(contextPrompt.trim())
            appendLine()
            appendLine("Based on the investigation above, what 3-5 follow-up web searches would best fill data gaps?")
            appendLine()
            appendLine(SUGGESTED_SEARCHES_SCHEMA)
        }
        val content = query(
            apiKey, model, userPrompt, useJsonMode = true,
            systemPrompt = """You are an OSINT analyst recommending follow-up search queries.
Be specific and actionable. Never invent facts about the subject."""
        ) ?: return null
        return parseSuggestedSearches(content)
    }

    private fun parseSuggestedSearches(content: String): List<AiSuggestedSearch>? {
        return try {
            val trimmed = content.trim()
            val jsonStart = trimmed.indexOf('{')
            val jsonEnd = trimmed.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd <= jsonStart) return null
            val arr = JSONObject(trimmed.substring(jsonStart, jsonEnd + 1))
                .optJSONArray("suggested_searches") ?: return null
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val label = obj.optString("label").trim()
                val query = obj.optString("query").trim()
                if (label.isBlank() || query.isBlank()) null
                else AiSuggestedSearch(label, query)
            }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun query(
        apiKey: String,
        model: String,
        userPrompt: String,
        useJsonMode: Boolean,
        systemPrompt: String = SYSTEM_PROMPT
    ): String? {
        return try {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
            put("max_tokens", 900)
            put("temperature", 0.3)
            if (useJsonMode) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
        }.toString()

        val req = Request.Builder()
            .url(CHAT_URL)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://6degrees.app")
            .header("X-Title", "6Degrees OSINT")
            .build()

        val resp = httpClient.newCall(req).execute()
        val respBody = resp.body?.string().orEmpty()
        resp.close()
        if (!resp.isSuccessful || respBody.isBlank()) {
            null
        } else {
            JSONObject(respBody)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
        } catch (_: Exception) {
            null
        }
    }
}

/** Label + DuckDuckGo query string (converted to URL by caller). */
data class AiSuggestedSearch(val label: String, val query: String)
