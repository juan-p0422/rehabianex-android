package com.example.rehabianex.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rehabianex.ui.theme.RehabianexColors

data class BottomNavItem(
    val label: String,
    val route: String,
    @param:DrawableRes val iconRes: Int,
)

@Composable
fun BottomNavigationBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = RehabianexColors.GrayBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onItemClick(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = item.label,
                        tint = if (selected) {
                            RehabianexColors.GreenMain
                        } else {
                            RehabianexColors.GrayBorder
                        },
                        modifier = Modifier
                            .size(22.dp),
                    )
                    Text(
                        text = item.label,
                        color = if (selected) {
                            RehabianexColors.GreenDark
                        } else {
                            RehabianexColors.GrayText
                        },
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
