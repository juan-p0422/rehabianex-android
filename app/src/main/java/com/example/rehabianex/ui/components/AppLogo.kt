package com.example.rehabianex.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rehabianex.R

enum class LogoVariant(val drawableId: Int) {
    Normal(R.drawable.logo_normal),
    Symbol(R.drawable.logo_sf),
    Inverse(R.drawable.logo_inv_normal),
    InverseSymbol(R.drawable.logo_inv_sf),
}

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    variant: LogoVariant = LogoVariant.Normal,
) {
    Image(
        painter = painterResource(id = variant.drawableId),
        contentDescription = "Logo de RehabiAnex",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}
