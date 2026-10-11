package com.example.rehabianex.ui.screens.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.ActionCard
import com.example.rehabianex.ui.components.BlueCard
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun InterventionsScreen(onNavigate: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(InterventionCatalog.Now) }
    var selectedExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    val exercises = InterventionCatalog.bySection(tab)
    val selectedExercise = InterventionCatalog.findById(selectedExerciseId)

    PatientScreenScaffold(Routes.Interventions, onNavigate) {
        ScreenHeader("Intervenciones", "Apoyo personalizado")
        Spacer(Modifier.height(8.dp))
        ChipSelector(
            options = InterventionCatalog.sections,
            selected = tab,
            onSelected = { selected ->
                tab = selected
                selectedExerciseId = null
            },
        )
        Spacer(Modifier.height(12.dp))
        InterventionIntro(tab)
        Spacer(Modifier.height(12.dp))
        exercises.forEachIndexed { index, exercise ->
            ActionCard(
                title = exercise.title,
                subtitle = "${exercise.duration} · ${exercise.focus}",
                buttonText = "▶ Iniciar",
                onClick = { selectedExerciseId = exercise.id },
            )
            if (index != exercises.lastIndex) {
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${exercises.size} ejercicios disponibles en $tab",
            color = RehabianexColors.GrayText,
        )
    }

    selectedExercise?.let { exercise ->
        InterventionGuideDialog(
            exercise = exercise,
            onDismiss = { selectedExerciseId = null },
            onOpenActionPlan = {
                selectedExerciseId = null
                onNavigate(Routes.ActionPlan)
            },
        )
    }
}

@Composable
private fun InterventionIntro(tab: String) {
    when (tab) {
        InterventionCatalog.Now -> SoftCard {
            Text("Para usar en el momento", fontWeight = FontWeight.Bold)
            Text("Ejercicios breves para bajar intensidad, alejarte del detonante y pedir apoyo.")
        }
        InterventionCatalog.Plan -> BlueCard {
            Text("Planes antes de la crisis", fontWeight = FontWeight.Bold)
            Text("Protocolos para preparar respuestas y reducir improvisación cuando aparezca riesgo.")
        }
        else -> RehabianexCard {
            Text("Biblioteca de habilidades", fontWeight = FontWeight.Bold)
            Text("Prácticas para entender patrones, regular emociones y fortalecer prevención.")
        }
    }
}

@Composable
private fun InterventionGuideDialog(
    exercise: InterventionExercise,
    onDismiss: () -> Unit,
    onOpenActionPlan: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RehabianexColors.GrayCard,
        title = {
            Column {
                Text(exercise.title, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = "${exercise.duration} · ${exercise.focus}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RehabianexColors.GrayText,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WarningCard {
                    Text("Si sientes riesgo inmediato, aléjate del detonante y contacta a tu red de apoyo.")
                }
                SoftCard {
                    Text("Cuándo usarlo", fontWeight = FontWeight.Bold)
                    Text(exercise.recommendedWhen)
                }
                RehabianexCard {
                    Text("Indicaciones", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    exercise.steps.forEachIndexed { index, step ->
                        Text("${index + 1}. $step")
                        if (index != exercise.steps.lastIndex) {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                BlueCard {
                    Text("Cierre", fontWeight = FontWeight.Bold)
                    Text(exercise.closing)
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (exercise.id == "rapid_action_plan") {
                    RehabianexButton(
                        text = "Abrir plan",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenActionPlan,
                    )
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                ) {
                    Text("Cerrar", color = RehabianexColors.GreenDark)
                }
            }
        },
    )
}
