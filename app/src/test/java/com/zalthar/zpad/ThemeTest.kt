package com.zalthar.zpad

import androidx.compose.ui.graphics.luminance
import com.zalthar.zpad.ui.theme.noteColors
import com.zalthar.zpad.ui.theme.themeNames
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ThemeTest {
    @Test fun everyThemeHasReadableLightAndDarkVariants() {
        themeNames.forEach { name ->
            listOf(false, true).forEach { dark ->
                val colors = noteColors(name, dark)
                val background = colors.background.luminance()
                val text = colors.onBackground.luminance()
                val contrast = (max(background, text) + 0.05f) / (min(background, text) + 0.05f)
                assertTrue("$name dark=$dark contrast", contrast >= 4.5f)
                assertTrue("$name dark=$dark brightness", if (dark) background < text else background > text)
            }
        }
    }
}
