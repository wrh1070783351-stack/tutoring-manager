package com.wrh.keshiguanjia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 品牌色：与应用图标的青色一致。 */
private val BrandPrimary = Color(0xFF0E7490)
private val BrandPrimaryDark = Color(0xFF67E8F9)

@Composable
fun KeshiGuanjiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(primary = BrandPrimaryDark)
        } else {
            lightColorScheme(primary = BrandPrimary)
        },
        content = content,
    )
}
