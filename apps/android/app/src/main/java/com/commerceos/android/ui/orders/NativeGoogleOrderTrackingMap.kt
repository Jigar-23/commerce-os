package com.commerceos.android.ui.orders

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
import com.commerceos.android.location.GoogleRoutesProvider
import com.commerceos.android.location.RouteResult
import com.commerceos.android.network.ApiResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.*
import com.google.maps.android.SphericalUtil
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Zepto/Blinkit-Grade Light Map Style JSON.
 * Soft off-white land, pastel sky blue water, clean grey/white roads, and minimal POI clutter.
 */
val ZEPTO_LIGHT_MAP_STYLE = """
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
    "featureType": "administrative.land_parcel",
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#bdbdbd" }]
  },
  {
    "featureType": "poi",
    "elementType": "geometry",
    "stylers": [{ "color": "#eeeeee" }]
  },
  {
    "featureType": "poi",
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#757575" }]
  },
  {
    "featureType": "poi.park",
    "elementType": "geometry",
    "stylers": [{ "color": "#e8f5e9" }]
  },
  {
    "featureType": "poi.park",
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#9e9e9e" }]
  },
  {
    "featureType": "road",
    "elementType": "geometry",
    "stylers": [{ "color": "#ffffff" }]
  },
  {
    "featureType": "road.arterial",
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#757575" }]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry",
    "stylers": [{ "color": "#dadada" }]
  },
  {
    "featureType": "road.highway",
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#616161" }]
  },
  {
    "featureType": "road.local",
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#9e9e9e" }]
  },
  {
    "featureType": "transit.line",
    "elementType": "geometry",
    "stylers": [{ "color": "#e5e5e5" }]
  },
  {
    "featureType": "transit.station",
    "elementType": "geometry",
    "stylers": [{ "color": "#eeeeee" }]
  },
  {
    "featureType": "water",
    "elementType": "geometry",
    "stylers": [{ "color": "#c8e0f4" }]
  },
  {
    "featureType": "water",
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#9e9e9e" }]
  }
]
""".trimIndent()

/**
 * Native Google Maps Live Order Tracking View with Progressive Path Filler.
 * Real turn-by-turn road polyline from Google Directions API, two-tone path filling
 * (traveled vs remaining), smooth 3D scooter heading rotation, and dynamic viewport bounds.
 */
@Composable
fun NativeGoogleOrderTrackingMap(
    merchantLat: Double,
    merchantLng: Double,
    customerLat: Double,
    customerLng: Double,
    riderLat: Double? = null,
    riderLng: Double? = null,
    riderHeading: Float? = null,
    speedKmh: Float? = null,
    waypoints: List<MapRoutePoint> = emptyList(),
    activeStage: String = "ASSIGNING_PARTNER",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val routesProvider = remember { GoogleRoutesProvider() }

    val storeLatLng = remember(merchantLat, merchantLng) {
        LatLng(if (merchantLat != 0.0) merchantLat else 28.1970, if (merchantLng != 0.0) merchantLng else 76.6190)
    }
    val customerLatLng = remember(customerLat, customerLng) {
        LatLng(if (customerLat != 0.0) customerLat else 28.2022, if (customerLng != 0.0) customerLng else 76.6154)
    }
    val riderLatLng = remember(riderLat, riderLng) {
        if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
            LatLng(riderLat, riderLng)
        } else null
    }

    var googleRouteWaypoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeDistanceText by remember { mutableStateOf("") }
    var routeDurationText by remember { mutableStateOf("") }

    // Fetch official turn-by-turn road geometry from Google Directions API
    LaunchedEffect(storeLatLng, customerLatLng) {
        val result = routesProvider.getDrivingRoute(
            originLat = storeLatLng.latitude,
            originLng = storeLatLng.longitude,
            destLat = customerLatLng.latitude,
            destLng = customerLatLng.longitude
        )
        if (result is ApiResult.Success) {
            googleRouteWaypoints = result.data.waypoints
            routeDistanceText = result.data.distanceText
            routeDurationText = result.data.durationText
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(storeLatLng, 15f)
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
                mapStyleOptions = MapStyleOptions(ZEPTO_LIGHT_MAP_STYLE),
                isBuildingEnabled = true,
                isIndoorEnabled = false,
                minZoomPreference = 10f,
                maxZoomPreference = 20f
            )
        )
    }

    // Custom Native Marker Bitmaps
    val storeMarkerBitmap = remember(context) {
        try {
            MapsInitializer.initialize(context.applicationContext, MapsInitializer.Renderer.LATEST, null)
            createStoreMarkerBitmap(context)
        } catch (_: Exception) {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        }
    }
    val customerMarkerBitmap = remember(context) {
        try {
            MapsInitializer.initialize(context.applicationContext, MapsInitializer.Renderer.LATEST, null)
            createCustomerMarkerBitmap(context)
        } catch (_: Exception) {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
        }
    }
    val riderScooterBitmap = remember(context) {
        try {
            MapsInitializer.initialize(context.applicationContext, MapsInitializer.Renderer.LATEST, null)
            createZeptoScooterBitmap(context)
        } catch (_: Exception) {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
        }
    }

    // Full baseline route points (hugging real roads)
    val fullRoutePoints = remember(googleRouteWaypoints, waypoints, storeLatLng, customerLatLng) {
        when {
            googleRouteWaypoints.size >= 2 -> googleRouteWaypoints
            waypoints.size >= 2 -> waypoints.map { LatLng(it.lat, it.lng) }
            else -> listOf(storeLatLng, customerLatLng)
        }
    }

    // Two-Tone Path Filler Split: Traveled (Behind Rider) vs Remaining (Ahead of Rider)
    val (traveledPath, remainingPath) = remember(fullRoutePoints, riderLatLng) {
        if (riderLatLng == null || fullRoutePoints.size < 2) {
            Pair(emptyList<LatLng>(), fullRoutePoints)
        } else {
            // Find closest index on route polyline
            var minIndex = 0
            var minDistance = Double.MAX_VALUE
            for (i in fullRoutePoints.indices) {
                val pt = fullRoutePoints[i]
                val dist = (pt.latitude - riderLatLng.latitude).pow(2.0) + (pt.longitude - riderLatLng.longitude).pow(2.0)
                if (dist < minDistance) {
                    minDistance = dist
                    minIndex = i
                }
            }

            val traveled = mutableListOf<LatLng>()
            for (i in 0..minIndex) {
                traveled.add(fullRoutePoints[i])
            }
            traveled.add(riderLatLng)

            val remaining = mutableListOf<LatLng>()
            remaining.add(riderLatLng)
            for (i in (minIndex + 1) until fullRoutePoints.size) {
                remaining.add(fullRoutePoints[i])
            }

            Pair(traveled, remaining)
        }
    }

    // Dynamic Scooter Bearing (Angle towards next road corner)
    val calculatedHeading = remember(riderHeading, remainingPath, riderLatLng) {
        when {
            riderHeading != null && riderHeading != 0f -> riderHeading
            remainingPath.size >= 2 && riderLatLng != null -> {
                val nextPoint = remainingPath[1]
                SphericalUtil.computeHeading(riderLatLng, nextPoint).toFloat()
            }
            else -> 0f
        }
    }

    // Smooth Auto-Framing Bounds: Focus directly between Rider and Customer when in transit
    LaunchedEffect(storeLatLng, customerLatLng, riderLatLng) {
        try {
            val builder = LatLngBounds.builder()
            builder.include(customerLatLng)
            if (riderLatLng != null) {
                builder.include(riderLatLng)
            } else {
                builder.include(storeLatLng)
            }
            val bounds = builder.build()
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngBounds(bounds, 140),
                durationMs = 800
            )
        } catch (_: Exception) {}
    }

    val storeMarkerState = rememberMarkerState(key = "track_store_pos", position = storeLatLng)
    val customerMarkerState = rememberMarkerState(key = "track_cust_pos", position = customerLatLng)
    val riderMarkerState = rememberMarkerState(key = "track_rider_pos", position = riderLatLng ?: storeLatLng)

    LaunchedEffect(storeLatLng) { storeMarkerState.position = storeLatLng }
    LaunchedEffect(customerLatLng) { customerMarkerState.position = customerLatLng }
    LaunchedEffect(riderLatLng) {
        if (riderLatLng != null) {
            riderMarkerState.position = riderLatLng
        }
    }

    // The single active delivery route line between Rider and Customer
    val activeDeliveryRoute = remember(remainingPath, fullRoutePoints, riderLatLng) {
        if (riderLatLng != null && remainingPath.size >= 2) {
            remainingPath
        } else {
            fullRoutePoints
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            // 1. Dark Store Hub Pin (Only shown before rider pickup)
            if (riderLatLng == null) {
                Marker(
                    state = storeMarkerState,
                    icon = storeMarkerBitmap,
                    title = "Dark Store Hub",
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                )
            }

            // 2. Customer Destination Pin (Destination)
            Marker(
                state = customerMarkerState,
                icon = customerMarkerBitmap,
                title = "Delivery Location",
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
            )

            // 3. Single High-Visibility Active Route Ribbon (Rider ➔ Customer)
            if (activeDeliveryRoute.size >= 2) {
                Polyline(
                    points = activeDeliveryRoute,
                    color = Color(0xFF7C3AED), // Signature Zepto Purple Navigation Ribbon
                    width = 16f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )
            }

            // 4. Native 3D Isometric Delivery Scooter (Real Turn-by-Turn Bearing)
            if (riderLatLng != null) {
                Marker(
                    state = riderMarkerState,
                    icon = riderScooterBitmap,
                    title = "Delivery Partner",
                    rotation = calculatedHeading,
                    flat = true,
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                )
            }
        }

        // Floating Recenter Crosshair Button (Top Right)
        FloatingActionButton(
            onClick = {
                coroutineScope.launch {
                    try {
                        val target = riderLatLng ?: storeLatLng
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(target, 17f),
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
                tint = Color(0xFF7C3AED),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Generates high-res 3D Store Hub Bitmap Descriptor.
 */
private fun createStoreMarkerBitmap(context: Context): BitmapDescriptor {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40000000
    }
    canvas.drawOval(RectF(18f, 74f, 78f, 90f), shadowPaint)

    // Pill background
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0284C7.toInt()
    }
    val rect = RectF(14f, 10f, 82f, 78f)
    canvas.drawRoundRect(rect, 20f, 20f, pillPaint)

    // White border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawRoundRect(rect, 20f, 20f, borderPaint)

    // Store Roof Icon
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRect(26f, 32f, 70f, 40f, iconPaint)
    canvas.drawRect(30f, 44f, 66f, 66f, iconPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * Generates high-res 3D Customer Destination Bitmap Descriptor.
 */
private fun createCustomerMarkerBitmap(context: Context): BitmapDescriptor {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40000000
    }
    canvas.drawOval(RectF(18f, 74f, 78f, 90f), shadowPaint)

    // Pill background
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF97316.toInt()
    }
    val rect = RectF(14f, 10f, 82f, 78f)
    canvas.drawRoundRect(rect, 20f, 20f, pillPaint)

    // White border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawRoundRect(rect, 20f, 20f, borderPaint)

    // House Icon
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRect(30f, 40f, 66f, 64f, iconPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * Generates Zepto-Style 3D Isometric Delivery Scooter Bitmap Descriptor.
 */
private fun createZeptoScooterBitmap(context: Context): BitmapDescriptor {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Ground Shadow Disk
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
    }
    canvas.drawOval(RectF(20f, 35f, 100f, 85f), shadowPaint)

    // Outer Radar Halo
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x337C3AED
    }
    canvas.drawCircle(60f, 60f, 44f, haloPaint)

    // Scooter Body Puck
    val puckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C3AED.toInt() // Zepto Violet
    }
    canvas.drawCircle(60f, 60f, 32f, puckPaint)

    // White Outer Rim
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(60f, 60f, 32f, rimPaint)

    // Rider Helmet & Scooter Handle (Clean Geometry)
    val riderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
    }
    canvas.drawCircle(60f, 54f, 10f, riderPaint) // Helmet
    canvas.drawRoundRect(RectF(52f, 66f, 68f, 76f), 4f, 4f, riderPaint) // Backpack/Chassis

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
