package com.example.rehabianex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.theme.RehabianexColors

private val CardShape = RoundedCornerShape(14.dp)

@Composable
fun RehabianexCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    BaseCard(
        modifier = modifier,
        background = RehabianexColors.GrayCard,
        borderColor = RehabianexColors.GrayBorder,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = BaseCard(
    modifier = modifier,
    background = RehabianexColors.GreenSoft,
    content = content,
)

@Composable
fun BlueCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = BaseCard(
    modifier = modifier,
    background = RehabianexColors.BlueLight,
    content = content,
)

@Composable
fun WarningCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = BaseCard(
    modifier = modifier,
    background = RehabianexColors.WarningBg,
    borderColor = RehabianexColors.WarningBorder,
    contentPadding = PaddingValues(14.dp),
    content = content,
)

@Composable
fun DangerCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = BaseCard(
    modifier = modifier,
    background = RehabianexColors.DangerBg,
    borderColor = RehabianexColors.DangerBorder,
    contentPadding = PaddingValues(14.dp),
    content = content,
)

@Composable
fun AiCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = BaseCard(
    modifier = modifier,
    background = RehabianexColors.AiBg,
    borderColor = RehabianexColors.AiBorder,
    contentPadding = PaddingValues(14.dp),
    content = content,
)

@Composable
private fun BaseCard(
    modifier: Modifier = Modifier,
    background: Color,
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = background,
        border = borderColor?.let { BorderStroke(1.dp, it) },
        shadowElevation = if (borderColor != null) 1.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}
