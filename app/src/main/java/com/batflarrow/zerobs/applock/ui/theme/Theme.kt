package com.batflarrow.zerobs.applock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
        darkColorScheme(
                primary = PrimaryDark,
                secondary = SecondaryDark,
                background = BackgroundDark,
                onPrimary = TextDark,
                onBackground = TextDark
        )

private val LightColorScheme =
        lightColorScheme(
                primary = PrimaryLight,
                secondary = SecondaryLight,
                background = BackgroundLight,
                onPrimary = TextLight,
                onBackground = TextLight
        )

@Composable
fun ZeroBSAppLockTheme(
        darkTheme: Boolean = isSystemInDarkTheme(),
        dynamicColor: Boolean = true,
        content: @Composable () -> Unit
) {
        val colorScheme =
                when {
                        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                                val context = LocalContext.current
                                if (darkTheme) dynamicDarkColorScheme(context)
                                else dynamicLightColorScheme(context)
                        }
                        darkTheme -> DarkColorScheme
                        else -> LightColorScheme
                }

        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
