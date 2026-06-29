package com.tvhanan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tvhanan.ui.theme.TextFaint

/**
 * Label kecil di atas tiap zona tombol (mis. "Navigasi", "Volume & Channel"),
 * dengan garis warna pendek di samping kiri sebagai penanda visual zona —
 * supaya mata bisa langsung kenali zona tanpa membaca teksnya secara penuh.
 */
@Composable
fun ZoneLabel(
    text: String,
    accentColor: Color? = null,
    accentBrush: Brush? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dashModifier = Modifier
            .size(width = 14.dp, height = 2.dp)
            .let { base ->
                when {
                    accentBrush != null -> base.background(accentBrush, RoundedCornerShape(2.dp))
                    accentColor != null -> base.background(accentColor, RoundedCornerShape(2.dp))
                    else -> base.background(TextFaint, RoundedCornerShape(2.dp))
                }
            }

        Box(modifier = dashModifier)
        Spacer(modifier = Modifier.size(width = 8.dp, height = 1.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint
        )
    }
}
