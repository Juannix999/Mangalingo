package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FavoriteManga
import com.example.data.model.SavedTranslation
import com.example.data.model.SearchHistoryItem
import com.example.data.repository.VocabularyRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VocabUiState<out T> {
    data object Loading : VocabUiState<Nothing>
    data class Success<T>(val data: T) : VocabUiState<T>
    data class Error(val message: String) : VocabUiState<Nothing>
}

class VocabularyViewModel(
    private val vocabularyRepository: VocabularyRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val searchHistory: StateFlow<VocabUiState<List<SearchHistoryItem>>> =
        vocabularyRepository.observeSearchHistory(currentUserId)
            .map<List<SearchHistoryItem>, VocabUiState<List<SearchHistoryItem>>> {
                VocabUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing search history", rootError)
                emit(VocabUiState.Error(rootError.message ?: "No se pudo cargar el historial"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = VocabUiState.Loading
            )

    val savedTranslations: StateFlow<VocabUiState<List<SavedTranslation>>> =
        vocabularyRepository.observeSavedTranslations(currentUserId)
            .map<List<SavedTranslation>, VocabUiState<List<SavedTranslation>>> {
                VocabUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing saved translations", rootError)
                emit(VocabUiState.Error(rootError.message ?: "No se pudieron cargar las traducciones guardadas"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = VocabUiState.Loading
            )

    val favoriteMangas: StateFlow<VocabUiState<List<FavoriteManga>>> =
        vocabularyRepository.observeFavoriteMangas(currentUserId)
            .map<List<FavoriteManga>, VocabUiState<List<FavoriteManga>>> {
                VocabUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing favorite mangas", rootError)
                emit(VocabUiState.Error(rootError.message ?: "No se pudo cargar tu biblioteca"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = VocabUiState.Loading
            )

    fun saveFromHistory(item: SearchHistoryItem) {
        viewModelScope.launch {
            vocabularyRepository.saveFromHistoryItem(currentUserId, item)
                .onSuccess { _actionMessage.value = "Traducción guardada en tu glosario" }
                .onFailure { _actionMessage.value = "Error al guardar: ${it.localizedMessage}" }
        }
    }

    fun deleteHistoryItem(historyId: String) {
        viewModelScope.launch {
            vocabularyRepository.deleteHistoryItem(currentUserId, historyId)
                .onFailure { _actionMessage.value = "No se pudo eliminar del historial" }
        }
    }

    fun updateSavedEntry(savedId: String, userNote: String, mastered: Boolean) {
        viewModelScope.launch {
            vocabularyRepository.updateSavedTranslation(currentUserId, savedId, userNote, mastered)
                .onSuccess { _actionMessage.value = "Traducción actualizada" }
                .onFailure { _actionMessage.value = "No se pudo actualizar: ${it.localizedMessage}" }
        }
    }

    fun deleteSavedEntry(savedId: String) {
        viewModelScope.launch {
            vocabularyRepository.deleteSavedTranslation(currentUserId, savedId)
                .onSuccess { _actionMessage.value = "Eliminada de guardadas" }
                .onFailure { _actionMessage.value = "No se pudo eliminar: ${it.localizedMessage}" }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "VocabularyVM"
    }
}
