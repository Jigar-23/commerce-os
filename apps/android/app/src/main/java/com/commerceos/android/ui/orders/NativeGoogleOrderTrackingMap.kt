package com.commerceos.android.ui.orders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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
    val stageUpper = activeStage.uppercase()
    val isPostPickup = stageUpper in listOf(
        "PICKED_UP", "OUT_FOR_DELIVERY", "EN_ROUTE_CUSTOMER", "REACHING_YOU",
        "NEARBY", "ARRIVED_CUSTOMER", "AT_DOORSTEP", "HANDOFF_STARTED"
    )
    val isHeadingToStore = stageUpper in listOf(
        "HEADING_TO_STORE", "ACCEPTED", "EN_ROUTE_PICKUP", "EN_ROUTE_STORE",
        "ARRIVED_PICKUP", "ARRIVED_STORE", "AT_STORE", "PACKED", "SELLER_ACCEPTED", "ASSIGNED"
    )
    val riderLatLng = remember(riderLat, riderLng, storeLatLng, isPostPickup) {
        if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
            LatLng(riderLat, riderLng)
        } else if (isPostPickup) {
            storeLatLng
        } else {
            null
        }
    }

    // Stage 1 (Order Placed): Strictly NO polyline while rider is not active/assigned
    val isOrderPlaced = !isPostPickup && !isHeadingToStore

    var googleRouteWaypoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeDistanceText by remember { mutableStateOf("") }
    var routeDurationText by remember { mutableStateOf("") }
    var lastRoutedOrigin by remember { mutableStateOf<LatLng?>(null) }
    var lastRoutedDest by remember { mutableStateOf<LatLng?>(null) }
    var lastActiveStage by remember { mutableStateOf<String?>(null) }

    val targetDest = when {
        isHeadingToStore && riderLatLng != null -> storeLatLng
        isPostPickup && riderLatLng != null -> customerLatLng
        else -> null
    }

    // Fetch turn-by-turn road geometry dynamically depending on active delivery stage
    LaunchedEffect(isOrderPlaced, isHeadingToStore, isPostPickup, riderLatLng, targetDest, activeStage) {
        if (isOrderPlaced || riderLatLng == null || targetDest == null) {
            googleRouteWaypoints = emptyList()
            routeDistanceText = ""
            routeDurationText = ""
            lastRoutedOrigin = null
            lastRoutedDest = null
            lastActiveStage = activeStage
            return@LaunchedEffect
        }

        val lastOrig = lastRoutedOrigin
        val lastDst = lastRoutedDest
        val stageChanged = activeStage != lastActiveStage
        val destChanged = lastDst != targetDest
        val distMoved = if (lastOrig != null) SphericalUtil.computeDistanceBetween(lastOrig, riderLatLng) else 999.0

        if (googleRouteWaypoints.isEmpty() || stageChanged || destChanged || distMoved > 75.0) {
            val result = routesProvider.getDrivingRoute(
                originLat = riderLatLng.latitude,
                originLng = riderLatLng.longitude,
                destLat = targetDest.latitude,
                destLng = targetDest.longitude
            )
            if (result is ApiResult.Success) {
                googleRouteWaypoints = result.data.waypoints
                routeDistanceText = result.data.distanceText
                routeDurationText = result.data.durationText
                lastRoutedOrigin = riderLatLng
                lastRoutedDest = targetDest
                lastActiveStage = activeStage
            }
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
    val fullRoutePoints = remember(googleRouteWaypoints, waypoints, isOrderPlaced) {
        if (isOrderPlaced) {
            emptyList()
        } else when {
            googleRouteWaypoints.size >= 2 -> googleRouteWaypoints
            waypoints.size >= 2 -> waypoints.map { LatLng(it.lat, it.lng) }
            else -> emptyList()
        }
    }

    // Two-Tone Path Filler Split: Traveled (Behind Rider) vs Remaining (Ahead of Rider)
    val (traveledPath, remainingPath) = remember(fullRoutePoints, riderLatLng, isOrderPlaced) {
        if (isOrderPlaced || riderLatLng == null || fullRoutePoints.size < 2) {
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

    // Smooth Auto-Framing Bounds: Always include Store and Customer pins, plus Rider when active
    LaunchedEffect(storeLatLng, customerLatLng, riderLatLng, isOrderPlaced) {
        try {
            val builder = LatLngBounds.builder()
            builder.include(storeLatLng)
            builder.include(customerLatLng)
            if (!isOrderPlaced && riderLatLng != null) {
                builder.include(riderLatLng)
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

    // The single active delivery route line
    // When order is placed: strictly emptyList()
    // When heading to store: connects rider to store
    // When out for delivery: connects rider to customer
    val activeDeliveryRoute = remember(remainingPath, fullRoutePoints, riderLatLng, targetDest, isOrderPlaced) {
        if (isOrderPlaced) {
            emptyList()
        } else if (remainingPath.size >= 2) {
            remainingPath
        } else if (fullRoutePoints.size >= 2) {
            fullRoutePoints
        } else {
            // Strictly road geometry only. Never draw a straight line between two far coordinates.
            emptyList()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            // 1. Dark Store Hub Pin (Store Pin is ALWAYS visible unconditionally)
            Marker(
                state = storeMarkerState,
                icon = storeMarkerBitmap,
                title = "Dark Store Hub",
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.90f)
            )

            // 2. Customer Destination Pin (Home Pin is ALWAYS visible unconditionally)
            Marker(
                state = customerMarkerState,
                icon = customerMarkerBitmap,
                title = "Delivery Location",
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.90f)
            )

            // 3. Single High-Visibility Active Route Ribbon (Blinkit / Zepto Style)
            // NO polyline when order is placed.
            // When rider is en route to store: Polyline ONLY between Rider and Store.
            // When rider confirms pickup: Polyline ONLY between Rider and Customer.
            if (!isOrderPlaced && activeDeliveryRoute.size >= 2) {
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
            if (!isOrderPlaced && riderLatLng != null) {
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
 * Generates high-res industry-standard Teardrop Store Pin Bitmap Descriptor.
 */
private fun createStoreMarkerBitmap(context: Context): BitmapDescriptor {
    val width = 100
    val height = 120
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. Ground contact shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x35000000
    }
    canvas.drawOval(RectF(32f, 106f, 68f, 118f), shadowPaint)

    // 2. Teardrop Pin Silhouette pointing down to (50, 108)
    val pinPath = Path().apply {
        moveTo(50f, 108f)
        cubicTo(42f, 94f, 16f, 66f, 16f, 42f)
        arcTo(RectF(16f, 8f, 84f, 76f), 180f, 180f, false)
        cubicTo(84f, 66f, 58f, 94f, 50f, 108f)
        close()
    }

    // Pin Body Fill - Deep Store Azure Blue
    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0284C7.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawPath(pinPath, pinPaint)

    // Pin Crisp White Outer Stroke
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawPath(pinPath, borderPaint)

    // 3. Inner White High-Contrast Badge Disc
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(50f, 42f, 24f, discPaint)

    // 4. Industry-Standard Storefront Vector Emblem
    val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0369A1.toInt()
        style = Paint.Style.FILL
    }

    // Store Pediment / Top Bar
    canvas.drawRoundRect(RectF(34f, 26f, 66f, 29f), 1.5f, 1.5f, emblemPaint)

    // Store Striped Awning Canopy
    val awningPath = Path().apply {
        moveTo(34f, 29f)
        lineTo(66f, 29f)
        lineTo(68f, 38f)
        lineTo(32f, 38f)
        close()
    }
    canvas.drawPath(awningPath, emblemPaint)

    // Awning white stripe accents
    val awningStripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
    }
    canvas.drawLine(41f, 29f, 41f, 38f, awningStripePaint)
    canvas.drawLine(50f, 29f, 50f, 38f, awningStripePaint)
    canvas.drawLine(59f, 29f, 59f, 38f, awningStripePaint)

    // Store Windows & Entrance
    canvas.drawRoundRect(RectF(35f, 41f, 43f, 49f), 1.5f, 1.5f, emblemPaint)
    canvas.drawRoundRect(RectF(57f, 41f, 65f, 49f), 1.5f, 1.5f, emblemPaint)
    canvas.drawRoundRect(RectF(46f, 41f, 54f, 54f), 2.5f, 2.5f, emblemPaint)

    // Ground line
    canvas.drawRoundRect(RectF(32f, 54f, 68f, 56.5f), 1.2f, 1.2f, emblemPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * Generates high-res industry-standard Teardrop Customer Home Pin Bitmap Descriptor.
 */
private fun createCustomerMarkerBitmap(context: Context): BitmapDescriptor {
    val width = 100
    val height = 120
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. Ground contact shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x35000000
    }
    canvas.drawOval(RectF(32f, 106f, 68f, 118f), shadowPaint)

    // 2. Teardrop Pin Silhouette pointing down to (50, 108)
    val pinPath = Path().apply {
        moveTo(50f, 108f)
        cubicTo(42f, 94f, 16f, 66f, 16f, 42f)
        arcTo(RectF(16f, 8f, 84f, 76f), 180f, 180f, false)
        cubicTo(84f, 66f, 58f, 94f, 50f, 108f)
        close()
    }

    // Pin Body Fill - Warm Delivery Coral / Amber
    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEA580C.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawPath(pinPath, pinPaint)

    // Pin Crisp White Outer Stroke
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawPath(pinPath, borderPaint)

    // 3. Inner White High-Contrast Badge Disc
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(50f, 42f, 24f, discPaint)

    // 4. Industry-Standard Home / House Vector Emblem
    val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC2410C.toInt()
        style = Paint.Style.FILL
    }

    // Chimney
    canvas.drawRoundRect(RectF(57f, 26f, 61f, 34f), 1f, 1f, emblemPaint)

    // Pitched Gable Roof with overhanging eaves
    val roofPath = Path().apply {
        moveTo(50f, 24f)
        lineTo(33f, 37f)
        lineTo(36f, 40f)
        lineTo(50f, 29f)
        lineTo(64f, 40f)
        lineTo(67f, 37f)
        close()
    }
    canvas.drawPath(roofPath, emblemPaint)

    // House Walls
    canvas.drawRoundRect(RectF(37f, 38f, 63f, 55f), 2f, 2f, emblemPaint)

    // Front Door Cutout (White)
    val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(47f, 45f, 53f, 55f), 2.5f, 2.5f, cutoutPaint)

    // Window Cutout (White)
    canvas.drawRoundRect(RectF(40f, 42f, 45f, 47f), 1.5f, 1.5f, cutoutPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * Generates industry-standard 2.5D Delivery Scooter Bitmap with Rider, Helmet, and Delivery Bag.
 */
private fun createZeptoScooterBitmap(context: Context): BitmapDescriptor {
    val size = 140
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. Forward Headlight Beam (Glowing cone projecting forward along vehicle heading)
    val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            70f, 46f, 70f, 4f,
            0x4DFBBF24.toInt(),
            0x00FBBF24.toInt(),
            Shader.TileMode.CLAMP
        )
        style = Paint.Style.FILL
    }
    val beamPath = Path().apply {
        moveTo(70f, 46f)
        lineTo(44f, 6f)
        lineTo(96f, 6f)
        close()
    }
    canvas.drawPath(beamPath, beamPaint)

    // 2. Ground Drop Shadow under vehicle
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
    }
    canvas.drawOval(RectF(48f, 36f, 92f, 108f), shadowPaint)

    // 3. Navigation Puck Outer Radar Ring
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x267C3AED
    }
    canvas.drawCircle(70f, 70f, 46f, haloPaint)

    // 4. White High-Contrast Outer Disc
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(70f, 70f, 34f, discPaint)

    // Inner Dark Navy Core Disc for sharp contrast
    val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E1B4B.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(70f, 70f, 31f, basePaint)

    // 5. Front Tire & Mudguard
    val tirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F172A.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(66f, 36f, 74f, 48f), 3f, 3f, tirePaint)

    // 6. Handlebars & Side Mirrors
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE2E8F0.toInt()
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    canvas.drawLine(52f, 48f, 88f, 48f, barPaint)

    val mirrorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(51f, 46f, 2.5f, mirrorPaint)
    canvas.drawCircle(89f, 46f, 2.5f, mirrorPaint)

    // 7. Scooter Front Apron / Cowl
    val apronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C3AED.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(62f, 46f, 78f, 58f), 4f, 4f, apronPaint)

    // Headlight bulb
    val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFDE047.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(70f, 47f, 3f, lightPaint)

    // 8. Rider Torso & Shoulders
    val riderBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF334155.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(58f, 62f, 82f, 74f), 5f, 5f, riderBodyPaint)

    // 9. Rider Helmet (with dark visor)
    val helmetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(70f, 60f, 9f, helmetPaint)

    val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F172A.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(64f, 53f, 76f, 59f), 2f, 2f, visorPaint)

    // 10. Thermal Delivery Backpack / Box
    val deliveryBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C3AED.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(58f, 74f, 82f, 94f), 4f, 4f, deliveryBoxPaint)

    // Reflective Safety Strip
    val reflectivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF1F5F9.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawLine(60f, 84f, 80f, 84f, reflectivePaint)

    // 11. Rear Tire
    canvas.drawRoundRect(RectF(66f, 93f, 74f, 104f), 3f, 3f, tirePaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
