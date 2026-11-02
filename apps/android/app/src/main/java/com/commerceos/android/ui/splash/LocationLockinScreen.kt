package com.commerceos.android.ui.splash

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.delay

/**
 * High-performing Location Lock-in Interstitial benchmarked directly against Tata 1mg.
 *
 * Appears for ~1.2s right after splash or during startup location acquisition:
 * 1. Phase 1 (0ms - 600ms): Soft stylized map background with pulsing dark radar arrow
 *    "Searching your location..."
 * 2. Phase 2 (600ms - 1300ms): Spotlight circular focus locks onto the map, arrow turns
 *    quick-commerce green, boldly displaying "Delivering to [Locality] • 10 MINS".
 * 3. Phase 3: Seamlessly crossfades into the main Home screen.
 */
@Composable
fun LocationLockinScreen(
    locality: String = "Rewari",
    onDismiss: () -> Unit
) {
    var isLockedIn by remember { mutableStateOf(false) }

    val radarWave = remember { Animatable(0.4f) }
    val radarAlpha = remember { Animatable(0.8f) }
    val spotlightScale = remember { Animatable(0.7f) }
    val arrowRotate = remember { Animatable(-35f) }

    // Pulsing radar animation
    LaunchedEffect(Unit) {
        radarWave.animateTo(
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    LaunchedEffect(Unit) {
        radarAlpha.animateTo(
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    // Step 1 -> Step 2 -> Transition to Home
    LaunchedEffect(Unit) {
        delay(600) // Phase 1: searching location
        isLockedIn = true
        spotlightScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        )
        arrowRotate.animateTo(
            targetValue = -45f,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        )
        delay(650) // Phase 2: display "Delivering to [Locality]"
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFBF9))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() } // 1-tap skip for impatient power users
    ) {
        // 1. STYLIZED PROCEDURAL MAP BACKGROUND (Tata 1mg Signature)
        ProceduralVectorMap(
            modifier = Modifier.fillMaxSize(),
            isHighlightActive = isLockedIn
        )

        // 2. CENTER LOCATION FOCUS & RADAR
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(170.dp)
            ) {
                // Outer Radar Wave (Searching phase)
                if (!isLockedIn) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(radarWave.value)
                            .alpha(radarAlpha.value)
                            .background(Color(0xFF0F172A).copy(alpha = 0.15f), CircleShape)
                    )
                }

                // Spotlight Disc (Tata 1mg Step 2 circular map magnifying glass)
                Surface(
                    color = if (isLockedIn) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.5f),
                    shape = CircleShape,
                    shadowElevation = if (isLockedIn) 8.dp else 1.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isLockedIn) 2.dp else 1.dp,
                        color = if (isLockedIn) Color(0xFF0C831F) else Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier
                        .size(120.dp)
                        .scale(spotlightScale.value)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Inner Map Fragment Details
                        if (isLockedIn) {
                            Canvas(modifier = Modifier.size(120.dp)) {
                                drawCircle(
                                    color = Color(0xFFE8F5E9),
                                    radius = size.minDimension / 2
                                )
                                drawLine(
                                    color = Color(0xFFFBBF24),
                                    start = Offset(0f, size.height * 0.45f),
                                    end = Offset(size.width, size.height * 0.55f),
                                    strokeWidth = 14f
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(size.width * 0.5f, 0f),
                                    end = Offset(size.width * 0.5f, size.height),
                                    strokeWidth = 10f
                                )
                            }
                        }

                        // Navigation Location Arrow
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = if (isLockedIn) Color(0xFF0C831F) else Color(0xFF1E293B),
                            modifier = Modifier
                                .size(42.dp)
                                .rotate(arrowRotate.value)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Text State Transition: "Searching your location..." -> "Delivering to [Locality]"
            AnimatedContent(
                targetState = isLockedIn,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "LocationTextTransition"
            ) { locked ->
                if (!locked) {
                    Text(
                        text = "Searching your location...",
                        fontSize = 17.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Delivering to",
                            fontSize = 14.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = locality.ifBlank { "Rewari" },
                            fontSize = 24.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            letterSpacing = (-0.5).sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFF0C831F),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "10 MINS DELIVERY",
                                    fontSize = 11.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF096818)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Canvas-rendered faint procedural street & river network matching Tata 1mg's vector map look.
 * Renders instantly with zero network latency and hardware-accelerated 60 FPS performance.
 */
@Composable
private fun ProceduralVectorMap(
    modifier: Modifier = Modifier,
    isHighlightActive: Boolean
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // River curve in faint blue
        val riverPath = Path().apply {
            moveTo(0f, h * 0.72f)
            cubicTo(
                w * 0.35f, h * 0.65f,
                w * 0.15f, h * 0.90f,
                w * 0.40f, h * 0.95f
            )
        }
        drawPath(
            path = riverPath,
            color = Color(0xFFE8F1FA),
            style = Stroke(width = 38f)
        )

        // Arterial roads in faint sand/gold
        val roadColor = if (isHighlightActive) Color(0xFFF7E9CE) else Color(0xFFF1EDE4)
        drawLine(
            color = roadColor,
            start = Offset(0f, h * 0.28f),
            end = Offset(w, h * 0.36f),
            strokeWidth = 14f
        )
        drawLine(
            color = roadColor,
            start = Offset(w * 0.22f, 0f),
            end = Offset(w * 0.45f, h),
            strokeWidth = 16f
        )
        drawLine(
            color = roadColor,
            start = Offset(w * 0.85f, 0f),
            end = Offset(w * 0.60f, h),
            strokeWidth = 12f
        )
        drawLine(
            color = roadColor,
            start = Offset(0f, h * 0.52f),
            end = Offset(w, h * 0.46f),
            strokeWidth = 20f
        )

        // Local connecting streets
        val streetColor = Color(0xFFF4EFE6)
        drawLine(
            color = streetColor,
            start = Offset(w * 0.10f, h * 0.10f),
            end = Offset(w * 0.90f, h * 0.22f),
            strokeWidth = 8f
        )
        drawLine(
            color = streetColor,
            start = Offset(w * 0.15f, h * 0.40f),
            end = Offset(w * 0.80f, h * 0.65f),
            strokeWidth = 8f
        )
        drawLine(
            color = streetColor,
            start = Offset(w * 0.50f, h * 0.30f),
            end = Offset(w * 0.30f, h * 0.85f),
            strokeWidth = 9f
        )

        // Junction nodes
        drawCircle(
            color = Color(0xFFFEE2E2),
            radius = 12f,
            center = Offset(w * 0.66f, h * 0.44f)
        )
        drawCircle(
            color = Color(0xFFFEE2E2),
            radius = 12f,
            center = Offset(w * 0.66f, h * 0.85f)
        )
    }
}
