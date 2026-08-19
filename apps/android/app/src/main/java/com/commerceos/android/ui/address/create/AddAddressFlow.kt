package com.commerceos.android.ui.address.create

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.commerceos.android.location.PlaceSearchResult
import com.commerceos.android.model.AddressConflictWarning
import com.commerceos.android.model.StructuredAddress
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.viewmodel.AddressPlatformStep
import com.commerceos.android.viewmodel.LocationAcquisitionState
import com.commerceos.android.viewmodel.SaveState

@Composable
fun AddAddressFlow(
    currentStep: AddressPlatformStep,
    existingAddress: StructuredAddress?,
    formAddress: StructuredAddress,
    searchQuery: String,
    searchResults: List<PlaceSearchResult>,
    isSearchingPlaces: Boolean,
    locationAcquisitionState: LocationAcquisitionState,
    gpsErrorMessage: String?,
    isReverseGeocoding: Boolean,
    saveState: SaveState,
    conflictWarning: AddressConflictWarning?,
    onBack: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRequestGps: () -> Unit,
    onSelectSearchResult: (PlaceSearchResult) -> Unit,
    onMapCameraSettled: (lat: Double, lng: Double) -> Unit,
    onRecenterGps: () -> Unit,
    onConfirmLocationPin: () -> Unit,
    onFormAddressChanged: (StructuredAddress) -> Unit,
    onApplyConflictSuggestion: () -> Unit,
    onSaveSubmitted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onRequestGps()
        }
    }

    val requestLocationPermission = {
        val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            onRequestGps()
        }
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CommerceColors.Background)
            .padding(16.dp)
    ) {
        // Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CommerceColors.TextPrimary)
            }
            Text(
                if (existingAddress == null) "Add delivery address" else "Edit delivery address",
                style = CommerceTypography.Title,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        when (currentStep) {
            is AddressPlatformStep.SearchingLocation -> {
                LocationSearchStep(
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    isSearching = isSearchingPlaces,
                    isLocatingGps = locationAcquisitionState is LocationAcquisitionState.AcquiringGps,
                    gpsErrorMessage = gpsErrorMessage,
                    onQueryChanged = onSearchQueryChanged,
                    onRequestGps = requestLocationPermission,
                    onSelectResult = onSelectSearchResult
                )
            }
            is AddressPlatformStep.LocationSelected,
            is AddressPlatformStep.ReverseGeocoding,
            is AddressPlatformStep.ConfirmingPin -> {
                LocationMapStep(
                    formAddress = formAddress,
                    isGeocoding = isReverseGeocoding,
                    onMapCameraSettled = onMapCameraSettled,
                    onRecenterGps = requestLocationPermission,
                    onConfirmLocation = onConfirmLocationPin
                )
            }
            AddressPlatformStep.EditingDetails -> {
                AddressDetailsStep(
                    formAddress = formAddress,
                    conflictWarning = conflictWarning,
                    validationResult = formAddress.validate(),
                    isSaving = saveState is SaveState.Saving,
                    onFormAddressChanged = onFormAddressChanged,
                    onApplyConflictSuggestion = onApplyConflictSuggestion,
                    onSaveSubmitted = onSaveSubmitted
                )
            }
            AddressPlatformStep.AddressBook -> {}
        }
    }
}
