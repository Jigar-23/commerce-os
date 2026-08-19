package com.commerceos.android.ui.account

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    prescriptionCount: Int,
    onOrders: () -> Unit,
    onPrescriptions: () -> Unit,
    onAddresses: () -> Unit,
    onLogout: () -> Unit
) {
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "My account"
    val phone = profile?.phone?.takeIf { it.isNotBlank() } ?: fallbackPhone

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg)
    ) {
        Text(
            "My account",
            style = CommerceTypography.Title,
            fontWeight = FontWeight.Bold,
            color = CommerceColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        // Profile Avatar Card (Clean neutral surface with brand accents)
        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
            shape = RoundedCornerShape(Radius.xl),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = CommerceColors.InfoContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = CommerceColors.Primary, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(modifier = Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(displayName, style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Surface(color = CommerceColors.SavingsSoft, shape = RoundedCornerShape(Radius.Chip)) {
                            Text(
                                "Verified mobile",
                                style = CommerceTypography.Meta,
                                fontWeight = FontWeight.Bold,
                                color = CommerceColors.Savings,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(maskPhone(phone), style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AccountStat("Orders", orderCount.toString(), Modifier.weight(1f))
            AccountStat("Addresses", addressCount.toString(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(Spacing.lg))

        // Account Menu List with Standard Material Vector Icons
        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
            shape = RoundedCornerShape(Radius.Card),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                AccountRow(
                    icon = Icons.Default.ShoppingCart,
                    title = "Orders & Reorder",
                    subtitle = "Track deliveries and buy previous items again",
                    onClick = onOrders
                )
                HorizontalDivider(color = CommerceColors.Border.copy(alpha = 0.4f), thickness = 0.5.dp)
                AccountRow(
                    icon = Icons.Default.LocationOn,
                    title = "Saved Addresses",
                    subtitle = "Manage Home, Work and family delivery locations",
                    onClick = onAddresses
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxl))

        OutlinedButton(
            onClick = onLogout,
            shape = RoundedCornerShape(Radius.Button),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CommerceColors.Danger),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Log out of account", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
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
