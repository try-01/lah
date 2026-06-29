package com.tvhanan.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvhanan.ui.theme.GlassBorder
import com.tvhanan.ui.theme.TextPrimary
import com.tvhanan.util.HapticUtil

/**
 * GlassButton + pemicu haptic otomatis saat tombol mulai ditekan
 * (bukan saat onClick/dilepas — supaya getar terasa instan, sesuai
 * keputusan desain awal: haptic di onPress, bukan onRelease).
 *
 * Dipakai untuk semua tombol remote fisik (D-pad, angka, power, dst)
 * supaya pemanggilan di RemoteScreen tidak perlu menulis ulang logic
 * HapticUtil.tick() di setiap tempat.
 */
@Composable
fun HapticGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    gradientColors: List<Color>? = null,
    borderColor: Color = GlassBorder,
    contentColor: Color = TextPrimary,
    enabled: Boolean = true,
    autoRepeat: Boolean = false, // Tambahkan parameter di sini
    content: @Composable () -> Unit
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        gradientColors = gradientColors,
        borderColor = borderColor,
        contentColor = contentColor,
        enabled = enabled,
        autoRepeat = autoRepeat, // Salurkan ke GlassButton
        onPressedChange = { pressed -> if (pressed) HapticUtil.tick() },
        content = content
    )
}

@Composable
fun HapticGlassLabelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    gradientColors: List<Color>? = null,
    borderColor: Color = GlassBorder,
    contentColor: Color = TextPrimary,
    autoRepeat: Boolean = false, // Tambahkan parameter di sini
    fontSize: TextUnit = 17.sp
) {
    HapticGlassButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        gradientColors = gradientColors,
        borderColor = borderColor,
        contentColor = contentColor,
        autoRepeat = autoRepeat // Salurkan ke HapticGlassButton
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
