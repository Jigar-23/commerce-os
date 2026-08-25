package com.commerceos.android.ui.address.create

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.commerceos.android.location.PlaceSearchResult
import com.commerceos.android.location.RealLocationMapViewport
import com.commerceos.android.model.AddressConflictWarning
import com.commerceos.android.model.StructuredAddress
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.viewmodel.AddressPlatformStep
import com.commerceos.android.viewmodel.LocationAcquisitionState
import com.commerceos.android.viewmodel.SaveState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val validationResult = formAddress.validate()
    val isSaving = saveState is SaveState.Saving
    val coroutineScope = rememberCoroutineScope()

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

    val configuration = LocalConfiguration.current
    val initialPeekHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp * 0.40f).dp.coerceIn(260.dp, 360.dp)
    }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = initialPeekHeight,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = Color.White,
        sheetShadowElevation = 16.dp,
        sheetDragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        coroutineScope.launch {
                            if (sheetState.currentValue == SheetValue.Expanded) {
                                sheetState.partialExpand()
                            } else {
                                sheetState.expand()
                            }
                        }
                    }
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFFCBD5E1),
                    shape = RoundedCornerShape(2.5.dp),
                    modifier = Modifier.size(width = 44.dp, height = 4.5.dp)
                ) {}
            }
        },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Delivery details",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSearchQueryChanged(" ") }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val localityDisplay = if (isReverseGeocoding) {
                                "Detecting location..."
                            } else {
                                val area = formAddress.subLocality.ifBlank { formAddress.street.ifBlank { formAddress.locality.ifBlank { "Selected Locality" } } }
                                if (formAddress.city.isNotBlank() && !area.contains(formAddress.city, ignoreCase = true)) {
                                    "$area, ${formAddress.city}"
                                } else {
                                    area
                                }
                            }
                            Text(
                                text = localityDisplay,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Address details* (House / Floor / Apartment)
                OutlinedTextField(
                    value = formAddress.houseNumber,
                    onValueChange = { onFormAddressChanged(formAddress.copy(houseNumber = it)) },
                    label = { Text("Address details*", fontSize = 12.sp) },
                    placeholder = { Text("Enter complete address*", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                    trailingIcon = {
                        if (formAddress.houseNumber.isNotBlank()) {
                            IconButton(onClick = { onFormAddressChanged(formAddress.copy(houseNumber = "")) }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF059669),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Example: A-504, Floor 5, Shanti Heights, Near City Mall",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Save address as (Home / Work / Other tags)
                Text(
                    text = "Save address as",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tags = listOf("Home" to Icons.Default.Home, "Work" to Icons.Default.Place, "Other" to Icons.Default.Star)
                    tags.forEach { (tag, icon) ->
                        val isSelected = formAddress.tag.equals(tag, ignoreCase = true)
                        Surface(
                            color = if (isSelected) Color(0xFFDCFCE7) else Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF16A34A) else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onFormAddressChanged(formAddress.copy(tag = tag)) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF166534) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tag,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF166534) else Color(0xFF334155)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Blinkit Signature Contact Details Section (Myself vs Someone else)
                var isForSomeoneElse by remember { mutableStateOf(formAddress.contactName.isNotBlank() && formAddress.contactName != "Myself") }

                Text(
                    text = "Contact details",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Myself Radio Option
                    Row(
                        modifier = Modifier
                            .clickable {
                                isForSomeoneElse = false
                                onFormAddressChanged(formAddress.copy(contactName = ""))
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isForSomeoneElse,
                            onClick = {
                                isForSomeoneElse = false
                                onFormAddressChanged(formAddress.copy(contactName = ""))
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF16A34A))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Myself",
                            fontSize = 13.sp,
                            fontWeight = if (!isForSomeoneElse) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isForSomeoneElse) Color(0xFF0F172A) else Color(0xFF64748B)
                        )
                    }

                    // Someone else Radio Option
                    Row(
                        modifier = Modifier
                            .clickable { isForSomeoneElse = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isForSomeoneElse,
                            onClick = { isForSomeoneElse = true },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF16A34A))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Someone else",
                            fontSize = 13.sp,
                            fontWeight = if (isForSomeoneElse) FontWeight.Bold else FontWeight.Medium,
                            color = if (isForSomeoneElse) Color(0xFF0F172A) else Color(0xFF64748B)
                        )
                    }
                }

                if (isForSomeoneElse) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = formAddress.contactName,
                        onValueChange = { onFormAddressChanged(formAddress.copy(contactName = it)) },
                        label = { Text("Receiver's name*", fontSize = 12.sp) },
                        placeholder = { Text("Friend or family member name", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF059669),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Recipient Contact (Phone)
                OutlinedTextField(
                    value = formAddress.contactPhone,
                    onValueChange = { onFormAddressChanged(formAddress.copy(contactPhone = it)) },
                    label = { Text(if (isForSomeoneElse) "Receiver's phone number*" else "Receiver phone number", fontSize = 12.sp) },
                    placeholder = { Text("+91 90507 23429", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF059669),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onSaveSubmitted,
                    enabled = !isSaving && validationResult.isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF166534),
                        disabledContainerColor = Color(0xFFE2E8F0)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving address...", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Text(
                            text = "Save address",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = if (validationResult.isValid) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select delivery location",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = {
                        Text("Search for area, street name...", fontSize = 13.sp, color = Color(0xFF94A3B8))
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF059669),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                RealLocationMapViewport(
                    centerPoint = formAddress.geoLocation,
                    isGeocoding = isReverseGeocoding,
                    onMapCameraSettled = onMapCameraSettled,
                    onRecenterGps = requestLocationPermission,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-40).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = Color(0xFF18181B),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = "Move pin to your exact delivery location",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clickable(onClick = requestLocationPermission)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Use current location",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857)
                        )
                    }
                }

                if (searchQuery.trim().length >= 2) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSearchingPlaces) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF16A34A), modifier = Modifier.size(28.dp))
                                }
                            }
                        } else if (searchResults.isEmpty()) {
                            item {
                                Text(
                                    "No areas found. Try a landmark or city name.",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            items(searchResults) { result ->
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectSearchResult(result)
                                            onSearchQueryChanged("")
                                        }
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(result.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                            Text(result.secondaryText, fontSize = 12.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
