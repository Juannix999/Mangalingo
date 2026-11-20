package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.core.SupportedLanguage
import com.example.core.activeAppCheckProviderLabel
import com.example.data.model.DetectedBubble
import com.example.data.model.TranslationAnalysis
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class AiTranslationRepository {

    private val bubbleScanSchema = Schema.obj(
        properties = mapOf(
            "detectedLang" to Schema.string(description = "ISO 639-1 language code of text on the manga page, e.g. ja, en, es, fr, ko"),
            "bubbles" to Schema.array(
                description = "List of speech bubbles or text blocks detected on the manga page",
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string(description = "Unique short identifier like b1, b2"),
                        "rawText" to Schema.string(description = "Complete transcribed text in the speech bubble"),
                        "tokens" to Schema.array(
                            items = Schema.string(),
                            description = "Individual words or meaningful lexical tokens in reading order"
                        ),
                        "ymin" to Schema.integer(description = "Top normalized coordinate 0..1000"),
                        "xmin" to Schema.integer(description = "Left normalized coordinate 0..1000"),
                        "ymax" to Schema.integer(description = "Bottom normalized coordinate 0..1000"),
                        "xmax" to Schema.integer(description = "Right normalized coordinate 0..1000")
                    )
                )
            )
        )
    )

    private val translationSchema = Schema.obj(
        properties = mapOf(
            "hasTranslation" to Schema.boolean(description = "True if the selected word or joined words form a valid translatable word, compound, or phrase; false if random meaningless fragment"),
            "detectedSourceLang" to Schema.string(description = "Detected ISO language code such as ja, en, es, fr, ko, pt, it, de"),
            "translatedText" to Schema.string(description = "Natural translation of the selected word or multi-word phrase into target language"),
            "reading" to Schema.string(description = "Phonetic reading, furigana/romaji for Japanese/Korean, or pronunciation guide; empty if unnecessary"),
            "partOfSpeech" to Schema.string(description = "Grammatical category or phrase type in target language"),
            "contextNote" to Schema.string(description = "Brief explanation of nuance or meaning in the manga dialogue written in target language"),
            "literalBreakdown" to Schema.string(description = "Word-by-word breakdown when 2+ words are selected, or root meaning"),
            "alternatives" to Schema.array(
                items = Schema.string(),
                description = "Up to 3 alternative translations in target language"
            )
        )
    )

    private val cropTranslationSchema = Schema.obj(
        properties = mapOf(
            "transcribedText" to Schema.string(description = "Exact text transcribed from the cropped manga image region"),
            "wordCount" to Schema.integer(description = "Approximate word or lexical token count"),
            "hasTranslation" to Schema.boolean(description = "True if valid translatable text was found in the region"),
            "detectedSourceLang" to Schema.string(description = "Detected ISO language code"),
            "translatedText" to Schema.string(description = "Natural translation into target language"),
            "reading" to Schema.string(description = "Romaji/furigana/pronunciation if applicable, else empty"),
            "partOfSpeech" to Schema.string(description = "Grammatical category"),
            "contextNote" to Schema.string(description = "Brief explanation in target language"),
            "literalBreakdown" to Schema.string(description = "Literal breakdown"),
            "alternatives" to Schema.array(items = Schema.string())
        )
    )

    private val bubbleScanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = bubbleScanSchema
            }
        )
    }

    private val translationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
            }
        )
    }

    private val cropTranslationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = cropTranslationSchema
            }
        )
    }

    suspend fun scanMangaPageBubbles(
        bitmap: Bitmap,
        sourceLangHint: String?
    ): Result<Pair<String, List<DetectedBubble>>> = withContext(Dispatchers.IO) {
        try {
            val prepared = prepareBitmapForAi(bitmap, maxDimension = 1024)
            val langHintText = sourceLangHint?.takeIf { it.isNotBlank() } ?: "unknown"
            val prompt = """
                Detect all dialogue speech bubbles on this manga page (language hint: $langHintText).
                For each bubble return ymin, xmin, ymax, xmax (0..1000), rawText, and tokens (individual words/morphemes in reading order).
                Return JSON only matching the schema. If no speech bubbles exist, return an empty bubbles list.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                bubbleScanModel.generateContent(
                    content {
                        image(prepared)
                        text(prompt)
                    }
                )
            }
            val jsonStr = response.text ?: throw IllegalStateException("El modelo devolvió una respuesta vacía.")
            val root = JSONObject(jsonStr)
            val detectedLang = root.optString("detectedLang", sourceLangHint ?: "ja").ifBlank { "ja" }
            val bubblesArr = root.optJSONArray("bubbles")
            val bubbles = mutableListOf<DetectedBubble>()
            if (bubblesArr != null) {
                for (i in 0 until bubblesArr.length()) {
                    val item = bubblesArr.getJSONObject(i)
                    val rawText = item.optString("rawText", "").trim()
                    if (rawText.isBlank()) continue
                    val tokensArr = item.optJSONArray("tokens")
                    val tokens = mutableListOf<String>()
                    if (tokensArr != null) {
                        for (j in 0 until tokensArr.length()) {
                            val t = tokensArr.optString(j, "").trim()
                            if (t.isNotEmpty()) tokens.add(t)
                        }
                    }
                    if (tokens.isEmpty()) {
                        tokens.addAll(rawText.split(Regex("\\s+")).filter { it.isNotBlank() })
                    }
                    bubbles.add(
                        DetectedBubble(
                            id = item.optString("id", "b$i").ifBlank { "b$i" },
                            rawText = rawText,
                            tokens = tokens,
                            yMin = item.optInt("ymin", 100).coerceIn(0, 999),
                            xMin = item.optInt("xmin", 100).coerceIn(0, 999),
                            yMax = item.optInt("ymax", 300).coerceIn(1, 1000),
                            xMax = item.optInt("xmax", 500).coerceIn(1, 1000)
                        )
                    )
                }
            }
            Result.success(detectedLang to bubbles)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Bubble scan timed out", e)
            Result.failure(Exception("El escaneo superó los 45s. Usa Recorte o mantén pulsado directamente sobre un globo."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bubble scan failed", e)
            Result.failure(Exception(friendlyAiError(e)))
        }
    }

    suspend fun translateSelection(
        selectedText: String,
        fullBubbleContext: String,
        sourceLang: String,
        targetLang: String,
        wordCount: Int
    ): Result<TranslationAnalysis> = withContext(Dispatchers.IO) {
        try {
            val targetName = SupportedLanguage.displayName(targetLang)
            val sourceName = SupportedLanguage.displayName(sourceLang)
            val prompt = """
                Translate the selected manga text from $sourceName into $targetName.
                Selected ($wordCount word(s)): "$selectedText"
                Bubble context: "$fullBubbleContext"

                Rules:
                - Set hasTranslation=true if the word or joined $wordCount words have a valid translation or phrase meaning, or false if random untranslatable fragment/punctuation.
                - Write translatedText, partOfSpeech, contextNote, literalBreakdown, and alternatives in $targetName.
                - Provide reading (romaji/furigana/pronunciation) when helpful, else empty string.
            """.trimIndent()

            val response = withTimeout(35_000L) {
                translationModel.generateContent(prompt)
            }
            val jsonStr = response.text ?: throw IllegalStateException("El modelo devolvió una respuesta vacía.")
            val json = JSONObject(jsonStr)
            val alternativesArr = json.optJSONArray("alternatives")
            val alternatives = buildList {
                if (alternativesArr != null) {
                    for (i in 0 until alternativesArr.length()) {
                        val alt = alternativesArr.optString(i, "").trim()
                        if (alt.isNotEmpty()) add(alt)
                    }
                }
            }
            Result.success(
                TranslationAnalysis(
                    sourceText = selectedText.trim(),
                    translatedText = json.optString("translatedText", "").ifBlank { "Sin traducción disponible" },
                    reading = json.optString("reading", ""),
                    sourceLang = json.optString("detectedSourceLang", sourceLang).ifBlank { sourceLang },
                    targetLang = targetLang,
                    wordCount = wordCount.coerceAtLeast(1),
                    hasTranslation = json.optBoolean("hasTranslation", true),
                    partOfSpeech = json.optString("partOfSpeech", ""),
                    contextNote = json.optString("contextNote", ""),
                    literalBreakdown = json.optString("literalBreakdown", ""),
                    alternatives = alternatives
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Translation timed out", e)
            Result.failure(Exception("La traducción tardó demasiado (35s). Toca Reintentar."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            Result.failure(Exception(friendlyAiError(e)))
        }
    }

    suspend fun translateImageCrop(
        cropBitmap: Bitmap,
        sourceLangHint: String?,
        targetLang: String
    ): Result<TranslationAnalysis> = withContext(Dispatchers.IO) {
        try {
            val prepared = prepareBitmapForAi(cropBitmap, maxDimension = 900)
            val targetName = SupportedLanguage.displayName(targetLang)
            val sourceHint = sourceLangHint?.takeIf { it.isNotBlank() } ?: "auto-detect"
            val prompt = """
                Transcribe the word or multi-word phrase visible in this cropped manga image region (hint: $sourceHint) and translate it into $targetName.
                If no readable text is present, set hasTranslation=false and explain in $targetName.
                Provide reading, partOfSpeech, contextNote, literalBreakdown, and alternatives in $targetName.
            """.trimIndent()

            val response = withTimeout(35_000L) {
                cropTranslationModel.generateContent(
                    content {
                        image(prepared)
                        text(prompt)
                    }
                )
            }
            val jsonStr = response.text ?: throw IllegalStateException("El modelo devolvió una respuesta vacía.")
            val json = JSONObject(jsonStr)
            val transcribed = json.optString("transcribedText", "").trim()
            val alternativesArr = json.optJSONArray("alternatives")
            val alternatives = buildList {
                if (alternativesArr != null) {
                    for (i in 0 until alternativesArr.length()) {
                        val alt = alternativesArr.optString(i, "").trim()
                        if (alt.isNotEmpty()) add(alt)
                    }
                }
            }
            Result.success(
                TranslationAnalysis(
                    sourceText = transcribed.ifBlank { "(Región seleccionada)" },
                    translatedText = json.optString("translatedText", "").ifBlank { "No se detectó texto traducible" },
                    reading = json.optString("reading", ""),
                    sourceLang = json.optString("detectedSourceLang", sourceLangHint ?: "ja"),
                    targetLang = targetLang,
                    wordCount = json.optInt("wordCount", 1).coerceAtLeast(1),
                    hasTranslation = json.optBoolean("hasTranslation", transcribed.isNotBlank()),
                    partOfSpeech = json.optString("partOfSpeech", ""),
                    contextNote = json.optString("contextNote", ""),
                    literalBreakdown = json.optString("literalBreakdown", ""),
                    alternatives = alternatives
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Crop translation timed out", e)
            Result.failure(Exception("El análisis tardó demasiado (35s). Intenta seleccionar un área más pequeña."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Crop translation failed", e)
            Result.failure(Exception(friendlyAiError(e)))
        }
    }

    private fun prepareBitmapForAi(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val softwareBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val width = softwareBitmap.width
        val height = softwareBitmap.height
        if (width <= maxDimension && height <= maxDimension) return softwareBitmap
        val ratio = width.toFloat() / height.toFloat()
        val (newW, newH) = if (width >= height) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(softwareBitmap, newW, newH, true)
    }

    private suspend fun friendlyAiError(e: Exception): String {
        val raw = e.localizedMessage ?: e.toString()
        return when {
            raw.contains("403") || raw.contains("App Check", ignoreCase = true) || raw.contains("attestation", ignoreCase = true) -> {
                val tokenDiag = runCatching {
                    withTimeoutOrNull(5_000L) {
                        val token = FirebaseAppCheck.getInstance().getAppCheckToken(true).await().token
                        if (token.count { it == '.' } == 2) "token JWT emitido" else "token inválido ($token)"
                    } ?: "tiempo agotado obteniendo token"
                }.getOrElse { err ->
                    "fallo proveedor: ${err.localizedMessage ?: err.javaClass.simpleName}"
                }
                "Error App Check (403) [Modo: $activeAppCheckProviderLabel | $tokenDiag]: $raw"
            }
            raw.contains("Unable to resolve host", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
                "Sin conexión o tiempo de espera agotado al contactar el traductor."
            else -> raw
        }
    }

    private companion object {
        const val TAG = "AiTranslationRepo"
        const val FAST_MODEL = "gemini-3.5-flash-lite"
    }
}
