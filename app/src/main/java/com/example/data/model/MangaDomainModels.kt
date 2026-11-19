package com.example.data.model

import androidx.annotation.DrawableRes

data class MangaSummary(
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val author: String,
    val originalLanguage: String,
    val availableLanguages: List<String>,
    val tags: List<String>,
    val status: String,
    val year: Int?,
    val isInteractiveOriginal: Boolean = false,
    @DrawableRes val coverDrawableRes: Int? = null
)

data class MangaTag(
    val id: String,
    val name: String
)

data class MangaChapter(
    val id: String,
    val volume: String?,
    val chapter: String?,
    val title: String,
    val language: String,
    val pageCount: Int,
    val isInteractiveOriginal: Boolean = false
) {
    val displayLabel: String
        get() {
            val ch = chapter?.takeIf { it.isNotBlank() }?.let { "Cap. $it" } ?: "Oneshot"
            val vol = volume?.takeIf { it.isNotBlank() }?.let { "Vol. $it · " } ?: ""
            return "$vol$ch"
        }
}

data class ReaderPage(
    val index: Int,
    val imageUrl: String? = null,
    val fallbackImageUrl: String? = null,
    val interactivePanel: InteractiveMangaPanel? = null
)

data class InteractiveMangaPanel(
    val sceneTitle: String,
    val sceneMood: String,
    val languageCode: String,
    @DrawableRes val artworkResId: Int? = null,
    val bubbles: List<DetectedBubble>
)

data class DetectedBubble(
    val id: String,
    val rawText: String,
    val tokens: List<String>,
    val yMin: Int, // 0..1000 normalized
    val xMin: Int,
    val yMax: Int,
    val xMax: Int,
    val speaker: String = ""
)

data class TranslationAnalysis(
    val sourceText: String,
    val translatedText: String,
    val reading: String,
    val sourceLang: String,
    val targetLang: String,
    val wordCount: Int,
    val hasTranslation: Boolean,
    val partOfSpeech: String,
    val contextNote: String,
    val literalBreakdown: String,
    val alternatives: List<String>
)
