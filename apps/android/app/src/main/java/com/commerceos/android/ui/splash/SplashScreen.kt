package com.commerceos.android.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.config.LocalClientConfiguration
import kotlinx.coroutines.delay

/**
 * High-aesthetic Splash Screen tailored for quick-commerce healthcare.
 * Inspired by Tata 1mg, Truemeds, and PharmEasy with brand trust badges,
 * 10-minute delivery cues, and smooth entry animation.
 */
@Composable
fun SplashScreen(
    onDismiss: () -> Unit
) {
    val clientConfig = LocalClientConfiguration.current
    val title = clientConfig.identity.splashTitle ?: clientConfig.identity.clientName
    val tagline = clientConfig.identity.splashTagline ?: "Your Trusted Licensed Pharmacy Partner"

    val scaleAnim = remember { Animatable(0.92f) }
    val alphaAnim = remember { Animatable(1f) }
    val pulseAnim = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Entrance animation
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        // Subtle breathing glow animation
        pulseAnim.animateTo(
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    LaunchedEffect(Unit) {
        // Splash duration: auto-transition after 1800ms
        delay(1800)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF0FDF4),
                        Color(0xFFE6F7EF)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        // Center Brand Identity
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .scale(scaleAnim.value)
                .alpha(alphaAnim.value)
        ) {
            // Glowing Pharmacy Icon Badge
            Box(contentAlignment = Alignment.Center) {
                // Ambient halo
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseAnim.value)
                        .background(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                )

                // Emerald Core Icon Container
                Surface(
                    color = Color(0xFF059669),
                    shape = RoundedCornerShape(26.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "💊",
                            fontSize = 38.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Brand Title
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A),
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Brand Tagline
            Text(
                text = tagline,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF047857),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 10-Minute Delivery Badge (Quick-Commerce Hallmark)
            Surface(
                color = Color(0xFF059669).copy(alpha = 0.10f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "10-Minute Express Delivery",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF065F46)
                    )
                }
            }
        }

        // Bottom Trust Section (Tata 1mg / Truemeds Pattern)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .alpha(alphaAnim.value)
        ) {
            // Regulatory & Quality Badges
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                TrustBadgePill(icon = "🛡️", text = "100% Genuine")
                Spacer(modifier = Modifier.width(8.dp))
                TrustBadgePill(icon = "🩺", text = "Rx Verified")
                Spacer(modifier = Modifier.width(8.dp))
                TrustBadgePill(icon = "❄️", text = "Cold-Chain")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Licensed Pharmacists • Real-Time Inventory • Express Dispatch",
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TrustBadgePill(
    icon: String,
    text: String
) {
    Surface(
        color = Color.White.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334155)
            )
        }
    }
}
