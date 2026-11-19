package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class SearchHistoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceText: String = "",
    val translatedText: String = "",
    val reading: String = "",
    val sourceLang: String = "ja",
    val targetLang: String = "es",
    val wordCount: Long = 1L,
    val hasTranslation: Boolean = true,
    val partOfSpeech: String = "",
    val contextNote: String = "",
    val mangaId: String = "",
    val mangaTitle: String = "",
    val chapterLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "sourceText" to sourceText.take(300),
        "translatedText" to translatedText.take(600),
        "reading" to reading.take(200),
        "sourceLang" to sourceLang.take(15),
        "targetLang" to targetLang.take(15),
        "wordCount" to wordCount.coerceIn(1L, 50L),
        "hasTranslation" to hasTranslation,
        "partOfSpeech" to partOfSpeech.take(60),
        "contextNote" to contextNote.take(500),
        "mangaId" to mangaId.take(128),
        "mangaTitle" to mangaTitle.take(200),
        "chapterLabel" to chapterLabel.take(60),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class SavedTranslation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceText: String = "",
    val translatedText: String = "",
    val reading: String = "",
    val sourceLang: String = "ja",
    val targetLang: String = "es",
    val wordCount: Long = 1L,
    val hasTranslation: Boolean = true,
    val partOfSpeech: String = "",
    val contextNote: String = "",
    val mangaId: String = "",
    val mangaTitle: String = "",
    val chapterLabel: String = "",
    val userNote: String = "",
    val mastered: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "sourceText" to sourceText.take(300),
        "translatedText" to translatedText.take(600),
        "reading" to reading.take(200),
        "sourceLang" to sourceLang.take(15),
        "targetLang" to targetLang.take(15),
        "wordCount" to wordCount.coerceIn(1L, 50L),
        "hasTranslation" to hasTranslation,
        "partOfSpeech" to partOfSpeech.take(60),
        "contextNote" to contextNote.take(500),
        "mangaId" to mangaId.take(128),
        "mangaTitle" to mangaTitle.take(200),
        "chapterLabel" to chapterLabel.take(60),
        "userNote" to userNote.take(300),
        "mastered" to mastered,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FavoriteManga(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mangaId: String = "",
    val title: String = "",
    val coverUrl: String = "",
    val preferredLang: String = "es",
    val lastChapterId: String = "",
    val lastChapterTitle: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "mangaId" to mangaId.take(128),
        "title" to title.take(200),
        "coverUrl" to coverUrl.take(500),
        "preferredLang" to preferredLang.take(15),
        "lastChapterId" to lastChapterId.take(128),
        "lastChapterTitle" to lastChapterTitle.take(120),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
