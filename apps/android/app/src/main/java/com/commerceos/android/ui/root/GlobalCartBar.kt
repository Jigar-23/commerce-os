package com.commerceos.android.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.ui.theme.PlusJakartaSans
import com.commerceos.android.util.MoneyFormatter
import java.math.BigDecimal

/**
 * Enterprise Quick Healthcare Floating Cart Bar.
 * Benchmarked directly against PharmEasy, Tata 1mg, and Zepto:
 * High contrast gradient, item count, formatted total, ETA badge, and arrow CTA.
 */
@Composable
fun GlobalCartBar(
    itemCount: Int,
    subtotal: BigDecimal?,
    etaLabel: String?,
    onClick: () -> Unit
) {
    if (itemCount <= 0) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF047857),
                            Color(0xFF065F46)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        val countLabel = "$itemCount ${if (itemCount == 1) "ITEM" else "ITEMS"}"
                        Text(
                            text = if (subtotal != null) "$countLabel • ${MoneyFormatter.format(subtotal)}" else countLabel,
                            fontSize = 13.5.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (!etaLabel.isNullOrBlank()) "⚡ $etaLabel" else "⚡ 10 MINS DELIVERY",
                            fontSize = 10.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View Cart",
                        fontSize = 13.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
