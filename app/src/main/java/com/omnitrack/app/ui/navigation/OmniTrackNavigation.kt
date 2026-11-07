package com.omnitrack.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.ui.screens.detail.DetailScreen
import com.omnitrack.app.ui.screens.detail.DetailViewModel
import com.omnitrack.app.ui.screens.home.HomeScreen
import com.omnitrack.app.ui.screens.home.HomeViewModel
import com.omnitrack.app.ui.screens.settings.ArtworkPriorityScreen
import com.omnitrack.app.ui.screens.settings.VisualPresentationScreen

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{mediaId}"
    const val ARTWORK_PRIORITY = "settings/artwork_priority"
    const val VISUAL_PRESENTATION = "settings/visual_presentation"

    fun detail(mediaId: Long) = "detail/$mediaId"
}

@Composable
fun OmniTrackNavigation(
    navController: NavHostController,
    repository: MediaRepository
) {
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
                    navController.navigate(Routes.detail(mediaId))
                },
                onNavigateToArtworkPriority = {
                    navController.navigate(Routes.ARTWORK_PRIORITY)
                },
                onNavigateToVisualPresentation = {
                    navController.navigate(Routes.VISUAL_PRESENTATION)
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
            val prefManager = remember { PreferenceManager(context) }

            val detailViewModel: DetailViewModel = viewModel(
                factory = DetailViewModel.factory(repository, mediaId, preferenceManager = prefManager)
            )

            DetailScreen(
                viewModel = detailViewModel,
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
    }
}
