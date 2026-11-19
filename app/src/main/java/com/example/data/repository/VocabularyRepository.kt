package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FavoriteManga
import com.example.data.model.MangaSummary
import com.example.data.model.SavedTranslation
import com.example.data.model.SearchHistoryItem
import com.example.data.model.TranslationAnalysis
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VocabularyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireVerifiedUserId(expectedUid: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para guardar tus datos.")
        check(uid == expectedUid) { "La sesión cambió; operación cancelada." }
        return uid
    }

    fun observeSearchHistory(userId: String): Flow<List<SearchHistoryItem>> =
        db.collection("users").document(userId).collection("search_history")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(150)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SearchHistoryItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeSavedTranslations(userId: String): Flow<List<SavedTranslation>> =
        db.collection("users").document(userId).collection("saved_translations")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(200)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SavedTranslation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeFavoriteMangas(userId: String): Flow<List<FavoriteManga>> =
        db.collection("users").document(userId).collection("favorite_mangas")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FavoriteManga::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun recordSearchHistory(
        userId: String,
        analysis: TranslationAnalysis,
        mangaId: String,
        mangaTitle: String,
        chapterLabel: String
    ): Result<String> {
        val cleanSource = analysis.sourceText.trim().take(300)
        if (cleanSource.isEmpty()) return Result.failure(IllegalArgumentException("Texto vacío"))
        val colRef = db.collection("users").document(userId).collection("search_history")
        val docRef = colRef.document()
        return try {
            val uid = requireVerifiedUserId(userId)
            val model = SearchHistoryItem(
                userId = uid,
                sourceText = cleanSource,
                translatedText = analysis.translatedText.trim().ifBlank { "-" }.take(600),
                reading = analysis.reading.trim().take(200),
                sourceLang = analysis.sourceLang.trim().ifBlank { "ja" }.take(15),
                targetLang = analysis.targetLang.trim().ifBlank { "es" }.take(15),
                wordCount = analysis.wordCount.toLong().coerceIn(1L, 50L),
                hasTranslation = analysis.hasTranslation,
                partOfSpeech = analysis.partOfSpeech.trim().take(60),
                contextNote = analysis.contextNote.trim().take(500),
                mangaId = mangaId.trim().take(128),
                mangaTitle = mangaTitle.trim().take(200),
                chapterLabel = chapterLabel.trim().take(60)
            )
            docRef.set(model.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteHistoryItem(userId: String, historyId: String): Result<Unit> {
        val docPath = "users/$userId/search_history/$historyId"
        return try {
            val uid = requireVerifiedUserId(userId)
            require(historyId.isNotBlank()) { "ID inválido" }
            db.collection("users").document(uid)
                .collection("search_history").document(historyId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveTranslation(
        userId: String,
        analysis: TranslationAnalysis,
        mangaId: String,
        mangaTitle: String,
        chapterLabel: String,
        userNote: String = ""
    ): Result<String> {
        val cleanSource = analysis.sourceText.trim().take(300)
        if (cleanSource.isEmpty()) return Result.failure(IllegalArgumentException("Texto vacío"))
        val docRef = db.collection("users").document(userId).collection("saved_translations").document()
        return try {
            val uid = requireVerifiedUserId(userId)
            val model = SavedTranslation(
                userId = uid,
                sourceText = cleanSource,
                translatedText = analysis.translatedText.trim().ifBlank { "-" }.take(600),
                reading = analysis.reading.trim().take(200),
                sourceLang = analysis.sourceLang.trim().ifBlank { "ja" }.take(15),
                targetLang = analysis.targetLang.trim().ifBlank { "es" }.take(15),
                wordCount = analysis.wordCount.toLong().coerceIn(1L, 50L),
                hasTranslation = analysis.hasTranslation,
                partOfSpeech = analysis.partOfSpeech.trim().take(60),
                contextNote = analysis.contextNote.trim().take(500),
                mangaId = mangaId.trim().take(128),
                mangaTitle = mangaTitle.trim().take(200),
                chapterLabel = chapterLabel.trim().take(60),
                userNote = userNote.trim().take(300),
                mastered = false
            )
            docRef.set(model.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveFromHistoryItem(userId: String, item: SearchHistoryItem): Result<String> {
        return saveTranslation(
            userId = userId,
            analysis = TranslationAnalysis(
                sourceText = item.sourceText,
                translatedText = item.translatedText,
                reading = item.reading,
                sourceLang = item.sourceLang,
                targetLang = item.targetLang,
                wordCount = item.wordCount.toInt(),
                hasTranslation = item.hasTranslation,
                partOfSpeech = item.partOfSpeech,
                contextNote = item.contextNote,
                literalBreakdown = "",
                alternatives = emptyList()
            ),
            mangaId = item.mangaId,
            mangaTitle = item.mangaTitle,
            chapterLabel = item.chapterLabel
        )
    }

    suspend fun updateSavedTranslation(
        userId: String,
        savedId: String,
        userNote: String,
        mastered: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/saved_translations/$savedId"
        return try {
            val uid = requireVerifiedUserId(userId)
            require(savedId.isNotBlank()) { "ID inválido" }
            db.collection("users").document(uid)
                .collection("saved_translations").document(savedId)
                .update(
                    mapOf(
                        "userNote" to userNote.trim().take(300),
                        "mastered" to mastered,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedTranslation(userId: String, savedId: String): Result<Unit> {
        val docPath = "users/$userId/saved_translations/$savedId"
        return try {
            val uid = requireVerifiedUserId(userId)
            require(savedId.isNotBlank()) { "ID inválido" }
            db.collection("users").document(uid)
                .collection("saved_translations").document(savedId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleFavoriteManga(
        userId: String,
        manga: MangaSummary,
        currentlyFavorite: Boolean,
        preferredLang: String
    ): Result<Unit> {
        val safeDocId = sanitizeDocId(manga.id)
        val docPath = "users/$userId/favorite_mangas/$safeDocId"
        return try {
            val uid = requireVerifiedUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("favorite_mangas").document(safeDocId)
            if (currentlyFavorite) {
                docRef.delete().await()
            } else {
                val model = FavoriteManga(
                    userId = uid,
                    mangaId = safeDocId,
                    title = manga.title.trim().ifBlank { "Manga" }.take(200),
                    coverUrl = manga.coverUrl.trim().take(500),
                    preferredLang = preferredLang.trim().ifBlank { "es" }.take(15),
                    lastChapterId = "",
                    lastChapterTitle = ""
                )
                docRef.set(model.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateReadingProgress(
        userId: String,
        mangaId: String,
        mangaTitle: String,
        coverUrl: String,
        preferredLang: String,
        chapterId: String,
        chapterTitle: String
    ): Result<Unit> {
        val safeDocId = sanitizeDocId(mangaId)
        val docPath = "users/$userId/favorite_mangas/$safeDocId"
        return try {
            val uid = requireVerifiedUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("favorite_mangas").document(safeDocId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "title" to mangaTitle.trim().ifBlank { "Manga" }.take(200),
                        "coverUrl" to coverUrl.trim().take(500),
                        "preferredLang" to preferredLang.trim().ifBlank { "es" }.take(15),
                        "lastChapterId" to chapterId.trim().take(128),
                        "lastChapterTitle" to chapterTitle.trim().take(120),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val model = FavoriteManga(
                    userId = uid,
                    mangaId = safeDocId,
                    title = mangaTitle.trim().ifBlank { "Manga" }.take(200),
                    coverUrl = coverUrl.trim().take(500),
                    preferredLang = preferredLang.trim().ifBlank { "es" }.take(15),
                    lastChapterId = chapterId.trim().take(128),
                    lastChapterTitle = chapterTitle.trim().take(120)
                )
                docRef.set(model.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        return cleaned.ifBlank { "manga" }
    }
}
