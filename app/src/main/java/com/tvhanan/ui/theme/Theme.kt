package com.tvhanan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dark-mode-only secara sengaja: app ini dipakai sambil menonton TV,
 * biasanya di ruangan gelap. Tidak ada dynamicColor/light theme supaya
 * identitas warna (mesh gradient + aksen teal/oranye) konsisten dan
 * tidak berubah ikut wallpaper user.
 */
private val TvDarkColorScheme = darkColorScheme(
    primary = NavAccent,
    secondary = NavAccent2,
    tertiary = AccentWarn,
    background = BgBase,
    surface = BgBase,
    surfaceVariant = GlassSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextDim,
    error = DisconnectedColor
)

@Composable
fun TvRemoteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = RemoteTypography,
        content = content
    )
}
