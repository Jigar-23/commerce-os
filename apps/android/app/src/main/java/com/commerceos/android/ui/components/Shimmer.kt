package com.commerceos.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceMotion
import com.commerceos.android.ui.theme.Radius

/**
 * Skeleton / shimmer loading language. Placeholders establish structure (size,
 * text hierarchy) before network data arrives instead of a blank spinner.
 */

private val ShimmerBase = CommerceColors.Placeholder
private val ShimmerHighlight = CommerceColors.SurfaceSubtle

/** An animated horizontal sweep used as a loading background brush. */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = CommerceMotion.Emphasized,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    return Brush.linearGradient(
        colors = listOf(ShimmerBase, ShimmerHighlight, ShimmerBase),
        start = Offset(x - 300f, 0f),
        end = Offset(x + 300f, 0f)
    )
}

/** Applies an animated shimmer as the background of the modifier. */
@Composable
fun Modifier.shimmer(): Modifier = this.background(shimmerBrush())

/** A shimmer block (image frame, tile, etc.) clipped to [shape]. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.Card)
) {
    Box(modifier = modifier.clip(shape).background(shimmerBrush()))
}

/** A static placeholder block for stable, non-animated frames. */
@Composable
fun PlaceholderBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.Card),
    color: Color = ShimmerBase
) {
    Box(modifier = modifier.clip(shape).background(SolidColor(color)))
}

/** A shimmer text placeholder of the given [width]/[height]. */
@Composable
fun SkeletonText(
    width: Dp = 120.dp,
    height: Dp = 14.dp,
    shape: Shape = RoundedCornerShape(Radius.Chip)
) {
    ShimmerBox(modifier = Modifier.width(width).height(height), shape = shape)
}

/** A full shimmer placeholder for a product image area (portrait ratio). */
@Composable
fun SkeletonProductImage(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.ImageTile)
) {
    ShimmerBox(modifier = modifier, shape = shape)
}
