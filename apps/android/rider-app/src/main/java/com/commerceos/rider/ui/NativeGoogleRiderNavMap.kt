package com.commerceos.rider.ui

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
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.90f)
            )

            // 2. Customer Destination
            Marker(
                state = customerMarkerState,
                icon = customerMarkerBitmap,
                title = "Customer Doorstep",
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.90f)
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

/**
 * Generates high-res industry-standard Teardrop Store Pin Bitmap Descriptor.
 */
private fun createRiderStoreBitmap(context: Context): BitmapDescriptor {
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
private fun createRiderCustomerBitmap(context: Context): BitmapDescriptor {
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
private fun createRiderScooterBitmap(context: Context): BitmapDescriptor {
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

    // 3. Navigation Puck Outer Radar Ring (Emerald for Rider Nav)
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2610B981
    }
    canvas.drawCircle(70f, 70f, 46f, haloPaint)

    // 4. White High-Contrast Outer Disc
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(70f, 70f, 34f, discPaint)

    // Inner Dark Slate Core Disc for sharp contrast
    val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F172A.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(70f, 70f, 31f, basePaint)

    // 5. Front Tire & Mudguard
    val tirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF020617.toInt()
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

    // 7. Scooter Front Apron / Cowl (Emerald)
    val apronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF10B981.toInt()
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
        color = 0xFF1E293B.toInt()
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

    // 10. Thermal Delivery Backpack / Box (Emerald)
    val deliveryBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF10B981.toInt()
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
