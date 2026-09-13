package com.commerceos.rider.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.commerceos.rider.model.RoutePoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

/**
 * Zepto/Blinkit Clean Light Map Style for Rider Navigation.
 */
val RIDER_LIGHT_MAP_STYLE = """
[
  {
    "elementType": "geometry",
    "stylers": [{ "color": "#f8f9fa" }]
  },
  {
    "elementType": "labels.icon",
    "stylers": [{ "visibility": "off" }]
  },
  {
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#616161" }]
  },
  {
    "elementType": "labels.text.stroke",
    "stylers": [{ "color": "#f5f5f5" }]
  },
  {
    "featureType": "poi",
    "elementType": "geometry",
    "stylers": [{ "color": "#eeeeee" }]
  },
  {
    "featureType": "poi.park",
    "elementType": "geometry",
    "stylers": [{ "color": "#e8f5e9" }]
  },
  {
    "featureType": "road",
    "elementType": "geometry",
    "stylers": [{ "color": "#ffffff" }]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry",
    "stylers": [{ "color": "#dadada" }]
  },
  {
    "featureType": "water",
    "elementType": "geometry",
    "stylers": [{ "color": "#c8e0f4" }]
  }
]
""".trimIndent()

/**
 * Native Google Maps Navigation View for Delivery Riders.
 * 120 FPS hardware acceleration, smooth camera tracking, and clean road geometry.
 */
@Composable
fun NativeGoogleRiderNavMap(
    merchantLat: Double,
    merchantLng: Double,
    customerLat: Double,
    customerLng: Double,
    riderLat: Double? = null,
    riderLng: Double? = null,
    riderHeading: Float? = null,
    waypoints: List<RoutePoint> = emptyList(),
    isPhase1: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val storeLatLng = remember(merchantLat, merchantLng) {
        LatLng(if (merchantLat != 0.0) merchantLat else 28.2022, if (merchantLng != 0.0) merchantLng else 76.6154)
    }
    val customerLatLng = remember(customerLat, customerLng) {
        LatLng(if (customerLat != 0.0) customerLat else 28.2022, if (customerLng != 0.0) customerLng else 76.6154)
    }
    val riderLatLng = remember(riderLat, riderLng) {
        if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
            LatLng(riderLat, riderLng)
        } else null
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(riderLatLng ?: storeLatLng, 16f)
    }

    val mapUiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                rotationGesturesEnabled = true,
                tiltGesturesEnabled = true,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true
            )
        )
    }

    val mapProperties by remember {
        mutableStateOf(
            MapProperties(
                mapStyleOptions = MapStyleOptions(RIDER_LIGHT_MAP_STYLE),
                isBuildingEnabled = true,
                isIndoorEnabled = false,
                minZoomPreference = 10f,
                maxZoomPreference = 20f
            )
        )
    }

    val storeMarkerBitmap = remember(context) {
        try {
            com.google.android.gms.maps.MapsInitializer.initialize(context.applicationContext, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST, null)
            createRiderStoreBitmap(context)
        } catch (_: Exception) {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        }
    }
    val customerMarkerBitmap = remember(context) {
        try {
            com.google.android.gms.maps.MapsInitializer.initialize(context.applicationContext, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST, null)
            createRiderCustomerBitmap(context)
        } catch (_: Exception) {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
        }
    }
    val riderScooterBitmap = remember(context) {
        try {
            com.google.android.gms.maps.MapsInitializer.initialize(context.applicationContext, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST, null)
            createRiderScooterBitmap(context)
        } catch (_: Exception) {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        }
    }

    val polylinePoints = remember(waypoints, merchantLat, merchantLng, customerLat, customerLng, riderLatLng, isPhase1) {
        if (waypoints.size >= 2) {
            waypoints.map { LatLng(it.lat, it.lng) }
        } else {
            val list = mutableListOf<LatLng>()
            if (riderLatLng != null) {
                list.add(riderLatLng)
            } else {
                list.add(storeLatLng)
            }
            if (isPhase1) {
                list.add(storeLatLng)
            } else {
                list.add(customerLatLng)
            }
            list
        }
    }

    val storeMarkerState = rememberMarkerState(key = "rider_store_pos", position = storeLatLng)
    val customerMarkerState = rememberMarkerState(key = "rider_cust_pos", position = customerLatLng)
    val riderMarkerState = rememberMarkerState(key = "rider_live_pos", position = riderLatLng ?: storeLatLng)

    LaunchedEffect(storeLatLng) { storeMarkerState.position = storeLatLng }
    LaunchedEffect(customerLatLng) { customerMarkerState.position = customerLatLng }
    LaunchedEffect(riderLatLng) {
        if (riderLatLng != null) {
            riderMarkerState.position = riderLatLng
        }
    }

    // Auto-follow rider location smoothly
    LaunchedEffect(riderLatLng) {
        if (riderLatLng != null) {
            try {
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLng(riderLatLng),
                    durationMs = 600
                )
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            // 1. Store Hub
            Marker(
                state = storeMarkerState,
                icon = storeMarkerBitmap,
                title = "Dark Store Hub",
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
            )

            // 2. Customer Destination
            Marker(
                state = customerMarkerState,
                icon = customerMarkerBitmap,
                title = "Customer Doorstep",
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
            )

            // 3. Signature Active Route Polyline
            if (polylinePoints.size >= 2) {
                Polyline(
                    points = polylinePoints,
                    color = Color(0xFF10B981), // Emerald Green Navigation Ribbon
                    width = 16f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )
            }

            // 4. Rider 3D Vehicle Marker
            if (riderLatLng != null) {
                Marker(
                    state = riderMarkerState,
                    icon = riderScooterBitmap,
                    title = "My Location",
                    rotation = riderHeading ?: 0f,
                    flat = true,
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                )
            }
        }

        // Recenter FAB
        FloatingActionButton(
            onClick = {
                coroutineScope.launch {
                    try {
                        val target = riderLatLng ?: storeLatLng
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(target, 17.5f),
                            durationMs = 600
                        )
                    } catch (_: Exception) {}
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(44.dp),
            containerColor = Color.White,
            contentColor = Color(0xFF0F172A),
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Recenter",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun createRiderStoreBitmap(context: Context): BitmapDescriptor {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    canvas.drawOval(RectF(18f, 74f, 78f, 90f), shadowPaint)

    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0284C7.toInt() }
    val rect = RectF(14f, 10f, 82f, 78f)
    canvas.drawRoundRect(rect, 20f, 20f, pillPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawRoundRect(rect, 20f, 20f, borderPaint)

    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    canvas.drawRect(26f, 32f, 70f, 40f, iconPaint)
    canvas.drawRect(30f, 44f, 66f, 66f, iconPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun createRiderCustomerBitmap(context: Context): BitmapDescriptor {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    canvas.drawOval(RectF(18f, 74f, 78f, 90f), shadowPaint)

    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF97316.toInt() }
    val rect = RectF(14f, 10f, 82f, 78f)
    canvas.drawRoundRect(rect, 20f, 20f, pillPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawRoundRect(rect, 20f, 20f, borderPaint)

    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    canvas.drawRect(30f, 40f, 66f, 64f, iconPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun createRiderScooterBitmap(context: Context): BitmapDescriptor {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    canvas.drawOval(RectF(20f, 35f, 100f, 85f), shadowPaint)

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3310B981 }
    canvas.drawCircle(60f, 60f, 44f, haloPaint)

    val puckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10B981.toInt() }
    canvas.drawCircle(60f, 60f, 32f, puckPaint)

    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(60f, 60f, 32f, rimPaint)

    val riderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    canvas.drawCircle(60f, 54f, 10f, riderPaint)
    canvas.drawRoundRect(RectF(52f, 66f, 68f, 76f), 4f, 4f, riderPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
