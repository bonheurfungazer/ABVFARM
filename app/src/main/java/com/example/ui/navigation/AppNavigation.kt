package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.viewmodels.FarmViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding

@Composable
fun AppNavigation(viewModel: FarmViewModel = viewModel()) {
    val navController = rememberNavController()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isBottomBarVisible by viewModel.isBottomBarVisible.collectAsState()

    val bottomBarRoutes = listOf(
        Screen.Dashboard.route,
        Screen.Rabbits.route,
        Screen.Mating.route,
        Screen.Inventory.route,
        Screen.Reminders.route
    )

    val isBottomBarRealVisible = currentRoute in bottomBarRoutes && (currentRoute != Screen.Rabbits.route || isBottomBarVisible)

    Scaffold(
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = isBottomBarRealVisible,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
            ) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Accueil") },
                        label = { Text("Accueil") },
                        selected = currentRoute == Screen.Dashboard.route,
                        onClick = {
                            if (currentRoute != Screen.Dashboard.route) {
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo(Screen.Dashboard.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Pets, contentDescription = "Mes Lapins") },
                        label = { Text("Lapins") },
                        selected = currentRoute == Screen.Rabbits.route,
                        onClick = {
                            if (currentRoute != Screen.Rabbits.route) {
                                navController.navigate(Screen.Rabbits.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Favorite, contentDescription = "Saillies") },
                        label = { Text("Saillies") },
                        selected = currentRoute == Screen.Mating.route,
                        onClick = {
                            if (currentRoute != Screen.Mating.route) {
                                navController.navigate(Screen.Mating.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = "Inventaire") },
                        label = { Text("Inventaire") },
                        selected = currentRoute == Screen.Inventory.route,
                        onClick = {
                            if (currentRoute != Screen.Inventory.route) {
                                navController.navigate(Screen.Inventory.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = "Rappels") },
                        label = { Text("Rappels") },
                        selected = currentRoute == Screen.Reminders.route,
                        onClick = {
                            if (currentRoute != Screen.Reminders.route) {
                                navController.navigate(Screen.Reminders.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(navController, viewModel)
            }
            composable(Screen.Rabbits.route) {
                RabbitListScreen(navController, viewModel)
            }
            composable(
                route = Screen.RabbitDetail.route,
                arguments = listOf(navArgument("rabbitId") { type = NavType.IntType })
            ) { backStackEntry ->
                val rabbitId = backStackEntry.arguments?.getInt("rabbitId") ?: return@composable
                RabbitDetailScreen(navController, viewModel, rabbitId)
            }
            composable(Screen.AddRabbit.route) {
                AddRabbitScreen(navController, viewModel)
            }
            composable(Screen.Mating.route) {
                MatingScreen(navController, viewModel)
            }
            composable(Screen.Inventory.route) {
                InventoryScreen(navController, viewModel)
            }
            composable(Screen.Reminders.route) {
                RemindersScreen(navController, viewModel)
            }
            composable(
                route = Screen.Genealogy.route,
                arguments = listOf(navArgument("rabbitId") { type = NavType.IntType })
            ) { backStackEntry ->
                val rabbitId = backStackEntry.arguments?.getInt("rabbitId") ?: return@composable
                GenealogyScreen(navController, viewModel, rabbitId)
            }
        }
    }
}
