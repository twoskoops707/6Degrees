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
Rules:
- Only state facts supported by the provided data; mark speculation clearly.
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

    private fun query(
        apiKey: String,
        model: String,
        userPrompt: String,
        useJsonMode: Boolean
    ): String? {
        return try {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
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
