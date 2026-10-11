package com.example.rehabianex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(
        horizontal = 20.dp,
        vertical = 18.dp,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RehabianexColors.GrayBg,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RehabianexColors.GreenMain)
                    .statusBarsPadding()
                    .height(28.dp),
                )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        val scrollModifier = if (scrollable) {
            Modifier.verticalScroll(rememberScrollState())
        } else {
            Modifier
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(scrollModifier)
                .imePadding()
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
    )
}
