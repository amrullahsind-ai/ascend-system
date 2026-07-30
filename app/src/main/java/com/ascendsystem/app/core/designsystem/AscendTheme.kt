package com.ascendsystem.app.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

val Void = AscendColors.Background
val Panel = AscendColors.Surface
val Cyan = AscendColors.Cyan
val Violet = AscendColors.Violet
val Danger = AscendColors.Critical

@Composable
fun AscendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, secondary = Violet, error = Danger,
            background = Void, surface = Panel, onBackground = AscendColors.Text
        ),
        typography = Typography(),
        content = content
    )
}

@Composable
fun HoloPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SystemPanel(modifier = modifier, content = content)
}
