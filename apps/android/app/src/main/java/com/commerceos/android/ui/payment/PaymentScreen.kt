package com.commerceos.android.ui.payment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.viewmodel.CheckoutUiState
import com.commerceos.android.viewmodel.ServiceabilityState

object PaymentConstants {
    const val COD = "COD"
}

@Composable
fun PaymentScreen(
    checkoutUiState: CheckoutUiState,
    onAuthorize: (String) -> Unit,
    onChangeAddress: (() -> Unit)? = null
) {
    var selectedMethod by remember { mutableStateOf(PaymentConstants.COD) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Review and pay", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
        Text("Confirm the delivery details before placing your order.", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
        Spacer(modifier = Modifier.height(Spacing.md))

        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
            shape = RoundedCornerShape(Radius.lg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Deliver to", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                        Text(
                            checkoutUiState.address?.let { "${it.tag} · ${it.addressLine}" } ?: "Address required",
                            style = CommerceTypography.BodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = CommerceColors.TextPrimary
                        )
                    }
                    if (onChangeAddress != null) {
                        TextButton(onClick = onChangeAddress) {
                            Text("Change", style = CommerceTypography.Caption, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                        }
                    }
                }
                ReviewRow(
                    label = "Arrives",
                    value = (checkoutUiState.serviceability as? ServiceabilityState.Success)?.response?.etaLabel ?: "After address confirmation"
                )
                ReviewRow(
                    label = "Items",
                    value = "${checkoutUiState.items.size} ${if (checkoutUiState.items.size == 1) "item" else "items"}"
                )
                ReviewRow(label = "Offers", value = "Best available prices applied")
                HorizontalDivider(color = CommerceColors.Border, thickness = 0.5.dp)
                PriceRow("Items total", MoneyFormatter.format(checkoutUiState.itemsSubtotal))
                PriceRow("Delivery fee", MoneyFormatter.format(checkoutUiState.deliveryFee))
                if (checkoutUiState.coldChainFee.signum() > 0) {
                    PriceRow("Temperature-safe packing", MoneyFormatter.format(checkoutUiState.coldChainFee))
                }
                HorizontalDivider(color = CommerceColors.Border, thickness = 0.5.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    Text(MoneyFormatter.format(checkoutUiState.grandTotal), style = CommerceTypography.Price, color = CommerceColors.TextPrimary)
                }
            }
        }

        Text("Payment", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
        Spacer(modifier = Modifier.height(Spacing.sm))

        Card(
            colors = CardDefaults.cardColors(containerColor = if (selectedMethod == PaymentConstants.COD) CommerceColors.InfoContainer else CommerceColors.Surface),
            modifier = Modifier.fillMaxWidth().clickable { selectedMethod = PaymentConstants.COD }.padding(bottom = 12.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedMethod == PaymentConstants.COD, onClick = { selectedMethod = PaymentConstants.COD })
                Spacer(modifier = Modifier.width(Spacing.md))
                Column {
                    Text("Cash on Delivery", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    Text("Pay cash when the order reaches you.", style = CommerceTypography.Caption, color = CommerceColors.TextMuted)
                }
            }
        }

        if (checkoutUiState.errorMessage != null) {
            Text(checkoutUiState.errorMessage, style = CommerceTypography.Caption, color = CommerceColors.Danger, modifier = Modifier.padding(vertical = Spacing.sm))
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onAuthorize(selectedMethod) },
            enabled = !checkoutUiState.isProcessing,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
            shape = RoundedCornerShape(Radius.md)
        ) {
            val buttonLabel = when {
                checkoutUiState.isProcessing -> "Processing..."
                selectedMethod == PaymentConstants.COD -> "Place Order"
                else -> "Pay ${MoneyFormatter.format(checkoutUiState.grandTotal)}"
            }
            Text(buttonLabel, style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Column {
        Text(label, style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
        Text(value, style = CommerceTypography.BodySmall, fontWeight = FontWeight.SemiBold, color = CommerceColors.TextPrimary)
    }
}

@Composable
private fun PriceRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = CommerceTypography.Caption, color = CommerceColors.TextSecondary)
        Text(value, style = CommerceTypography.Caption, color = CommerceColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}
