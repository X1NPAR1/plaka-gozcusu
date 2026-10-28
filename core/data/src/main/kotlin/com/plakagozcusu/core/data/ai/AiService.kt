package com.plakagozcusu.core.data.ai

import com.plakagozcusu.core.common.DispatcherProvider
import com.plakagozcusu.core.data.ObservationRepository
import com.plakagozcusu.core.datastore.SecretStore
import com.plakagozcusu.core.datastore.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Result of one AI check, with a reason the UI can explain in Turkish. */
sealed interface AiCheckOutcome {
    data class Done(
        val plate: String?,
    ) : AiCheckOutcome

    data object NoKey : AiCheckOutcome

    data object NoImage : AiCheckOutcome

    data object UserConfirmed : AiCheckOutcome

    /** HTTP status (401/403 key, 404 model, 429 quota, -1 offline, -2 malformed answer). */
    data class Failed(
        val code: Int,
    ) : AiCheckOutcome
}

/** Shared AI operations for the background worker and the "Yapay zekâya sor" button. */
@Singleton
class AiService
    @Inject
    constructor(
        private val repository: ObservationRepository,
        private val settings: SettingsRepository,
        private val secrets: SecretStore,
        private val dispatchers: DispatcherProvider,
    ) {
        /** Runs one check now. [requireEnabled] = false for an explicit user request. */
        suspend fun check(
            observationId: String,
            requireEnabled: Boolean,
        ): AiCheckOutcome =
            withContext(dispatchers.io) {
                val s = settings.settings.first()
                val key = secrets.aiKey()
                val o = repository.get(observationId)
                val crop = o?.let { it.plateCrop ?: it.vehicleCrop }?.let(repository::resolveFile)?.takeIf { it.isFile }
                when {
                    key == null || (requireEnabled && !s.aiEnabled) -> {
                        AiCheckOutcome.NoKey
                    }

                    o == null || crop == null -> {
                        AiCheckOutcome.NoImage
                    }

                    o.recognitionStatus.isUserConfirmed -> {
                        AiCheckOutcome.UserConfirmed
                    }

                    else -> {
                        val candidates = listOfNotNull(o.plateText, o.rawOcrText).distinct()
                        val r = verifyWithFallback(key, s.aiModel, crop.readBytes(), candidates)
                        when (r) {
                            is AiResult.Read -> {
                                repository.applyAiOpinion(observationId, r.plate, GeminiPlateVerifier.PROVIDER)
                                AiCheckOutcome.Done(r.plate)
                            }

                            is AiResult.Transient -> {
                                AiCheckOutcome.Failed(r.code)
                            }

                            is AiResult.Permanent -> {
                                AiCheckOutcome.Failed(r.code)
                            }
                        }
                    }
                }
            }

        /** One request; on 404 (model retired / not offered for this key) switch to an available model and retry once. */
        private suspend fun verifyWithFallback(
            key: String,
            model: String,
            image: ByteArray,
            candidates: List<String>,
        ): AiResult {
            val first = GeminiPlateVerifier(key, model).verify(image, candidates)
            if (first !is AiResult.Permanent || first.code != HTTP_NOT_FOUND) return first
            val fallback = listModels(key).getOrNull()?.firstOrNull { it != model } ?: return first
            settings.update { it.copy(aiModel = fallback) }
            return GeminiPlateVerifier(key, fallback).verify(image, candidates)
        }

        /**
         * Models available to this key that can read images and generate text (Gemini ListModels).
         * Image-generation, embedding, TTS and live/audio-only models are filtered out.
         */
        suspend fun listModels(key: String): Result<List<String>> =
            withContext(dispatchers.io) {
                runCatching {
                    val conn =
                        URL(
                            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
                        ).openConnection() as HttpURLConnection
                    try {
                        conn.connectTimeout = TIMEOUT_MS
                        conn.readTimeout = TIMEOUT_MS
                        conn.setRequestProperty("x-goog-api-key", key.trim())
                        if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
                        parseModels(conn.inputStream.bufferedReader().use { it.readText() })
                    } finally {
                        conn.disconnect()
                    }
                }
            }

        companion object {
            private const val TIMEOUT_MS = 15_000
            private const val HTTP_NOT_FOUND = 404
            private val EXCLUDED = listOf("image", "embedding", "tts", "audio", "live", "aqa", "imagen", "veo", "learnlm")

            internal fun parseModels(body: String): List<String> {
                val arr = JSONObject(body).optJSONArray("models") ?: return emptyList()
                return (0 until arr.length())
                    .map { arr.getJSONObject(it) }
                    .filter { m ->
                        val methods = m.optJSONArray("supportedGenerationMethods")
                        val canGenerate = methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                        val id = m.getString("name").removePrefix("models/")
                        canGenerate && id.startsWith("gemini") && EXCLUDED.none { id.contains(it) }
                    }.map { it.getString("name").removePrefix("models/") }
                    .sortedWith(compareBy({ !it.contains("flash") }, { it }))
            }
        }
    }
