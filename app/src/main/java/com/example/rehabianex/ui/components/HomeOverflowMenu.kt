package com.example.rehabianex.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.rehabianex.R
import com.example.rehabianex.ui.theme.RehabianexColors

data class HomeMenuItem(
    val label: String,
    val route: String,
    @param:DrawableRes val iconRes: Int,
)

@Composable
fun HomeOverflowMenu(
    items: List<HomeMenuItem>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box {
            Surface(
                shape = CircleShape,
                color = RehabianexColors.GreenVerySoft,
                border = BorderStroke(1.dp, RehabianexColors.GreenSoft),
                shadowElevation = 2.dp,
            ) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_more),
                        contentDescription = "Opciones",
                        tint = RehabianexColors.GreenDark,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = RehabianexColors.GrayCard,
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = null,
                                tint = RehabianexColors.GreenDark,
                            )
                        },
                        onClick = {
                            expanded = false
                            onNavigate(item.route)
                        },
                    )
                }
            }
        }
    }
}
