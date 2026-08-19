package com.commerceos.android.ui.address

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.commerceos.android.model.ApiAddress
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius

/**
 * Production Address Card with overflow menu hierarchy.
 * Masks sensitive recipient contact details for privacy, limits long lines, and ensures accessible touch targets.
 */
@Composable
fun AddressCard(
    address: ApiAddress,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CommerceColors.InfoContainer else CommerceColors.Surface
        ),
        border = if (isSelected) BorderStroke(2.dp, CommerceColors.Primary) else BorderStroke(1.dp, CommerceColors.Border),
        shape = RoundedCornerShape(Radius.lg),
        modifier = modifier.fillMaxWidth().clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSelected, onClick = onSelect)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        address.tag.ifBlank { "Home" },
                        style = CommerceTypography.BodySmall,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.TextPrimary
                    )
                    if (address.isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(color = CommerceColors.InfoSoft, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "Default",
                                color = CommerceColors.Primary,
                                style = CommerceTypography.Meta,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Overflow Options Menu (•••) with 44dp Touch Target
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Address Options", tint = CommerceColors.TextSecondary)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit address") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { showMenu = false; onEdit() }
                        )
                        if (!address.isDefault) {
                            DropdownMenuItem(
                                text = { Text("Set as default delivery address") },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                onClick = { showMenu = false; onSetDefault() }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete address", color = CommerceColors.Danger) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CommerceColors.Danger) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                address.addressLine,
                style = CommerceTypography.Caption,
                fontWeight = FontWeight.SemiBold,
                color = CommerceColors.NeutralDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${address.city}, ${address.state} ${address.postalCode}",
                style = CommerceTypography.Meta,
                color = CommerceColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (address.contactName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                val maskedPhone = if (address.contactPhone.length >= 4) "••••• ${address.contactPhone.takeLast(4)}" else address.contactPhone
                Text(
                    "Deliver to: ${address.contactName}" + if (maskedPhone.isNotBlank()) " ($maskedPhone)" else "",
                    style = CommerceTypography.Meta,
                    color = CommerceColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
