package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.SupportedLanguage
import com.example.data.model.DetectedBubble
import com.example.data.model.ReaderPage
import com.example.data.model.TranslationAnalysis
import com.example.data.repository.AiTranslationRepository
import com.example.data.repository.AppSettings
import com.example.data.repository.MangaCatalogRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.VocabularyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TranslationSheetState {
    data object Hidden : TranslationSheetState
    data class Loading(val previewText: String, val wordCount: Int) : TranslationSheetState
    data class Success(
        val analysis: TranslationAnalysis,
        val isSaved: Boolean = false,
        val isSaving: Boolean = false
    ) : TranslationSheetState
    data class Error(val message: String, val previewText: String) : TranslationSheetState
}

data class ReaderUiState(
    val mangaId: String = "",
    val mangaTitle: String = "",
    val coverUrl: String = "",
    val chapterId: String = "",
    val chapterLabel: String = "",
    val sourceLang: String = "ja",
    val targetLang: SupportedLanguage = SupportedLanguage.SPANISH,
    val pages: List<ReaderPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val isLoadingPages: Boolean = true,
    val pagesError: String? = null,
    val scannedBubblesByPage: Map<Int, List<DetectedBubble>> = emptyMap(),
    val scanningPageIndex: Int? = null,
    val scanError: String? = null,
    val autoScanEnabled: Boolean = false,
    val rtlReadingDirection: Boolean = false,
    val showBubbleOutlines: Boolean = true,
    val freeLassoMode: Boolean = false,
    val activeBubble: DetectedBubble? = null,
    val selectedRange: IntRange? = null,
    val lastBubbleContext: String = "",
    val translationSheet: TranslationSheetState = TranslationSheetState.Hidden,
    val feedbackMessage: String? = null
)

class ReaderViewModel(
    private val mangaId: String,
    private val mangaTitle: String,
    private val coverUrl: String,
    private val chapterId: String,
    private val chapterLabel: String,
    private val sourceLang: String,
    private val currentUserId: String,
    private val catalogRepository: MangaCatalogRepository,
    private val aiRepository: AiTranslationRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private var userOverrodeTargetLang = false

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            coverUrl = coverUrl,
            chapterId = chapterId,
            chapterLabel = chapterLabel,
            sourceLang = sourceLang,
            targetLang = resolveTargetLanguage(sourceLang, AppSettings().defaultTargetLang)
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var translationJob: Job? = null
    private var scanJob: Job? = null

    init {
        observeSettings()
        loadPages()
        saveReadingProgress()
    }

    private fun observeSettings() {
        val repo = settingsRepository ?: return
        viewModelScope.launch {
            repo.settingsFlow.collect { settings ->
                _uiState.update { curr ->
                    curr.copy(
                        autoScanEnabled = settings.autoScanEnabled,
                        rtlReadingDirection = settings.rtlReadingDirection,
                        showBubbleOutlines = settings.showBubbleOutlines,
                        targetLang = if (userOverrodeTargetLang) {
                            curr.targetLang
                        } else {
                            resolveTargetLanguage(curr.sourceLang, settings.defaultTargetLang)
                        }
                    )
                }
            }
        }
    }

    private fun resolveTargetLanguage(
        srcLang: String,
        preferred: SupportedLanguage
    ): SupportedLanguage {
        val normalized = srcLang.lowercase()
        return if ((normalized == "es" || normalized == "es-la") && preferred == SupportedLanguage.SPANISH) {
            SupportedLanguage.ENGLISH
        } else {
            preferred
        }
    }

    private fun saveReadingProgress() {
        viewModelScope.launch {
            vocabularyRepository.updateReadingProgress(
                userId = currentUserId,
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                coverUrl = coverUrl,
                preferredLang = sourceLang,
                chapterId = chapterId,
                chapterTitle = chapterLabel
            )
        }
    }

    fun loadPages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPages = true, pagesError = null) }
            catalogRepository.fetchChapterPages(chapterId).fold(
                onSuccess = { loadedPages ->
                    val prePopulated = mutableMapOf<Int, List<DetectedBubble>>()
                    loadedPages.forEach { page ->
                        page.interactivePanel?.let { panel ->
                            prePopulated[page.index] = panel.bubbles
                        }
                    }
                    _uiState.update {
                        it.copy(
                            pages = loadedPages,
                            isLoadingPages = false,
                            scannedBubblesByPage = it.scannedBubblesByPage + prePopulated
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingPages = false,
                            pagesError = error.localizedMessage ?: "No se pudieron cargar las páginas."
                        )
                    }
                }
            )
        }
    }

    fun onPageChanged(index: Int) {
        if (index == _uiState.value.currentPageIndex) return
        _uiState.update {
            it.copy(
                currentPageIndex = index,
                activeBubble = null,
                selectedRange = null,
                scanError = null
            )
        }
    }

    fun toggleAutoScan() {
        _uiState.update { it.copy(autoScanEnabled = !it.autoScanEnabled) }
    }

    fun toggleFreeLassoMode() {
        _uiState.update {
            it.copy(
                freeLassoMode = !it.freeLassoMode,
                activeBubble = null,
                selectedRange = null
            )
        }
    }

    fun onTargetLanguageChanged(newLang: SupportedLanguage) {
        userOverrodeTargetLang = true
        _uiState.update { it.copy(targetLang = newLang) }
        val currentSheet = _uiState.value.translationSheet
        if (currentSheet is TranslationSheetState.Success) {
            val bubble = _uiState.value.activeBubble
            val range = _uiState.value.selectedRange
            if (bubble != null && range != null) {
                translateBubbleRange(bubble, range)
            } else {
                translateRawText(
                    selectedText = currentSheet.analysis.sourceText,
                    bubbleContext = _uiState.value.lastBubbleContext,
                    wordCount = currentSheet.analysis.wordCount
                )
            }
        }
    }

    fun scanPageBitmap(pageIndex: Int, bitmap: Bitmap) {
        if (_uiState.value.scannedBubblesByPage.containsKey(pageIndex)) return
        if (_uiState.value.scanningPageIndex == pageIndex) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(scanningPageIndex = pageIndex, scanError = null) }
            aiRepository.scanMangaPageBubbles(bitmap, _uiState.value.sourceLang).fold(
                onSuccess = { (detectedLang, bubbles) ->
                    _uiState.update { curr ->
                        val updatedMap = curr.scannedBubblesByPage.toMutableMap()
                        updatedMap[pageIndex] = bubbles
                        curr.copy(
                            scanningPageIndex = null,
                            scannedBubblesByPage = updatedMap,
                            sourceLang = detectedLang.ifBlank { curr.sourceLang },
                            feedbackMessage = if (bubbles.isEmpty()) "No se detectaron textos en esta página." else null
                        )
                    }
                },
                onFailure = { err ->
                    Log.e(TAG, "Scan page failed", err)
                    _uiState.update {
                        it.copy(
                            scanningPageIndex = null,
                            scanError = err.localizedMessage ?: "Error al escanear texto con IA."
                        )
                    }
                }
            )
        }
    }

    fun openBubble(bubble: DetectedBubble) {
        _uiState.update {
            it.copy(
                activeBubble = bubble,
                selectedRange = null,
                freeLassoMode = false
            )
        }
    }

    fun dismissBubbleSelector() {
        _uiState.update { it.copy(activeBubble = null, selectedRange = null) }
    }

    fun updateSelectionPreview(range: IntRange) {
        _uiState.update { it.copy(selectedRange = range) }
    }

    fun translateBubbleRange(bubble: DetectedBubble, range: IntRange) {
        val safeStart = range.first.coerceIn(0, bubble.tokens.lastIndex.coerceAtLeast(0))
        val safeEnd = range.last.coerceIn(safeStart, bubble.tokens.lastIndex.coerceAtLeast(0))
        val cleanRange = safeStart..safeEnd
        val selectedTokens = bubble.tokens.slice(cleanRange)
        val joined = joinTokensForLanguage(selectedTokens, _uiState.value.sourceLang)
        _uiState.update {
            it.copy(
                activeBubble = bubble,
                selectedRange = cleanRange,
                lastBubbleContext = bubble.rawText
            )
        }
        translateRawText(
            selectedText = joined,
            bubbleContext = bubble.rawText,
            wordCount = selectedTokens.size
        )
    }

    private fun translateRawText(
        selectedText: String,
        bubbleContext: String,
        wordCount: Int
    ) {
        if (selectedText.isBlank()) return
        lastRetryAction = { translateRawText(selectedText, bubbleContext, wordCount) }
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            val state = _uiState.value
            _uiState.update {
                it.copy(translationSheet = TranslationSheetState.Loading(selectedText, wordCount))
            }
            aiRepository.translateSelection(
                selectedText = selectedText,
                fullBubbleContext = bubbleContext.ifBlank { selectedText },
                sourceLang = state.sourceLang,
                targetLang = state.targetLang.code,
                wordCount = wordCount
            ).fold(
                onSuccess = { analysis ->
                    _uiState.update {
                        it.copy(
                            translationSheet = TranslationSheetState.Success(
                                analysis = analysis,
                                isSaved = false
                            )
                        )
                    }
                    // Automatically log lookup in Firestore Search History
                    vocabularyRepository.recordSearchHistory(
                        userId = currentUserId,
                        analysis = analysis,
                        mangaId = mangaId,
                        mangaTitle = mangaTitle,
                        chapterLabel = chapterLabel
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            translationSheet = TranslationSheetState.Error(
                                message = error.localizedMessage ?: "No se pudo traducir la selección.",
                                previewText = selectedText
                            )
                        )
                    }
                }
            )
        }
    }

    fun translateCroppedBitmap(cropBitmap: Bitmap) {
        lastRetryAction = { translateCroppedBitmap(cropBitmap) }
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            val state = _uiState.value
            _uiState.update {
                it.copy(
                    translationSheet = TranslationSheetState.Loading("Analizando selección...", 1),
                    lastBubbleContext = ""
                )
            }
            aiRepository.translateImageCrop(
                cropBitmap = cropBitmap,
                sourceLangHint = state.sourceLang,
                targetLang = state.targetLang.code
            ).fold(
                onSuccess = { analysis ->
                    _uiState.update {
                        it.copy(
                            translationSheet = TranslationSheetState.Success(
                                analysis = analysis,
                                isSaved = false
                            )
                        )
                    }
                    if (analysis.sourceText.isNotBlank() && analysis.sourceText != "(Región seleccionada)") {
                        vocabularyRepository.recordSearchHistory(
                            userId = currentUserId,
                            analysis = analysis,
                            mangaId = mangaId,
                            mangaTitle = mangaTitle,
                            chapterLabel = chapterLabel
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            translationSheet = TranslationSheetState.Error(
                                message = error.localizedMessage ?: "Error al reconocer el texto seleccionado.",
                                previewText = "Selección en imagen"
                            )
                        )
                    }
                }
            )
        }
    }

    fun saveCurrentTranslation(userNote: String = "") {
        val current = _uiState.value.translationSheet as? TranslationSheetState.Success ?: return
        if (current.isSaved || current.isSaving) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(translationSheet = current.copy(isSaving = true))
            }
            vocabularyRepository.saveTranslation(
                userId = currentUserId,
                analysis = current.analysis,
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                chapterLabel = chapterLabel,
                userNote = userNote
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            translationSheet = current.copy(isSaved = true, isSaving = false),
                            feedbackMessage = "Traducción guardada en tu colección"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            translationSheet = current.copy(isSaved = false, isSaving = false),
                            feedbackMessage = "No se pudo guardar: ${err.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    private var lastRetryAction: (() -> Unit)? = null

    fun retryLastTranslation() {
        lastRetryAction?.invoke()
    }

    fun dismissTranslationSheet() {
        translationJob?.cancel()
        _uiState.update { it.copy(translationSheet = TranslationSheetState.Hidden) }
    }

    fun dismissScanError() {
        _uiState.update { it.copy(scanError = null) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun joinTokensForLanguage(tokens: List<String>, langCode: String): String {
        val code = langCode.lowercase()
        return if (code == "ja" || code == "zh" || code == "zh-hk") {
            tokens.joinToString("")
        } else {
            tokens.joinToString(" ")
        }
    }

    private companion object {
        const val TAG = "ReaderVM"
    }
}
