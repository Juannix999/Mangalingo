package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MangaSummary
import com.example.data.model.MangaTag
import com.example.data.repository.MangaCatalogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val query: String = "",
    val selectedLanguage: String? = null, // null = todos los idiomas
    val selectedTagId: String? = null,
    val tags: List<MangaTag> = emptyList(),
    val interactiveShowcase: List<MangaSummary> = emptyList(),
    val catalogMangas: List<MangaSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val errorMessage: String? = null
)

class ExploreViewModel(
    private val catalogRepository: MangaCatalogRepository = MangaCatalogRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExploreUiState(interactiveShowcase = catalogRepository.getInteractiveShowcase())
    )
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadTags()
        loadCatalog(reset = true)
    }

    private fun loadTags() {
        viewModelScope.launch {
            catalogRepository.fetchGenreTags().onSuccess { genres ->
                _uiState.update { it.copy(tags = genres.take(18)) }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400L)
            loadCatalog(reset = true)
        }
    }

    fun onLanguageSelected(langCode: String?) {
        val next = if (_uiState.value.selectedLanguage == langCode) null else langCode
        _uiState.update { it.copy(selectedLanguage = next) }
        loadCatalog(reset = true)
    }

    fun onTagSelected(tagId: String?) {
        val next = if (_uiState.value.selectedTagId == tagId) null else tagId
        _uiState.update { it.copy(selectedTagId = next) }
        loadCatalog(reset = true)
    }

    fun refresh() {
        loadCatalog(reset = true)
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return
        loadCatalog(reset = false)
    }

    private fun loadCatalog(reset: Boolean) {
        viewModelScope.launch {
            val offset = if (reset) 0 else _uiState.value.catalogMangas.size
            _uiState.update {
                if (reset) it.copy(isLoading = true, errorMessage = null)
                else it.copy(isLoadingMore = true, errorMessage = null)
            }
            val state = _uiState.value
            val result = catalogRepository.fetchMangaCatalog(
                query = state.query,
                languageFilter = state.selectedLanguage,
                tagIds = listOfNotNull(state.selectedTagId),
                offset = offset,
                limit = 24
            )
            result.fold(
                onSuccess = { fetched ->
                    _uiState.update { curr ->
                        val merged = if (reset) fetched else (curr.catalogMangas + fetched).distinctBy { it.id }
                        curr.copy(
                            catalogMangas = merged,
                            isLoading = false,
                            isLoadingMore = false,
                            canLoadMore = fetched.size >= 24,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { curr ->
                        curr.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = "No se pudo conectar con el catálogo online (${error.localizedMessage ?: "sin red"}). Puedes seguir practicando con los mangas interactivos."
                        )
                    }
                }
            )
        }
    }
}
