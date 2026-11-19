package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MangaChapter
import com.example.data.model.MangaSummary
import com.example.data.repository.MangaCatalogRepository
import com.example.data.repository.VocabularyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MangaDetailUiState(
    val manga: MangaSummary? = null,
    val chapters: List<MangaChapter> = emptyList(),
    val selectedChapterLang: String? = null,
    val isLoadingManga: Boolean = true,
    val isLoadingChapters: Boolean = true,
    val errorMessage: String? = null,
    val isFavorite: Boolean = false
)

class MangaDetailViewModel(
    private val mangaId: String,
    private val currentUserId: String,
    private val catalogRepository: MangaCatalogRepository,
    private val vocabularyRepository: VocabularyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MangaDetailUiState())
    val uiState: StateFlow<MangaDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
        observeFavoriteStatus()
    }

    private fun observeFavoriteStatus() {
        viewModelScope.launch {
            vocabularyRepository.observeFavoriteMangas(currentUserId).collect { favs ->
                val isFav = favs.any { it.mangaId == mangaId || it.id == mangaId }
                _uiState.update { it.copy(isFavorite = isFav) }
            }
        }
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingManga = true, errorMessage = null) }
            catalogRepository.fetchMangaDetail(mangaId).fold(
                onSuccess = { summary ->
                    _uiState.update { it.copy(manga = summary, isLoadingManga = false) }
                    loadChapters(language = _uiState.value.selectedChapterLang)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingManga = false,
                            isLoadingChapters = false,
                            errorMessage = error.localizedMessage ?: "No se pudo cargar el manga."
                        )
                    }
                }
            )
        }
    }

    fun onChapterLanguageSelected(lang: String?) {
        val next = if (_uiState.value.selectedChapterLang == lang) null else lang
        _uiState.update { it.copy(selectedChapterLang = next) }
        loadChapters(language = next)
    }

    fun loadChapters(language: String? = _uiState.value.selectedChapterLang) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChapters = true, errorMessage = null) }
            catalogRepository.fetchMangaChapters(mangaId, language).fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(chapters = list, isLoadingChapters = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingChapters = false,
                            errorMessage = error.localizedMessage ?: "No se pudieron cargar los capítulos."
                        )
                    }
                }
            )
        }
    }

    fun toggleFavorite() {
        val manga = _uiState.value.manga ?: return
        val currentFav = _uiState.value.isFavorite
        val prefLang = _uiState.value.selectedChapterLang ?: manga.availableLanguages.firstOrNull() ?: "es"
        viewModelScope.launch {
            vocabularyRepository.toggleFavoriteManga(
                userId = currentUserId,
                manga = manga,
                currentlyFavorite = currentFav,
                preferredLang = prefLang
            )
        }
    }
}
