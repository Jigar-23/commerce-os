package com.commerceos.android.ui.root

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.util.MoneyFormatter
import java.math.BigDecimal

/** Global sticky cart bar displayed above bottom navigation. */
@Composable
fun GlobalCartBar(
    itemCount: Int,
    subtotal: BigDecimal?,
    etaLabel: String?,
    onClick: () -> Unit
) {
    if (itemCount <= 0) return

    Surface(
        color = CommerceColors.PrimaryDark,
        shape = RoundedCornerShape(Radius.Card),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                val countLabel = "$itemCount ${if (itemCount == 1) "ITEM" else "ITEMS"}"
                Text(
                    text = if (subtotal != null) "$countLabel • ${MoneyFormatter.format(subtotal)}" else countLabel,
                    style = CommerceTypography.BodySmall,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.OnPrimary
                )
                if (etaLabel != null) {
                    Text(
                        text = "Arrives $etaLabel",
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.SuccessSoft
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "View Cart",
                    style = CommerceTypography.Label,
                    fontWeight = FontWeight.Black,
                    color = CommerceColors.OnPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = CommerceColors.OnPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
