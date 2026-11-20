package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.core.SupportedLanguage
import com.example.core.switchToDebugTokenProvider
import com.example.data.model.DetectedBubble
import com.example.data.model.InteractiveMangaPanel
import com.example.ui.components.LanguagePairPill
import com.example.ui.viewmodel.ReaderViewModel
import com.example.ui.viewmodel.TranslationSheetState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val loadedBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var showTargetLangMenu by remember { mutableStateOf(false) }
    var debugTokenDialogUuid by remember { mutableStateOf<String?>(null) }
    var copiedTokenNotice by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(state.feedbackMessage) {
        val msg = state.feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { state.pages.size.coerceAtLeast(1) }
    )

    LaunchedEffect(pagerState.currentPage, state.pages.size) {
        if (state.pages.isNotEmpty()) {
            viewModel.onPageChanged(pagerState.currentPage)
        }
    }

    val currentBitmapForAutoScan = loadedBitmaps[state.currentPageIndex]
    val currentPageForAutoScan = state.pages.getOrNull(state.currentPageIndex)
    LaunchedEffect(state.currentPageIndex, currentBitmapForAutoScan, state.autoScanEnabled) {
        if (
            state.autoScanEnabled &&
            currentPageForAutoScan?.imageUrl != null &&
            currentBitmapForAutoScan != null &&
            !state.scannedBubblesByPage.containsKey(state.currentPageIndex) &&
            state.scanningPageIndex != state.currentPageIndex
        ) {
            viewModel.scanPageBitmap(state.currentPageIndex, currentBitmapForAutoScan)
        }
    }

    Scaffold(
        containerColor = Color(0xFF0A0B0F),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.mangaTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val pageIndicator = if (state.pages.isNotEmpty()) {
                            "${state.chapterLabel} · Pág. ${state.currentPageIndex + 1}/${state.pages.size}"
                        } else {
                            state.chapterLabel
                        }
                        Text(
                            text = pageIndicator,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier
                                .clickable { showTargetLangMenu = true }
                                .testTag("target_language_picker")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = "Idioma de traducción",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${SupportedLanguage.displayBadge(state.sourceLang)} → ${state.targetLang.shortBadge}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showTargetLangMenu,
                            onDismissRequest = { showTargetLangMenu = false }
                        ) {
                            SupportedLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Text("Traducir a ${lang.spanishName} (${lang.nativeName})")
                                    },
                                    onClick = {
                                        showTargetLangMenu = false
                                        viewModel.onTargetLanguageChanged(lang)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            val currentPage = state.pages.getOrNull(state.currentPageIndex)
            val hasScanned = state.scannedBubblesByPage.containsKey(state.currentPageIndex)
            val isScanning = state.scanningPageIndex == state.currentPageIndex

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    val activeBubble = state.activeBubble
                    if (activeBubble != null) {
                        BubbleWordSelectionBar(
                            bubble = activeBubble,
                            selectedRange = state.selectedRange,
                            onTranslateRange = { range -> viewModel.translateBubbleRange(activeBubble, range) },
                            onDismiss = viewModel::dismissBubbleSelector
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                                    pagerState.animateScrollToPage(prev)
                                }
                            },
                            enabled = pagerState.currentPage > 0
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Página anterior"
                            )
                        }

                        if (currentPage?.imageUrl != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val bmp = loadedBitmaps[state.currentPageIndex]
                                        if (bmp != null) {
                                            viewModel.scanPageBitmap(state.currentPageIndex, bmp)
                                        }
                                    },
                                    enabled = !isScanning && loadedBitmaps[state.currentPageIndex] != null,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("scan_page_button")
                                ) {
                                    if (isScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.DocumentScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            isScanning -> "Escaneando..."
                                            hasScanned -> "Escaneado"
                                            else -> "Detectar textos"
                                        },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                OutlinedButton(
                                    onClick = viewModel::toggleFreeLassoMode,
                                    colors = if (state.freeLassoMode) {
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("free_lasso_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CropFree,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (state.freeLassoMode) "Arrastrar activo" else "Recorte",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Mantén pulsada 1 palabra o desliza sobre 2+ para traducir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    val next = (pagerState.currentPage + 1)
                                        .coerceAtMost(state.pages.lastIndex.coerceAtLeast(0))
                                    pagerState.animateScrollToPage(next)
                                }
                            },
                            enabled = pagerState.currentPage < state.pages.lastIndex
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Página siguiente"
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoadingPages -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Cargando páginas del capítulo...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                state.pagesError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.pagesError.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = viewModel::loadPages) {
                                Text("Reintentar")
                            }
                        }
                    }
                }

                state.pages.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Este capítulo no tiene páginas disponibles.")
                    }
                }

                else -> {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !state.freeLassoMode,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIdx ->
                        val page = state.pages[pageIdx]
                        if (page.interactivePanel != null) {
                            InteractiveMangaPanelPage(
                                panel = page.interactivePanel,
                                activeBubble = state.activeBubble,
                                selectedRange = state.selectedRange,
                                onTranslateRange = { bubble, range ->
                                    viewModel.translateBubbleRange(bubble, range)
                                }
                            )
                        } else if (page.imageUrl != null) {
                            val bitmap = loadedBitmaps[pageIdx]
                            val bubbles = state.scannedBubblesByPage[pageIdx].orEmpty()

                            ImageMangaPageWithOverlays(
                                pageUrl = page.imageUrl,
                                fallbackUrl = page.fallbackImageUrl,
                                cachedBitmap = bitmap,
                                onBitmapLoaded = { loadedBmp ->
                                    loadedBitmaps[pageIdx] = loadedBmp
                                },
                                bubbles = bubbles,
                                activeBubbleId = state.activeBubble?.id,
                                freeLassoMode = state.freeLassoMode,
                                isScanningThisPage = state.scanningPageIndex == pageIdx,
                                onExitFreeLasso = viewModel::toggleFreeLassoMode,
                                onBubbleTapOrHold = { bubble ->
                                    viewModel.openBubble(bubble)
                                },
                                onCropRegionSelected = { cropBmp ->
                                    viewModel.translateCroppedBitmap(cropBmp)
                                }
                            )
                        }
                    }
                }
            }

            if (state.scanError != null) {
                val errText = state.scanError.orEmpty()
                val isAppCheckError = errText.contains("App Check", ignoreCase = true) || errText.contains("403")
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = errText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(
                                onClick = viewModel::dismissScanError,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cerrar aviso",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (isAppCheckError) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        copiedTokenNotice = false
                                        debugTokenDialogUuid = switchToDebugTokenProvider(context)
                                    }
                                ) {
                                    Text(
                                        text = "Token Debug",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        viewModel.dismissScanError()
                                        loadedBitmaps[state.currentPageIndex]?.let { bmp ->
                                            viewModel.scanPageBitmap(state.currentPageIndex, bmp)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Reintentar",
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val tokenUuid = debugTokenDialogUuid
            if (tokenUuid != null) {
                AlertDialog(
                    onDismissRequest = { debugTokenDialogUuid = null },
                    title = { Text("Token Debug de App Check") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Si Firebase rechaza reCAPTCHA en tu teléfono, copia este token y agrégalo en Firebase Console → App Check → Apps → MangaLingo → Administrar tokens de depuración:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = tokenUuid,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            if (copiedTokenNotice) {
                                Text(
                                    text = "Token copiado al portapapeles.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(tokenUuid))
                                copiedTokenNotice = true
                            }
                        ) {
                            Text("Copiar token")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { debugTokenDialogUuid = null }) {
                            Text("Cerrar")
                        }
                    }
                )
            }

            if (state.translationSheet !is TranslationSheetState.Hidden) {
                TranslationResultSheet(
                    sheetState = state.translationSheet,
                    onSaveClick = { viewModel.saveCurrentTranslation() },
                    onRetryClick = viewModel::retryLastTranslation,
                    onDismiss = viewModel::dismissTranslationSheet,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ImageMangaPageWithOverlays(
    pageUrl: String,
    fallbackUrl: String?,
    cachedBitmap: Bitmap?,
    onBitmapLoaded: (Bitmap) -> Unit,
    bubbles: List<DetectedBubble>,
    activeBubbleId: String?,
    freeLassoMode: Boolean,
    isScanningThisPage: Boolean,
    onExitFreeLasso: () -> Unit,
    onBubbleTapOrHold: (DetectedBubble) -> Unit,
    onCropRegionSelected: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var useFallback by remember(pageUrl) { mutableStateOf(false) }
    var retryCounter by remember(pageUrl) { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    val currentBitmap by rememberUpdatedState(cachedBitmap)
    val currentOnCrop by rememberUpdatedState(onCropRegionSelected)

    val activeUrl = if (useFallback && !fallbackUrl.isNullOrBlank()) fallbackUrl else pageUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0F))
            .onSizeChanged { containerSize = it }
            .then(
                if (cachedBitmap != null) {
                    if (freeLassoMode) {
                        Modifier.pointerInput(cachedBitmap, containerSize) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    dragStart = start
                                    dragEnd = start
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    dragEnd = change.position
                                },
                                onDragEnd = {
                                    val s = dragStart
                                    val e = dragEnd
                                    dragStart = null
                                    dragEnd = null
                                    val bmp = currentBitmap
                                    if (bmp != null && s != null && e != null) {
                                        cropBitmapFromUiRectOrPoint(bmp, containerSize, s, e)?.let(currentOnCrop)
                                    }
                                },
                                onDragCancel = {
                                    dragStart = null
                                    dragEnd = null
                                }
                            )
                        }
                    } else {
                        // Allow HorizontalPager to swipe sideways freely; only activate crop if finger holds still for >= 500ms
                        Modifier.pointerInput(cachedBitmap, containerSize) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val longPressMs = max(viewConfiguration.longPressTimeoutMillis, 500L)
                                val touchSlop = viewConfiguration.touchSlop
                                val deadline = System.currentTimeMillis() + longPressMs

                                var isCropActive = false
                                var pointerUp = false
                                var movedEarly = false

                                while (!isCropActive && !pointerUp && !movedEarly) {
                                    val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
                                    val event = withTimeoutOrNull(remaining) {
                                        awaitPointerEvent()
                                    }
                                    if (event == null) {
                                        isCropActive = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragStart = down.position
                                        dragEnd = down.position
                                        break
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) {
                                        pointerUp = true
                                        break
                                    }
                                    if ((change.position - down.position).getDistance() > touchSlop) {
                                        // User is swiping sideways to change page -> do NOT consume, yield to HorizontalPager!
                                        movedEarly = true
                                        break
                                    }
                                }

                                if (isCropActive) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                        change.consume()
                                        dragEnd = change.position
                                    }
                                    val s = dragStart
                                    val e = dragEnd
                                    dragStart = null
                                    dragEnd = null
                                    val bmp = currentBitmap
                                    if (bmp != null && s != null && e != null) {
                                        cropBitmapFromUiRectOrPoint(bmp, containerSize, s, e)?.let(currentOnCrop)
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(activeUrl)
                .allowHardware(false)
                .crossfade(true)
                .setParameter("retry", retryCounter)
                .build(),
            contentDescription = "Página del manga",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { successState ->
                onBitmapLoaded(successState.result.drawable.toBitmap())
            },
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Cargando imagen...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            error = { errState ->
                if (!useFallback && !fallbackUrl.isNullOrBlank()) {
                    LaunchedEffect(errState) {
                        useFallback = true
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.BrokenImage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No se pudo cargar la imagen de esta página.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    useFallback = false
                                    retryCounter++
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reintentar imagen")
                            }
                        }
                    }
                }
            }
        )

        val bitmap = cachedBitmap
        if (bitmap != null) {
            val fitted = computeFittedRect(bitmap.width, bitmap.height, containerSize)

            if (!freeLassoMode && fitted.width > 0f && fitted.height > 0f) {
                val density = LocalDensity.current
                bubbles.forEach { bubble ->
                    val leftPx = fitted.left + (bubble.xMin / 1000f) * fitted.width
                    val topPx = fitted.top + (bubble.yMin / 1000f) * fitted.height
                    val widthPx = ((bubble.xMax - bubble.xMin).coerceAtLeast(40) / 1000f) * fitted.width
                    val heightPx = ((bubble.yMax - bubble.yMin).coerceAtLeast(30) / 1000f) * fitted.height

                    val isActive = bubble.id == activeBubbleId
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                            .size(
                                width = with(density) { widthPx.toDp() },
                                height = with(density) { heightPx.toDp() }
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                            )
                            .border(
                                width = if (isActive) 2.dp else 1.5.dp,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .pointerInput(bubble) {
                                detectTapGestures(
                                    onTap = { onBubbleTapOrHold(bubble) },
                                    onLongPress = { onBubbleTapOrHold(bubble) }
                                )
                            }
                    )
                }
            }

            val s = dragStart
            val e = dragEnd
            if (s != null && e != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dx = abs(e.x - s.x)
                    val dy = abs(e.y - s.y)
                    if (dx < 24f && dy < 24f) {
                        val halfW = (fitted.width * 0.14f).coerceAtLeast(60f)
                        val halfH = (fitted.height * 0.08f).coerceAtLeast(44f)
                        val topLeft = Offset(s.x - halfW, s.y - halfH)
                        val boxSize = Size(halfW * 2f, halfH * 2f)
                        drawRect(color = Color(0x44E63946), topLeft = topLeft, size = boxSize)
                        drawRect(color = Color(0xFFE63946), topLeft = topLeft, size = boxSize, style = Stroke(width = 4f))
                    } else {
                        val left = min(s.x, e.x)
                        val top = min(s.y, e.y)
                        val w = max(s.x, e.x) - left
                        val h = max(s.y, e.y) - top
                        drawRect(color = Color(0x44E63946), topLeft = Offset(left, top), size = Size(w, h))
                        drawRect(color = Color(0xFFE63946), topLeft = Offset(left, top), size = Size(w, h), style = Stroke(width = 4f))
                    }
                }
            }

            if (isScanningThisPage) {
                Surface(
                    color = Color.Black.copy(alpha = 0.84f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Detectando globos de diálogo...",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            } else if (freeLassoMode && s == null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.84f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .clickable(onClick = onExitFreeLasso)
                ) {
                    Text(
                        text = "Arrastra sobre el texto · Toca aquí para volver a deslizar páginas",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            } else if (!freeLassoMode && bubbles.isEmpty() && s == null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = "Desliza ↔ para cambiar página · Mantén pulsado sobre texto para traducir",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE2E6F0),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveMangaPanelPage(
    panel: InteractiveMangaPanel,
    activeBubble: DetectedBubble?,
    selectedRange: IntRange?,
    onTranslateRange: (DetectedBubble, IntRange) -> Unit
) {
    val baseArtwork = panel.artworkResId ?: R.drawable.img_scene_shibuya
    val panelAlignments = listOf(
        Alignment.TopCenter,
        Alignment.Center,
        Alignment.BottomCenter
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = panel.sceneMood,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = panel.sceneTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = SupportedLanguage.displayBadge(panel.languageCode),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Each speech dialogue is rendered inside its own illustrated manga panel frame
        panel.bubbles.forEachIndexed { index, bubble ->
            val isThisBubbleActive = activeBubble?.id == bubble.id
            val currentRange = if (isThisBubbleActive) selectedRange else null
            val panelAlign = panelAlignments[index % panelAlignments.size]
            val bubbleOnRight = index % 2 == 1

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 230.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0E1018))
                    .border(3.dp, Color(0xFFF5F2EB), RoundedCornerShape(14.dp))
            ) {
                // Full manga panel illustration background
                Image(
                    painter = painterResource(id = baseArtwork),
                    contentDescription = "${panel.sceneTitle} - ${bubble.speaker}",
                    contentScale = ContentScale.Crop,
                    alignment = panelAlign,
                    modifier = Modifier.matchParentSize()
                )

                // Subtle comic vignette so speech bubble text stands out clearly over artwork
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.12f),
                                    Color.Black.copy(alpha = 0.55f)
                                )
                            )
                        )
                )

                // Comic speech bubble overlaid directly on the manga scene
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = if (bubbleOnRight) Alignment.End else Alignment.Start
                ) {
                    if (bubble.speaker.isNotBlank()) {
                        Surface(
                            color = Color(0xFF161822).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                            modifier = Modifier.border(1.5.dp, Color(0xFFF5F2EB), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                        ) {
                            Text(
                                text = bubble.speaker.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(52.dp))

                    Surface(
                        color = Color(0xFFFAF8F4).copy(alpha = 0.96f),
                        shape = RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 22.dp,
                            bottomStart = if (bubbleOnRight) 22.dp else 4.dp,
                            bottomEnd = if (bubbleOnRight) 4.dp else 22.dp
                        ),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .border(
                                width = 2.5.dp,
                                color = Color(0xFF161822),
                                shape = RoundedCornerShape(
                                    topStart = 22.dp,
                                    topEnd = 22.dp,
                                    bottomStart = if (bubbleOnRight) 22.dp else 4.dp,
                                    bottomEnd = if (bubbleOnRight) 4.dp else 22.dp
                                )
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            SelectableTokenFlowRow(
                                tokens = bubble.tokens,
                                selectedRange = currentRange,
                                isDarkPaper = false,
                                onCommitRange = { range -> onTranslateRange(bubble, range) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Toca o mantén 1 palabra, o desliza 2+ palabras",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF5D647A)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
private fun BubbleWordSelectionBar(
    bubble: DetectedBubble,
    selectedRange: IntRange?,
    onTranslateRange: (IntRange) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mantén pulsada 1 palabra o arrastra sobre 2+ palabras",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cerrar selector",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            SelectableTokenFlowRow(
                tokens = bubble.tokens,
                selectedRange = selectedRange,
                isDarkPaper = true,
                onCommitRange = onTranslateRange
            )

            if (selectedRange != null && bubble.tokens.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedRange.first > 0) {
                        AssistChip(
                            onClick = {
                                onTranslateRange((selectedRange.first - 1)..selectedRange.last)
                            },
                            label = { Text("+ Anterior") }
                        )
                    }
                    if (selectedRange.last < bubble.tokens.lastIndex) {
                        AssistChip(
                            onClick = {
                                onTranslateRange(selectedRange.first..(selectedRange.last + 1))
                            },
                            label = { Text("+ Siguiente") }
                        )
                    }
                    if (selectedRange.last > selectedRange.first) {
                        AssistChip(
                            onClick = {
                                onTranslateRange(selectedRange.first..selectedRange.first)
                            },
                            label = { Text("Solo 1 palabra") }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectableTokenFlowRow(
    tokens: List<String>,
    selectedRange: IntRange?,
    isDarkPaper: Boolean,
    onCommitRange: (IntRange) -> Unit
) {
    val tokenRects = remember(tokens) { mutableMapOf<Int, Rect>() }
    var activeDragRange by remember(tokens) { mutableStateOf<IntRange?>(null) }
    val currentSelectedRange by rememberUpdatedState(selectedRange)
    val currentOnCommitRange by rememberUpdatedState(onCommitRange)
    val haptics = LocalHapticFeedback.current

    fun findTokenAt(pos: Offset): Int? {
        tokenRects.entries.forEach { (idx, rect) ->
            if (rect.contains(pos)) return idx
        }
        return tokenRects.minByOrNull { (_, rect) ->
            val cx = (rect.left + rect.right) / 2f
            val cy = (rect.top + rect.bottom) / 2f
            val dx = pos.x - cx
            val dy = pos.y - cy
            dx * dx + dy * dy
        }?.key
    }

    val displayedRange = activeDragRange ?: selectedRange

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            // Keyed ONLY on tokens so state updates never cancel an active touch/drag gesture
            .pointerInput(tokens) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startIdx = findTokenAt(down.position) ?: return@awaitEachGesture
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val touchSlop = viewConfiguration.touchSlop
                    val deadline = System.currentTimeMillis() + longPressTimeout

                    var isLongPressActive = false
                    var pointerUp = false
                    var movedBeyondSlop = false

                    // Phase 1: Wait up to longPressTimeout (even when finger is completely still!)
                    while (!isLongPressActive && !pointerUp && !movedBeyondSlop) {
                        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
                        val event = withTimeoutOrNull(remaining) {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            // Timeout expired while finger stayed down & still -> trigger long press!
                            isLongPressActive = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            activeDragRange = startIdx..startIdx
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            pointerUp = true
                            break
                        }
                        if ((change.position - down.position).getDistance() > touchSlop) {
                            movedBeyondSlop = true
                            break
                        }
                    }

                    // Phase 2: While long-press is active, track sliding across 2+ words until lift
                    if (isLongPressActive) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                            val currentIdx = findTokenAt(change.position) ?: startIdx
                            val lo = min(startIdx, currentIdx)
                            val hi = max(startIdx, currentIdx)
                            val nextRange = lo..hi
                            if (nextRange != activeDragRange) {
                                activeDragRange = nextRange
                            }
                        }
                        val finalRange = activeDragRange ?: (startIdx..startIdx)
                        activeDragRange = null
                        currentOnCommitRange(finalRange)
                    } else if (pointerUp && !movedBeyondSlop) {
                        // Simple tap: if another range is already selected, extend/join 2+ words; otherwise translate tapped word
                        val prev = currentSelectedRange
                        if (prev != null) {
                            val lo = min(prev.first, startIdx)
                            val hi = max(prev.last, startIdx)
                            currentOnCommitRange(lo..hi)
                        } else {
                            currentOnCommitRange(startIdx..startIdx)
                        }
                    }
                }
            }
    ) {
        tokens.forEachIndexed { idx, token ->
            val isSelected = displayedRange != null && idx in displayedRange
            val chipBg = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isDarkPaper -> MaterialTheme.colorScheme.surface
                else -> Color(0xFFECE8DF)
            }
            val chipText = when {
                isSelected -> Color.White
                isDarkPaper -> MaterialTheme.colorScheme.onSurface
                else -> Color(0xFF161822)
            }

            Box(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInParent()
                        tokenRects[idx] = Rect(pos, coords.size.toSize())
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(chipBg)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("word_token_$idx")
            ) {
                Text(
                    text = token,
                    style = MaterialTheme.typography.titleMedium,
                    color = chipText
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslationResultSheet(
    sheetState: TranslationSheetState,
    onSaveClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 10.dp,
        shadowElevation = 14.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .testTag("translation_result_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (sheetState) {
                TranslationSheetState.Hidden -> Unit

                is TranslationSheetState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Traduciendo ${if (sheetState.wordCount > 1) "${sheetState.wordCount} palabras" else "palabra"}...",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = sheetState.previewText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Cerrar")
                        }
                    }
                }

                is TranslationSheetState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No se pudo traducir",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Cerrar")
                        }
                    }
                    Text(
                        text = sheetState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRetryClick,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reintentar")
                    }
                }

                is TranslationSheetState.Success -> {
                    val analysis = sheetState.analysis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LanguagePairPill(
                                sourceLang = analysis.sourceLang,
                                targetLang = analysis.targetLang
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (analysis.wordCount > 1) "${analysis.wordCount} palabras" else "1 palabra",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Cerrar")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = analysis.sourceText,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (analysis.reading.isNotBlank()) {
                        Text(
                            text = analysis.reading,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = if (analysis.hasTranslation) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (!analysis.hasTranslation) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sin frase idiomática directa — desglose:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = analysis.translatedText,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (analysis.partOfSpeech.isNotBlank() || analysis.contextNote.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (analysis.partOfSpeech.isNotBlank()) {
                            Text(
                                text = analysis.partOfSpeech,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        if (analysis.contextNote.isNotBlank()) {
                            Text(
                                text = analysis.contextNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (analysis.literalBreakdown.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Desglose: ${analysis.literalBreakdown}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (analysis.alternatives.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            analysis.alternatives.forEach { alt ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = alt,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Guardado en historial",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onSaveClick,
                            enabled = !sheetState.isSaved && !sheetState.isSaving,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sheetState.isSaved) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (sheetState.isSaved) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    Color.White
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("save_translation_button")
                        ) {
                            if (sheetState.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = if (sheetState.isSaved) Icons.Rounded.CheckCircle else Icons.Rounded.BookmarkBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (sheetState.isSaved) "Guardada" else "Guardar traducción",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun computeFittedRect(bmpW: Int, bmpH: Int, container: IntSize): Rect {
    if (bmpW <= 0 || bmpH <= 0 || container.width <= 0 || container.height <= 0) {
        return Rect.Zero
    }
    val cw = container.width.toFloat()
    val ch = container.height.toFloat()
    val scale = min(cw / bmpW.toFloat(), ch / bmpH.toFloat())
    val drawnW = bmpW * scale
    val drawnH = bmpH * scale
    val left = (cw - drawnW) / 2f
    val top = (ch - drawnH) / 2f
    return Rect(left, top, left + drawnW, top + drawnH)
}

private fun cropBitmapFromUiRectOrPoint(
    bitmap: Bitmap,
    container: IntSize,
    start: Offset,
    end: Offset
): Bitmap? {
    val fitted = computeFittedRect(bitmap.width, bitmap.height, container)
    if (fitted.width <= 0f || fitted.height <= 0f) return null

    val dx = abs(end.x - start.x)
    val dy = abs(end.y - start.y)

    // If user simply long-pressed a single word without dragging, crop a smart local bubble patch around their finger
    val (uiLeft, uiTop, uiRight, uiBottom) = if (dx < 24f && dy < 24f) {
        val halfW = (fitted.width * 0.14f).coerceAtLeast(60f)
        val halfH = (fitted.height * 0.08f).coerceAtLeast(44f)
        listOf(
            (start.x - halfW).coerceIn(fitted.left, fitted.right),
            (start.y - halfH).coerceIn(fitted.top, fitted.bottom),
            (start.x + halfW).coerceIn(fitted.left, fitted.right),
            (start.y + halfH).coerceIn(fitted.top, fitted.bottom)
        )
    } else {
        listOf(
            min(start.x, end.x).coerceIn(fitted.left, fitted.right),
            min(start.y, end.y).coerceIn(fitted.top, fitted.bottom),
            max(start.x, end.x).coerceIn(fitted.left, fitted.right),
            max(start.y, end.y).coerceIn(fitted.top, fitted.bottom)
        )
    }

    if ((uiRight - uiLeft) < 12f || (uiBottom - uiTop) < 12f) return null

    val scaleX = bitmap.width.toFloat() / fitted.width
    val scaleY = bitmap.height.toFloat() / fitted.height

    val cropX = ((uiLeft - fitted.left) * scaleX).toInt().coerceIn(0, bitmap.width - 2)
    val cropY = ((uiTop - fitted.top) * scaleY).toInt().coerceIn(0, bitmap.height - 2)
    val cropW = ((uiRight - uiLeft) * scaleX).toInt().coerceIn(2, bitmap.width - cropX)
    val cropH = ((uiBottom - uiTop) * scaleY).toInt().coerceIn(2, bitmap.height - cropY)

    return Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
}
