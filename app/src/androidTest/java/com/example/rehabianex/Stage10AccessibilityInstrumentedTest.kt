package com.example.rehabianex

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.components.BottomNavItem
import com.example.rehabianex.ui.components.BottomNavigationBar
import com.example.rehabianex.ui.components.HomeMenuItem
import com.example.rehabianex.ui.components.HomeOverflowMenu
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.SimpleBarChart
import com.example.rehabianex.ui.components.TimeSelector12h
import com.example.rehabianex.ui.screens.patient.AgendaEventsUiState
import com.example.rehabianex.ui.screens.patient.AgendaScreen
import com.example.rehabianex.data.model.SupervisorChatData
import com.example.rehabianex.data.model.SupervisorChatMessage
import com.example.rehabianex.ui.screens.supervisor.AiChatScreen
import com.example.rehabianex.ui.screens.supervisor.SupervisorChatUiState
import org.junit.Rule
import org.junit.Test

class Stage10AccessibilityInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomNavigationAnnouncesTabsSelectionAndMinimumTouchTarget() {
        composeRule.setContent {
            BottomNavigationBar(
                items = listOf(
                    BottomNavItem("Inicio", "home", R.drawable.ic_nav_home),
                    BottomNavItem("Diario", "diary", R.drawable.ic_nav_diary),
                ),
                selectedRoute = "home",
                onItemClick = {},
            )
        }

        composeRule.onNodeWithContentDescription("Inicio")
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }

    @Test
    fun menuAndTimeStepperExposeAccessibleTargetsAndContext() {
        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                HomeOverflowMenu(
                    items = listOf(HomeMenuItem("Perfil", "profile", R.drawable.ic_nav_more)),
                    onNavigate = {},
                )
                TimeSelector12h(
                    value = "09:30",
                    onValueChange = {},
                    label = "Hora del evento",
                )
            }
        }

        composeRule.onNodeWithContentDescription("Opciones").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Disminuir Hora").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Aumentar Min").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun formLabelRemainsAvailableAfterEnteringText() {
        composeRule.setContent {
            var value by remember { mutableStateOf("") }
            InputField(value = value, onValueChange = { value = it }, label = "Nombre completo")
        }

        composeRule.onNodeWithText("Nombre completo").performTextInput("María López")
        composeRule.onNodeWithText("Nombre completo").assertExists()
    }

    @Test
    fun chartHasATextAlternativeForValues() {
        composeRule.setContent { SimpleBarChart(values = listOf(0.2f, 0.75f, 1f)) }

        composeRule.onNodeWithContentDescription(
            "Valores de la tendencia: 20 por ciento, 75 por ciento, 100 por ciento",
        ).assertExists()
    }

    @Test
    fun agendaPrimaryFlowRemainsReachableAtOneHundredFiftyPercentText() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                AgendaScreen(
                    onNavigate = {},
                    uiState = AgendaEventsUiState.Content(emptyList()),
                    onRetry = {},
                    onCompleteEvent = {},
                    onCancelEvent = {},
                    onReportAttendance = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Mi Agenda").assertIsDisplayed()
        composeRule.onNodeWithText("+ Nuevo evento").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Activar recordatorios").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun longAssistantResponseRemainsReachableAtOneHundredFiftyPercentText() {
        val longResponse = "Respuesta sintética extensa con espacios y acentos. ".repeat(12)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                AiChatScreen(
                    onNavigate = {},
                    onBack = {},
                    uiState = SupervisorChatUiState(
                        loading = false,
                        data = SupervisorChatData(
                            supervisorUid = "synthetic-supervisor",
                            patientsCount = 1,
                            authorized = true,
                            messages = listOf(
                                SupervisorChatMessage(text = longResponse, fromUser = false),
                            ),
                        ),
                    ),
                    onRetry = {},
                    onInputChanged = {},
                    onModeChanged = {},
                    onSend = {},
                )
            }
        }

        composeRule.onNodeWithText(longResponse).assertExists()
        composeRule.onNodeWithText("Enviar").assertIsDisplayed()
    }
}
