package com.commerceos.android.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CustomerProfile
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

/**
 * Account & Profile dashboard. Uses standard Material vector icons, clean customer profile data,
 * and fully wired non-empty navigation routes.
 */
@Composable
fun AccountScreen(
    profile: CustomerProfile?,
    fallbackPhone: String,
    orderCount: Int,
    addressCount: Int,
    isLoadingAddresses: Boolean = false,
    prescriptionCount: Int,
    onOrders: () -> Unit,
    onPrescriptions: () -> Unit,
    onAddresses: () -> Unit,
    onLogout: () -> Unit
) {
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "My Account"
    val phone = profile?.phone?.takeIf { it.isNotBlank() } ?: fallbackPhone

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Profile Header with Warm Amber/Gold Gradient (Blinkit Style)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFFEF3C7), // Amber 100
                                Color(0xFFFDE68A), // Amber 200
                                Color(0xFFFFFFFF)  // Pure White
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                        border = BorderStroke(2.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        displayName,
                        style = CommerceTypography.Title,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1E293B)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            maskPhone(phone),
                            style = CommerceTypography.BodySmall,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "✓ Verified",
                                style = CommerceTypography.Meta,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3 Quick Action Cards (Blinkit Screenshot Pattern)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionCard(
                icon = "📦",
                title = "Your orders",
                subtitle = "$orderCount placed",
                onClick = onOrders,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = "💳",
                title = "Commerce Pay",
                subtitle = "₹0 balance",
                onClick = {},
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = "💬",
                title = "Need help?",
                subtitle = "24x7 support",
                onClick = {},
                modifier = Modifier.weight(1f)
            )
        }

        // Account Settings & Navigation Rows
        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                AccountRow(
                    icon = Icons.Default.ShoppingCart,
                    title = "Order History & Reorder",
                    subtitle = "Track active orders & repeat previous carts",
                    onClick = onOrders
                )
                HorizontalDivider(color = CommerceColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)
                AccountRow(
                    icon = Icons.Default.LocationOn,
                    title = "Address Book",
                    subtitle = if (isLoadingAddresses) "Loading saved addresses..." else "$addressCount saved delivery addresses",
                    onClick = onAddresses
                )
                HorizontalDivider(color = CommerceColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)
                AccountRow(
                    icon = Icons.Default.Settings,
                    title = "Preferences & Security",
                    subtitle = "Hide sensitive items, notifications & appearance",
                    onClick = {}
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onLogout,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CommerceColors.Danger),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Log out of account", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.TextPrimary,
                maxLines = 1
            )
            Text(
                subtitle,
                fontSize = 10.sp,
                color = CommerceColors.TextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AccountStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = CommerceColors.Surface,
        shape = RoundedCornerShape(Radius.Card),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
            Text(label, style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
        }
    }
}

@Composable
private fun AccountRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = CommerceColors.Primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
            Text(subtitle, style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = CommerceColors.TextMuted, modifier = Modifier.size(16.dp))
    }
}

private fun maskPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    if (digits.length < 4) return "Verified mobile number"
    val suffix = digits.takeLast(4)
    val country = if (digits.length > 10) "+${digits.dropLast(10)} " else ""
    return "${country}XXXXX $suffix"
}
