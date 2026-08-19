package com.commerceos.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.Radius

/**
 * Reusable product image surface. Loads via Coil (memory + disk caching,
 * crossfade) and renders a shimmer while loading, a neutral pharmacy tile when
 * the URL is absent, and the same tile on error — so product grids keep a
 * clean, consistent retail frame no matter what the catalog serves.
 *
 * Every medicine visual in the app (cards, PDP, cart, orders) should flow
 * through this component instead of raw Image()/placeholder Text().
 */
@Composable
fun ProductImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    showShimmer: Boolean = true,
    shape: Shape = RoundedCornerShape(Radius.ImageTile),
    tint: Color = CommerceColors.TextMuted
) {
    val url = imageUrl?.trim().orEmpty()

    Box(
        modifier = modifier
            .clip(shape)
            .background(CommerceColors.SurfaceSubtle),
        contentAlignment = Alignment.Center
    ) {
        if (url.isEmpty()) {
            CommerceProductPlaceholder(tint = tint)
        } else {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build()
            )

            when (painter.state) {
                is AsyncImagePainter.State.Loading ->
                    if (showShimmer) {
                        ShimmerBox(modifier = Modifier.fillMaxSize(), shape = shape)
                    } else {
                        CommerceProductPlaceholder(tint = tint)
                    }

                is AsyncImagePainter.State.Error -> CommerceProductPlaceholder(tint = tint)

                else -> Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Neutral universal product placeholder surface for Commerce OS. */
@Composable
fun CommerceProductPlaceholder(
    modifier: Modifier = Modifier,
    tint: Color = CommerceColors.TextMuted
) {
    Box(
        modifier = modifier.fillMaxSize().background(CommerceColors.SurfaceSubtle),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barW = size.minDimension * 0.16f
            val barH = size.minDimension * 0.46f
            val color = tint.copy(alpha = 0.35f)
            drawRoundRect(
                color = color,
                topLeft = Offset((size.width - barW) / 2f, (size.height - barH) / 2f),
                size = Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset((size.width - barH) / 2f, (size.height - barW) / 2f),
                size = Size(barH, barW),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f)
            )
        }
    }
}

@Deprecated("Use CommerceProductPlaceholder instead", ReplaceWith("CommerceProductPlaceholder(modifier, tint)"))
@Composable
fun PharmacyPlaceholder(modifier: Modifier = Modifier, tint: Color = CommerceColors.TextMuted) {
    CommerceProductPlaceholder(modifier = modifier, tint = tint)
}
