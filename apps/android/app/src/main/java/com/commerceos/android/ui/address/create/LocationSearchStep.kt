package com.commerceos.android.ui.address.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.location.PlaceSearchResult
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

@Composable
fun LocationSearchStep(
    searchQuery: String,
    searchResults: List<PlaceSearchResult>,
    isSearching: Boolean,
    isLocatingGps: Boolean,
    gpsErrorMessage: String?,
    onQueryChanged: (String) -> Unit,
    onRequestGps: () -> Unit,
    onSelectResult: (PlaceSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text("Search building, street, area, landmark or PIN code", style = CommerceTypography.Caption, color = CommerceColors.TextMuted)
        Spacer(modifier = Modifier.height(Spacing.sm))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChanged,
            placeholder = { Text("Search location (e.g. DLF Cyber, Sector 45, 122018)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (isSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Location Acquisition Button
        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.SurfaceSubtle),
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onRequestGps)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp)
            ) {
                if (isLocatingGps) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CommerceColors.Primary)
                } else {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = CommerceColors.Primary)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Use current location", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                    Text(
                        gpsErrorMessage ?: if (isLocatingGps) "Getting your current location..." else "Pinpoint location using device sensors",
                        style = CommerceTypography.Meta,
                        color = if (gpsErrorMessage != null) CommerceColors.Danger else CommerceColors.TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Place Autocomplete Results List or Empty State
        if (!isSearching && searchQuery.trim().length >= 2 && searchResults.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No locations found", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Try searching for a nearby landmark, building, sector or 6-digit PIN code.", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(searchResults) { result ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "${result.primaryText}, ${result.secondaryText}. Double tap to select location."
                            }
                            .clickable { onSelectResult(result) }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = CommerceColors.TextSecondary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(result.primaryText, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold)
                                Text(result.secondaryText, style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
