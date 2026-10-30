package com.commerceos.android.ui.address

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.ApiAddress
import java.util.Locale

/**
 * Production Address Card matching Blinkit's visual hierarchy.
 * Displays left badge with Home/Work icon and live proximity ("You're here" for <=100m or "X.XX km").
 */
@Composable
fun AddressCard(
    address: ApiAddress,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    distanceMeters: Double? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = if (isSelected) Color(0xFFF0FDF4) else Color.White,
        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF16A34A)) else BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Box: Soft cream container with Icon + "You're here" / Distance badge (Blinkit Pattern)
            Surface(
                color = Color(0xFFFFFBEB),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFEF3C7)),
                modifier = Modifier
                    .width(52.dp)
                    .defaultMinSize(minHeight = 56.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (address.tag.lowercase()) {
                            "work", "office" -> Icons.Default.Place
                            else -> Icons.Default.Home
                        },
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))

                    val isAtLocation = distanceMeters != null && distanceMeters <= 100.0
                    if (isAtLocation) {
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 1.dp)
                        ) {
                            Text(
                                text = "You're here",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF15803D),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    } else if (distanceMeters != null) {
                        Text(
                            text = String.format(Locale.US, "%.2f km", distanceMeters / 1000.0),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = if (address.isDefault) "Default" else "Saved",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (address.isDefault) Color(0xFF16A34A) else Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right Column: Tag, Full Address, Phone & Actions
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = address.tag.ifBlank { "Home" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )

                    if (isSelected) {
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "✓ Selected",
                                color = Color(0xFF166534),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (address.isDefault) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Default",
                                color = Color(0xFF475569),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = address.addressLine,
                    fontSize = 13.sp,
                    color = Color(0xFF334155),
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (address.city.isNotBlank()) {
                    Text(
                        text = "${address.city}${if (address.state.isNotBlank()) ", ${address.state}" else ""}${if (address.postalCode.isNotBlank()) " - ${address.postalCode}" else ""}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (address.contactPhone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Phone number: ${address.contactPhone}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Circular Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Menu Options Button (•••)
                    Box {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { showMenu = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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
                                text = { Text("Delete address", color = Color(0xFFDC2626)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626)) },
                                onClick = { showMenu = false; onDelete() }
                            )
                        }
                    }

                    // Edit Shortcut Button
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onEdit)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
