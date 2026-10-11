package com.example.rehabianex.ui.screens.patient

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.components.BackLink
import com.example.rehabianex.ui.components.BlueCard
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun ActionPlanScreen(onBack: () -> Unit) {
    ScreenScaffold {
        BackLink(onBack)
        ScreenHeader("Plan de Acción", "Para momentos difíciles")
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { 0.5f },
            modifier = Modifier.fillMaxWidth(),
            color = RehabianexColors.GreenMain,
        )
        Spacer(Modifier.height(6.dp))
        Text("Paso 2 de 4", color = RehabianexColors.GrayText)
        Spacer(Modifier.height(12.dp))
        RehabianexCard {
            Text("2. Técnica de respiración", fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text("Inhala contando hasta 4")
            Text("Mantén el aire contando hasta 4")
            Text("Exhala contando hasta 6")
            Text("Repite 5 veces")
        }
        Spacer(Modifier.height(12.dp))
        SoftCard { Text("✓ 1. Aléjate del detonante") }
        Spacer(Modifier.height(8.dp))
        BlueCard { Text("● 2. Técnica de respiración") }
        Spacer(Modifier.height(8.dp))
        RehabianexCard { Text("3. Recuerda tus motivos") }
        Spacer(Modifier.height(8.dp))
        RehabianexCard { Text("4. Contacta a tu apoyo") }
    }
}
