package com.example.rehabianex.navigation

import androidx.navigation.NavHostController

internal fun NavHostController.navigateSingleTop(route: String) {
    if (currentDestination?.route == route) return

    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}

internal fun NavHostController.returnToLogin() {
    navigate(Routes.Login) {
        popUpTo(graph.id) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
