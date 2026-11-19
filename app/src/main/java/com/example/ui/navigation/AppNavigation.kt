package com.example.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.repository.AiTranslationRepository
import com.example.data.repository.MangaCatalogRepository
import com.example.data.repository.VocabularyRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MangaDetailScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.viewmodel.ExploreViewModel
import com.example.ui.viewmodel.MangaDetailViewModel
import com.example.ui.viewmodel.ReaderViewModel
import com.example.ui.viewmodel.VocabularyViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object AppRoutes {
    const val ROUTE_EXPLORE = "explore"
    const val ROUTE_LIBRARY = "library"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_SAVED = "saved"
    const val ROUTE_DETAIL = "detail/{mangaId}"
    const val ROUTE_READER =
        "reader?mangaId={mangaId}&mangaTitle={mangaTitle}&coverUrl={coverUrl}&chapterId={chapterId}&chapterLabel={chapterLabel}&sourceLang={sourceLang}"

    fun detailRoute(mangaId: String): String = "detail/${Uri.encode(mangaId)}"

    fun readerRoute(
        mangaId: String,
        mangaTitle: String,
        coverUrl: String,
        chapterId: String,
        chapterLabel: String,
        sourceLang: String
    ): String {
        return "reader" +
            "?mangaId=${Uri.encode(mangaId)}" +
            "&mangaTitle=${Uri.encode(mangaTitle)}" +
            "&coverUrl=${Uri.encode(coverUrl)}" +
            "&chapterId=${Uri.encode(chapterId)}" +
            "&chapterLabel=${Uri.encode(chapterLabel)}" +
            "&sourceLang=${Uri.encode(sourceLang)}"
    }
}

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopTabs = listOf(
    TopLevelTab(
        route = AppRoutes.ROUTE_EXPLORE,
        label = "Explorar",
        selectedIcon = Icons.Rounded.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        testTag = "tab_explore"
    ),
    TopLevelTab(
        route = AppRoutes.ROUTE_LIBRARY,
        label = "Biblioteca",
        selectedIcon = Icons.Rounded.MenuBook,
        unselectedIcon = Icons.Outlined.MenuBook,
        testTag = "tab_library"
    ),
    TopLevelTab(
        route = AppRoutes.ROUTE_HISTORY,
        label = "Historial",
        selectedIcon = Icons.Rounded.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "tab_history"
    ),
    TopLevelTab(
        route = AppRoutes.ROUTE_SAVED,
        label = "Guardadas",
        selectedIcon = Icons.Rounded.Bookmark,
        unselectedIcon = Icons.Outlined.BookmarkBorder,
        testTag = "tab_saved"
    )
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedMangaApp(
                currentUserId = user.uid,
                userDisplayName = user.displayName
            )
        }
    }
}

@Composable
private fun AuthenticatedMangaApp(
    currentUserId: String,
    userDisplayName: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    val exploreViewModel: ExploreViewModel = viewModel(
        key = "explore_$currentUserId",
        factory = viewModelFactory {
            initializer { ExploreViewModel(MangaCatalogRepository()) }
        }
    )

    val vocabularyViewModel: VocabularyViewModel = viewModel(
        key = "vocab_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                VocabularyViewModel(VocabularyRepository(db), currentUserId)
            }
        }
    )

    val actionMessage by vocabularyViewModel.actionMessage.collectAsStateWithLifecycle()
    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vocabularyViewModel.clearActionMessage()
        }
    }

    val isTopLevelRoute = TopTabs.any { it.route == currentRoute }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = if (isTopLevelRoute) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (isTopLevelRoute && !useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        TopTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelRoute && useNavigationRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        TopTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.ROUTE_EXPLORE,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(AppRoutes.ROUTE_EXPLORE) {
                        ExploreScreen(
                            viewModel = exploreViewModel,
                            onMangaClick = { manga ->
                                navController.navigate(AppRoutes.detailRoute(manga.id))
                            },
                            onSignOutClick = {
                                signOut(credentialManager, scope)
                            },
                            userDisplayName = userDisplayName
                        )
                    }

                    composable(AppRoutes.ROUTE_LIBRARY) {
                        LibraryScreen(
                            viewModel = vocabularyViewModel,
                            onOpenManga = { mangaId ->
                                navController.navigate(AppRoutes.detailRoute(mangaId))
                            }
                        )
                    }

                    composable(AppRoutes.ROUTE_HISTORY) {
                        HistoryScreen(viewModel = vocabularyViewModel)
                    }

                    composable(AppRoutes.ROUTE_SAVED) {
                        SavedScreen(viewModel = vocabularyViewModel)
                    }

                    composable(
                        route = AppRoutes.ROUTE_DETAIL,
                        arguments = listOf(navArgument("mangaId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val mangaId = Uri.decode(backStackEntry.arguments?.getString("mangaId").orEmpty())
                        val detailVm: MangaDetailViewModel = viewModel(
                            key = "detail_${currentUserId}_$mangaId",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                    MangaDetailViewModel(
                                        mangaId = mangaId,
                                        currentUserId = currentUserId,
                                        catalogRepository = MangaCatalogRepository(),
                                        vocabularyRepository = VocabularyRepository(db)
                                    )
                                }
                            }
                        )
                        MangaDetailScreen(
                            viewModel = detailVm,
                            onBack = { navController.popBackStack() },
                            onOpenChapter = { manga, chapter ->
                                navController.navigate(
                                    AppRoutes.readerRoute(
                                        mangaId = manga.id,
                                        mangaTitle = manga.title,
                                        coverUrl = manga.coverUrl,
                                        chapterId = chapter.id,
                                        chapterLabel = chapter.displayLabel,
                                        sourceLang = chapter.language
                                    )
                                )
                            }
                        )
                    }

                    composable(
                        route = AppRoutes.ROUTE_READER,
                        arguments = listOf(
                            navArgument("mangaId") { type = NavType.StringType; defaultValue = "" },
                            navArgument("mangaTitle") { type = NavType.StringType; defaultValue = "" },
                            navArgument("coverUrl") { type = NavType.StringType; defaultValue = "" },
                            navArgument("chapterId") { type = NavType.StringType; defaultValue = "" },
                            navArgument("chapterLabel") { type = NavType.StringType; defaultValue = "" },
                            navArgument("sourceLang") { type = NavType.StringType; defaultValue = "ja" }
                        )
                    ) { backStackEntry ->
                        val args = backStackEntry.arguments
                        val mangaId = Uri.decode(args?.getString("mangaId").orEmpty())
                        val mangaTitle = Uri.decode(args?.getString("mangaTitle").orEmpty())
                        val coverUrl = Uri.decode(args?.getString("coverUrl").orEmpty())
                        val chapterId = Uri.decode(args?.getString("chapterId").orEmpty())
                        val chapterLabel = Uri.decode(args?.getString("chapterLabel").orEmpty())
                        val sourceLang = Uri.decode(args?.getString("sourceLang").orEmpty()).ifBlank { "ja" }

                        val readerVm: ReaderViewModel = viewModel(
                            key = "reader_${currentUserId}_$chapterId",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                    ReaderViewModel(
                                        mangaId = mangaId,
                                        mangaTitle = mangaTitle,
                                        coverUrl = coverUrl,
                                        chapterId = chapterId,
                                        chapterLabel = chapterLabel,
                                        sourceLang = sourceLang,
                                        currentUserId = currentUserId,
                                        catalogRepository = MangaCatalogRepository(),
                                        aiRepository = AiTranslationRepository(),
                                        vocabularyRepository = VocabularyRepository(db)
                                    )
                                }
                            }
                        )
                        ReaderScreen(
                            viewModel = readerVm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
