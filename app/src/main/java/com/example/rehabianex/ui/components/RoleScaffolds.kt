package com.example.rehabianex.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.rehabianex.R
import com.example.rehabianex.navigation.Routes

private val PatientNavItems = listOf(
    BottomNavItem("Inicio", Routes.PatientHome, R.drawable.ic_nav_home),
    BottomNavItem("Diario", Routes.EmotionalCheckin, R.drawable.ic_nav_diary),
    BottomNavItem("Apoyo", Routes.SupportNetwork, R.drawable.ic_nav_support),
    BottomNavItem("Ejerc.", Routes.Interventions, R.drawable.ic_nav_exercises),
    BottomNavItem("Agenda", Routes.Agenda, R.drawable.ic_nav_agenda),
)

private val SupervisorNavItems = listOf(
    BottomNavItem("Inicio", Routes.SupervisorHome, R.drawable.ic_nav_home),
    BottomNavItem("Pacientes", Routes.PatientsList, R.drawable.ic_nav_patients),
    BottomNavItem("Chat", Routes.AiChat, R.drawable.ic_nav_chat),
)

internal fun supervisorNavigationRoutes(limitedAccess: Boolean = false): List<String> {
    val items = if (limitedAccess) SupervisorNavItems.take(1) else SupervisorNavItems
    return items.map(BottomNavItem::route)
}

@Composable
fun SupervisorBottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    limitedAccess: Boolean = false,
) {
    val navigationItems = if (limitedAccess) {
        SupervisorNavItems.take(1)
    } else {
        SupervisorNavItems
    }
    BottomNavigationBar(
        items = navigationItems,
        selectedRoute = currentRoute,
        onItemClick = { onNavigate(it.route) },
    )
}

@Composable
fun PatientScreenScaffold(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenScaffold(
        modifier = Modifier.swipeBetweenNavItems(
            items = PatientNavItems,
            currentRoute = currentRoute,
            onNavigate = onNavigate,
        ),
        bottomBar = {
            BottomNavigationBar(
                items = PatientNavItems,
                selectedRoute = currentRoute,
                onItemClick = { onNavigate(it.route) },
            )
        },
        content = content,
    )
}

@Composable
fun SupervisorScreenScaffold(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    limitedAccess: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navigationItems = if (limitedAccess) {
        SupervisorNavItems.take(1)
    } else {
        SupervisorNavItems
    }
    ScreenScaffold(
        modifier = Modifier.swipeBetweenNavItems(
            items = navigationItems,
            currentRoute = currentRoute,
            onNavigate = onNavigate,
        ),
        bottomBar = {
            SupervisorBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                limitedAccess = limitedAccess,
            )
        },
        content = content,
    )
}

private fun Modifier.swipeBetweenNavItems(
    items: List<BottomNavItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
): Modifier {
    return pointerInput(currentRoute) {
        var dragAmount = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragAmount = 0f },
            onHorizontalDrag = { _, dragDelta ->
                dragAmount += dragDelta
            },
            onDragCancel = { dragAmount = 0f },
            onDragEnd = {
                val currentIndex = items.indexOfFirst { it.route == currentRoute }
                if (currentIndex == -1) return@detectHorizontalDragGestures

                val targetIndex = when {
                    dragAmount <= -SwipeThresholdPx -> currentIndex + 1
                    dragAmount >= SwipeThresholdPx -> currentIndex - 1
                    else -> currentIndex
                }.coerceIn(items.indices)

                if (targetIndex != currentIndex) {
                    onNavigate(items[targetIndex].route)
                }
                dragAmount = 0f
            },
        )
    }
}

private const val SwipeThresholdPx = 90f
