package com.plakagozcusu.core.data.ai

import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.vision.pipeline.plate.TurkishPlateParser
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** Outcome of one AI request. */
sealed interface AiResult {
    /** [plate] is the validated civilian plate the AI saw, or null when it judged the plate unreadable. */
    data class Read(
        val plate: String?,
    ) : AiResult

    /** Network / quota problems: worth retrying later. */
    data class Transient(
        val code: Int,
    ) : AiResult

    /** Bad key, bad model id or a malformed answer: retrying will not help. */
    data class Permanent(
        val code: Int,
    ) : AiResult
}

/**
 * Google Gemini (generateContent REST) as an optional second opinion. Sends ONLY the plate (or
 * vehicle) crop and the on-device candidate readings; asks for strict JSON. The answer is accepted
 * only if it parses as a valid Turkish civilian plate — the model cannot invent a free-form value.
 */
class GeminiPlateVerifier(
    private val apiKey: String,
    private val model: String,
) {
    fun verify(
        jpeg: ByteArray,
        candidates: List<String>,
    ): AiResult {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            // Header, not query string: keeps the key out of URLs and proxy logs.
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(requestBody(jpeg, candidates).toByteArray()) }
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                HTTP_TOO_MANY, in SERVER_ERRORS -> AiResult.Transient(code)
                else -> AiResult.Permanent(code)
            }
        } catch (
            // Offline / timeout: WorkManager retries later. The message is dropped on purpose (may echo the URL).
            @Suppress("SwallowedException") e: IOException,
        ) {
            AiResult.Transient(NETWORK_ERROR)
        } finally {
            conn.disconnect()
        }
    }

    internal fun requestBody(
        jpeg: ByteArray,
        candidates: List<String>,
    ): String {
        val prompt =
            "You are an expert reader of Turkish vehicle licence plates. The image is a crop from a home " +
                "security camera. Turkish civilian plates are: 2-digit province code 01-81, then 1-3 letters, then " +
                "2-5 digits (1 letter + 4-5 digits, 2 letters + 3-4 digits, 3 letters + 2-3 digits). Letters used: " +
                "A B C D E F G H I J K L M N O P R S T U V Y Z (never Q, W, X, Ç, Ğ, İ, Ö, Ş, Ü). Ignore the blue " +
                "'TR' band. Typical OCR mistakes to check carefully: D vs O vs 0, F vs E, T vs L or I, B vs 8, " +
                "S vs 5, Z vs 2, G vs 6. In the letter group there are never digits; in the number group never " +
                "letters. On-device OCR candidates: " +
                (candidates.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none") +
                ". Do not guess characters you cannot see. Answer ONLY with JSON: " +
                "{\"plate\": \"<province> <letters> <digits>\" or null, \"readable\": true|false}"
        val part = JSONObject().put("text", prompt)
        val image =
            JSONObject().put(
                "inline_data",
                JSONObject().put("mime_type", "image/jpeg").put("data", Base64.getEncoder().encodeToString(jpeg)),
            )
        val content =
            JSONObject().put(
                "parts",
                org.json
                    .JSONArray()
                    .put(part)
                    .put(image),
            )
        return JSONObject()
            .put("contents", org.json.JSONArray().put(content))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0))
            .toString()
    }

    internal fun parse(body: String): AiResult =
        runCatching {
            val text =
                JSONObject(body)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            val answer = JSONObject(text.trim().removePrefix("```json").removeSuffix("```"))
            val raw = answer.optString("plate").takeIf { answer.optBoolean("readable", false) && it.isNotBlank() && it != "null" }
            val parsed = raw?.let(TurkishPlateParser::parse)?.takeIf { it.isValidCivilian }
            AiResult.Read(parsed?.formatted)
        }.getOrElse { AiResult.Permanent(MALFORMED) }

    companion object {
        const val PROVIDER = "gemini"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        private const val TIMEOUT_MS = 20_000
        private const val HTTP_TOO_MANY = 429
        private val SERVER_ERRORS = 500..599
        private const val MALFORMED = -2
        private const val NETWORK_ERROR = -1
    }
}

/**
 * Pure status rule for an AI answer (unit-tested). User-confirmed records are never touched.
 * Agreement upgrades to HIGH_CONFIDENCE; a different or new reading becomes a NEEDS_REVIEW suggestion.
 */
object AiDecision {
    fun apply(
        current: RecognitionStatus,
        currentPlate: String?,
        aiPlate: String?,
    ): Pair<RecognitionStatus, Boolean> {
        if (current.isUserConfirmed) return current to false
        val agrees = aiPlate != null && normalize(aiPlate) == currentPlate?.let(::normalize)
        val status =
            when {
                agrees -> RecognitionStatus.HIGH_CONFIDENCE
                aiPlate != null -> RecognitionStatus.NEEDS_REVIEW
                else -> current
            }
        return status to agrees
    }

    private fun normalize(s: String) = s.filter { it.isLetterOrDigit() }.uppercase()
}
