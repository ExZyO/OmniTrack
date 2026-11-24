package com.omnitrack.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.ui.screens.detail.DetailScreen
import com.omnitrack.app.ui.screens.detail.DetailViewModel
import com.omnitrack.app.ui.screens.home.HomeScreen
import com.omnitrack.app.ui.screens.home.HomeViewModel
import com.omnitrack.app.ui.screens.home.views.DiscoverView
import com.omnitrack.app.ui.screens.settings.ArtworkPriorityScreen
import com.omnitrack.app.ui.screens.settings.DetailsPageLayoutScreen
import com.omnitrack.app.ui.screens.settings.ManageCategoriesScreen
import com.omnitrack.app.ui.screens.settings.VisualPresentationScreen
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{mediaId}"
    const val DISCOVER_SEARCH = "discover_search?query={query}"
    const val ARTWORK_PRIORITY = "settings/artwork_priority"
    const val VISUAL_PRESENTATION = "settings/visual_presentation"
    const val DETAILS_PAGE_LAYOUT = "settings/details_page_layout"
    const val MANAGE_CATEGORIES = "settings/manage_categories"

    fun detail(mediaId: Long) = "detail/$mediaId"
    fun discoverSearch(query: String) = "discover_search?query=${Uri.encode(query)}"
}

@Composable
fun OmniTrackNavigation(
    navController: NavHostController,
    repository: MediaRepository
) {
    var lastNavTimeMs by remember { mutableLongStateOf(0L) }
    val navigateDebounced: (String) -> Unit = { route ->
        val now = SystemClock.elapsedRealtime()
        if (now - lastNavTimeMs >= 500L) {
            lastNavTimeMs = now
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(
            route = Routes.HOME,
            enterTransition = { fadeIn(spring(stiffness = Spring.StiffnessMedium)) },
            exitTransition = {
                fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            },
            popEnterTransition = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val coverCache = androidx.compose.runtime.remember { (context.applicationContext as com.omnitrack.app.OmniTrackApplication).coverCache }
            val diskCache = androidx.compose.runtime.remember { coil3.SingletonImageLoader.get(context).diskCache }
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(repository, coverCache, diskCache)
            )

            HomeScreen(
                viewModel = homeViewModel,
                onItemClick = { mediaId ->
                    navigateDebounced(Routes.detail(mediaId))
                },
                onNavigateToArtworkPriority = {
                    navigateDebounced(Routes.ARTWORK_PRIORITY)
                },
                onNavigateToVisualPresentation = {
                    navigateDebounced(Routes.VISUAL_PRESENTATION)
                },
                onNavigateToDetailsPageLayout = {
                    navigateDebounced(Routes.DETAILS_PAGE_LAYOUT)
                },
                onNavigateToManageCategories = {
                    navigateDebounced(Routes.MANAGE_CATEGORIES)
                }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.LongType }
            ),
            enterTransition = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            },
            popExitTransition = {
                fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: return@composable
            val context = LocalContext.current
            val app = context.applicationContext as OmniTrackApplication
            val prefManager = remember(context) { app.preferenceManager }

            val detailViewModel: DetailViewModel = viewModel(
                factory = DetailViewModel.factory(
                    repository = repository,
                    mediaId = mediaId,
                    preferenceManager = prefManager,
                    trackerSyncEngine = app.trackerSyncEngine,
                    trackerTokenManager = app.trackerTokenManager
                )
            )

            DetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() },
                onSearchInDiscover = { titleQuery ->
                    if (titleQuery.isNotBlank()) {
                        navigateDebounced(Routes.discoverSearch(titleQuery))
                    }
                }
            )
        }

        composable(
            route = Routes.DISCOVER_SEARCH,
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
            enterTransition = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            },
            popExitTransition = {
                fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
        ) { backStackEntry ->
            val rawQuery = backStackEntry.arguments?.getString("query").orEmpty()
            val decodedQuery = remember(rawQuery) { Uri.decode(rawQuery) }
            val context = LocalContext.current
            val app = context.applicationContext as OmniTrackApplication
            val prefManager = remember(context) { app.preferenceManager }
            val scope = rememberCoroutineScope()
            val allMedia by repository.getAllMediaSummaries().collectAsState(initial = emptyList())

            DiscoverView(
                allMedia = allMedia,
                initialSearchQuery = decodedQuery,
                onBack = { navController.popBackStack() },
                onItemClick = { id -> navigateDebounced(Routes.detail(id)) },
                onAddMedia = { searchItem ->
                    scope.launch {
                        val entity = searchItem.toMediaItemEntity()
                        val newId = repository.insertMedia(entity)
                        val defCat = prefManager.chimanewLibrarySettings.firstOrNull()?.defaultCategoryId
                        if (newId > 0L && defCat != null && defCat > 0L) {
                            prefManager.setMediaCategories(newId, setOf(defCat))
                        }
                    }
                },
                modifier = Modifier.statusBarsPadding(),
                homeViewModel = null
            )
        }

        composable(
            route = Routes.MANAGE_CATEGORIES,
            enterTransition = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            },
            popExitTransition = {
                fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
        ) {
            val context = LocalContext.current
            val app = context.applicationContext as OmniTrackApplication
            ManageCategoriesScreen(
                repository = repository,
                preferenceManager = app.preferenceManager,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ARTWORK_PRIORITY,
            enterTransition = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            },
            popExitTransition = {
                fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
        ) {
            ArtworkPriorityScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.VISUAL_PRESENTATION,
            enterTransition = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            },
            popExitTransition = {
                fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
        ) {
            VisualPresentationScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.DETAILS_PAGE_LAYOUT,
            enterTransition = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            },
            popExitTransition = {
                fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
        ) {
            DetailsPageLayoutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
