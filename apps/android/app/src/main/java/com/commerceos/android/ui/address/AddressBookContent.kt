package com.commerceos.android.ui.address

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.ApiAddress
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.viewmodel.ServiceabilityState

/**
 * Saved Address Book View component.
 * Displays saved address list, adaptive tag filters, multi-vertical fulfillment promises,
 * and handles address selection and modal prompts.
 */
@Composable
fun AddressBookContent(
    addresses: List<ApiAddress>,
    selectedAddressId: String?,
    isLoading: Boolean,
    errorMessage: String?,
    serviceability: ServiceabilityState,
    onSelectAddress: (ApiAddress) -> Unit,
    onEditAddress: (ApiAddress) -> Unit,
    onDeleteAddress: (String) -> Unit,
    onSetDefaultAddress: (String) -> Unit,
    onAddNewLocation: () -> Unit,
    onProceedToPayment: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var addressSearchQuery by remember { mutableStateOf("") }
    var selectedTagFilter by remember { mutableStateOf("All") }
    var addrToDelete by remember { mutableStateOf<ApiAddress?>(null) }

    val filteredAddresses = remember(addresses, addressSearchQuery, selectedTagFilter) {
        addresses.filter { addr ->
            val query = addressSearchQuery.trim()
            val matchesQuery = query.isBlank() ||
                    addr.addressLine.contains(query, ignoreCase = true) ||
                    addr.city.contains(query, ignoreCase = true) ||
                    addr.state.contains(query, ignoreCase = true) ||
                    addr.postalCode.contains(query, ignoreCase = true) ||
                    addr.contactName.contains(query, ignoreCase = true) ||
                    addr.tag.contains(query, ignoreCase = true)
            val matchesTag = selectedTagFilter == "All" || addr.tag.equals(selectedTagFilter, ignoreCase = true)
            matchesQuery && matchesTag
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Delivery Addresses",
                    style = CommerceTypography.Title,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.TextPrimary
                )
                Text(
                    "Confirm fulfillment availability & ETAs",
                    style = CommerceTypography.Meta,
                    color = CommerceColors.TextMuted
                )
            }
            Button(
                onClick = onAddNewLocation,
                colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                shape = RoundedCornerShape(Radius.Button)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add address", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Search Filter Bar
        OutlinedTextField(
            value = addressSearchQuery,
            onValueChange = { addressSearchQuery = it },
            placeholder = { Text("Search saved addresses...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CommerceColors.TextMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Adaptive Tag Filters (Scrollable LazyRow)
        if (addresses.size >= 2) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("All", "Home", "Work", "Family", "Other")) { tag ->
                    val isSelected = selectedTagFilter.equals(tag, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTagFilter = tag },
                        label = { Text(tag, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CommerceColors.Primary,
                            selectedLabelColor = CommerceColors.OnPrimary,
                            containerColor = CommerceColors.SurfaceSubtle,
                            labelColor = CommerceColors.TextPrimary
                        ),
                        shape = RoundedCornerShape(Radius.Chip)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Multi-Vertical Fulfillment Promise Header
        AddressServiceabilityCard(
            serviceabilityState = serviceability,
            selectedAddressId = selectedAddressId
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Address List
        if (isLoading && addresses.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CommerceColors.Primary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                if (filteredAddresses.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                            shape = RoundedCornerShape(Radius.lg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = CommerceColors.Primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    if (addresses.isEmpty()) "No saved addresses yet" else "No addresses match your search",
                                    style = CommerceTypography.Title,
                                    fontWeight = FontWeight.Bold,
                                    color = CommerceColors.TextPrimary
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    if (addresses.isEmpty())
                                        "Add your delivery address to see live 10-minute delivery ETAs and place your order."
                                    else
                                        "Try searching for a different street, area, tag, or city.",
                                    style = CommerceTypography.Caption,
                                    color = CommerceColors.TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                if (addresses.isEmpty()) {
                                    Spacer(modifier = Modifier.height(Spacing.md))
                                    Button(
                                        onClick = onAddNewLocation,
                                        colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                                        shape = RoundedCornerShape(Radius.Button),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Add Delivery Address",
                                            style = CommerceTypography.Label,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredAddresses, key = { it.id }) { address ->
                        AddressCard(
                            address = address,
                            isSelected = address.id == selectedAddressId,
                            onSelect = { onSelectAddress(address) },
                            onEdit = { onEditAddress(address) },
                            onDelete = { addrToDelete = address },
                            onSetDefault = { onSetDefaultAddress(address.id) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Bottom CTA (Only visible during checkout flow)
        if (onProceedToPayment != null) {
            val isServiceable = serviceability !is ServiceabilityState.Unavailable
            Button(
                onClick = onProceedToPayment,
                enabled = selectedAddressId != null && isServiceable,
                colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                shape = RoundedCornerShape(Radius.Button),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Proceed to Checkout", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Delete Confirmation Dialog
    if (addrToDelete != null) {
        AlertDialog(
            onDismissRequest = { addrToDelete = null },
            title = { Text("Delete Address", style = CommerceTypography.Title) },
            text = { Text("Are you sure you want to remove '${addrToDelete?.addressLine}'? This action cannot be undone.", style = CommerceTypography.BodySmall) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = addrToDelete!!.id
                        addrToDelete = null
                        onDeleteAddress(targetId)
                    }
                ) {
                    Text("Delete", color = CommerceColors.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { addrToDelete = null }) {
                    Text("Cancel", color = CommerceColors.TextSecondary)
                }
            }
        )
    }
}
