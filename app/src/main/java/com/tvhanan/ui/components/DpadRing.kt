package com.tvhanan.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tvhanan.ui.theme.NavAccent
import com.tvhanan.ui.theme.NavAccent2
import com.tvhanan.ui.theme.TextPrimary
import com.tvhanan.util.HapticUtil

private enum class DpadDirection { UP, DOWN, LEFT, RIGHT }

@Composable
fun DpadRing(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 216.dp
) {
    val sweepBrush = remember {
        Brush.sweepGradient(
            listOf(
                NavAccent.copy(alpha = 0.55f),
                NavAccent2.copy(alpha = 0.55f),
                NavAccent.copy(alpha = 0.55f)
            )
        )
    }
    val radialBrush = remember {
        Brush.radialGradient(
            listOf(
                NavAccent.copy(alpha = 0.14f),
                Color(0xFF12141A),
                Color(0xFF0A0B0E)
            )
        )
    }
    Box(
        modifier = modifier
            .size(size)
            .background(brush = sweepBrush, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size - 6.dp)
                .background(brush = radialBrush, shape = CircleShape)
        ) {
            DpadArrowZone(onClick = onUp, alignment = Alignment.TopCenter, direction = DpadDirection.UP)
            DpadArrowZone(onClick = onDown, alignment = Alignment.BottomCenter, direction = DpadDirection.DOWN)
            DpadArrowZone(onClick = onLeft, alignment = Alignment.CenterStart, direction = DpadDirection.LEFT)
            DpadArrowZone(onClick = onRight, alignment = Alignment.CenterEnd, direction = DpadDirection.RIGHT)

            HapticGlassButton(
                onClick = onOk,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(74.dp),
                shape = CircleShape,
                gradientColors = listOf(
                    NavAccent.copy(alpha = 0.20f),
                    NavAccent2.copy(alpha = 0.18f)
                ),
                borderColor = NavAccent.copy(alpha = 0.45f)
            ) {
                Text(
                    text = "OK",
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.DpadArrowZone(
    onClick: () -> Unit,
    alignment: Alignment,
    direction: DpadDirection
) {
    val edgePadding = 2.dp
    val zoneModifier = when (direction) {
        DpadDirection.UP -> Modifier.align(alignment).padding(top = edgePadding)
        DpadDirection.DOWN -> Modifier.align(alignment).padding(bottom = edgePadding)
        DpadDirection.LEFT -> Modifier.align(alignment).padding(start = edgePadding)
        DpadDirection.RIGHT -> Modifier.align(alignment).padding(end = edgePadding)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing),
        label = "dpadArrowScale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) HapticUtil.tick()
    }

    Box(
        modifier = zoneModifier
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                if (isPressed) NavAccent.copy(alpha = 0.16f) else Color.Transparent,
                CircleShape
            )
            .repeatingClickable(interactionSource = interactionSource, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (direction) {
                DpadDirection.UP -> Icons.Filled.KeyboardArrowUp
                DpadDirection.DOWN -> Icons.Filled.KeyboardArrowDown
                DpadDirection.LEFT -> Icons.Filled.KeyboardArrowLeft
                DpadDirection.RIGHT -> Icons.Filled.KeyboardArrowRight
            },
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(34.dp)
        )
    }
}