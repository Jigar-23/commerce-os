package com.commerceos.android.ui.orders

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

data class MapRoutePoint(val lat: Double, val lng: Double)

/**
 * Hardened Dark Cartography Map Engine for Customer Tracking with Monotonic GPS Smoothing,
 * Clean Quick-Commerce Branded Vehicle Marker, and Zero Artificial Coordinates.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ZomatoDarkMapView(
    merchantLat: Double,
    merchantLng: Double,
    customerLat: Double,
    customerLng: Double,
    riderLat: Double? = null,
    riderLng: Double? = null,
    riderHeading: Float? = null,
    waypoints: List<MapRoutePoint> = emptyList(),
    isStale: Boolean = false,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    val hasRiderLocation = riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0

    val mapHtml = remember {
        generateHardenedDarkMapHtml(
            merchantLat = merchantLat,
            merchantLng = merchantLng,
            customerLat = customerLat,
            customerLng = customerLng
        )
    }

    LaunchedEffect(riderLat, riderLng, riderHeading, isStale, waypoints, isMapLoaded) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (!isMapLoaded) return@LaunchedEffect

        val jsCall = buildUpdateScript(
            merchantLat = merchantLat,
            merchantLng = merchantLng,
            customerLat = customerLat,
            customerLng = customerLng,
            riderLat = riderLat,
            riderLng = riderLng,
            riderHeading = riderHeading,
            isStale = isStale,
            waypoints = waypoints
        )
        webView.evaluateJavascript(jsCall, null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B1120))) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(0xFF0B1120.toInt())

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoaded = true
                            val initialSync = buildUpdateScript(
                                merchantLat = merchantLat,
                                merchantLng = merchantLng,
                                customerLat = customerLat,
                                customerLng = customerLng,
                                riderLat = riderLat,
                                riderLng = riderLng,
                                riderHeading = riderHeading,
                                isStale = isStale,
                                waypoints = waypoints
                            )
                            view?.evaluateJavascript(initialSync, null)
                        }
                    }

                    loadDataWithBaseURL("https://commerceos.local", mapHtml, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Customer Status Overlay (Simple static badge, zero loading spinners)
        if (!hasRiderLocation) {
            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF38BDF8), CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Finding a delivery partner", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        } else if (isStale) {
            Surface(
                color = Color(0xFF78350F).copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Updating rider location…",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFBBF24),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Recenter FAB
        FloatingActionButton(
            onClick = {
                webViewRef?.evaluateJavascript("recenterMap();", null)
            },
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF38BDF8),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(40.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Recenter", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
        }
    }
}

private fun buildUpdateScript(
    merchantLat: Double,
    merchantLng: Double,
    customerLat: Double,
    customerLng: Double,
    riderLat: Double?,
    riderLng: Double?,
    riderHeading: Float?,
    isStale: Boolean,
    waypoints: List<MapRoutePoint>
): String {
    val wpArray = JSONArray()
    waypoints.forEach {
        val obj = JSONObject()
        obj.put("lat", it.lat)
        obj.put("lng", it.lng)
        wpArray.put(obj)
    }

    val riderObj = if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
        val ro = JSONObject()
        ro.put("lat", riderLat)
        ro.put("lng", riderLng)
        ro.put("heading", riderHeading ?: JSONObject.NULL)
        ro.put("isStale", isStale)
        ro.put("timestamp", System.currentTimeMillis())
        ro
    } else {
        JSONObject.NULL
    }

    return "updateMapData($merchantLat, $merchantLng, $customerLat, $customerLng, $riderObj, $wpArray);"
}

private fun generateHardenedDarkMapHtml(
    merchantLat: Double,
    merchantLng: Double,
    customerLat: Double,
    customerLng: Double
): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0B1120; font-family: -apple-system, Roboto, sans-serif; }
        .leaflet-container { background: #0B1120; }
        
        .pin-wrapper {
            display: flex;
            flex-direction: column;
            align-items: center;
            width: 44px;
            height: 52px;
        }
        .pin-head {
            border-radius: 50%;
            width: 30px;
            height: 30px;
            display: flex;
            align-items: center;
            justify-content: center;
            position: relative;
            z-index: 2;
        }
        .store-head {
            background: #0284C7;
            border: 2px solid #38BDF8;
            box-shadow: 0 4px 12px rgba(2, 132, 199, 0.8);
        }
        .store-head svg { width: 16px; height: 16px; fill: white; }
        .customer-head {
            background: #10B981;
            border: 2px solid #6EE7B7;
            box-shadow: 0 4px 12px rgba(16, 185, 129, 0.8);
        }
        .customer-head svg { width: 16px; height: 16px; fill: white; }
        .pin-needle {
            width: 0;
            height: 0;
            border-left: 5px solid transparent;
            border-right: 5px solid transparent;
            margin-top: -2px;
            position: relative;
            z-index: 1;
        }
        .store-needle { border-top: 8px solid #0284C7; }
        .customer-needle { border-top: 8px solid #10B981; }
        .pin-dot {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: #38BDF8;
            border: 1px solid #0F172A;
            margin-top: -2px;
        }
        .customer-dot {
            background: #6EE7B7;
        }
        
        .biker-container {
            width: 34px;
            height: 34px;
            position: relative;
            transition: transform 0.4s cubic-bezier(0.25, 1, 0.5, 1);
        }
        .biker-pulse {
            position: absolute;
            width: 34px;
            height: 34px;
            border-radius: 50%;
            background: rgba(245, 158, 11, 0.25);
            animation: pulse 2.2s infinite;
        }
        .biker-core {
            position: absolute;
            top: 2px;
            left: 2px;
            width: 30px;
            height: 30px;
            background: #F59E0B;
            border: 2px solid #0F172A;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 2px 8px rgba(0,0,0,0.5);
        }
        .biker-core svg {
            width: 16px;
            height: 16px;
            fill: #0F172A;
        }
        @keyframes pulse {
            0% { transform: scale(0.9); opacity: 0.8; }
            70% { transform: scale(1.6); opacity: 0; }
            100% { transform: scale(1.6); opacity: 0; }
        }
    </style>
</head>
<body>
<div id="map"></div>
<script>
    var map = L.map('map', {
        zoomControl: false,
        attributionControl: false
    }).setView([$merchantLat, $merchantLng], 14);

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        maxZoom: 19,
        subdomains: 'abcd'
    }).addTo(map);

    var storeMarker = null;
    var customerMarker = null;
    var riderMarker = null;
    var routePolyline = null;
    var boundsGroup = [];
    var lastAcceptedTimestamp = 0;
    var initialFitted = false;

    var animFrame = null;
    function interpolateMarker(marker, startPos, endPos, durationMs) {
        if (animFrame) {
            cancelAnimationFrame(animFrame);
            animFrame = null;
        }
        var startTime = performance.now();
        function step(now) {
            var elapsed = now - startTime;
            var t = Math.min(1, elapsed / durationMs);
            var ease = 1 - Math.pow(1 - t, 3);
            var curLat = startPos[0] + (endPos[0] - startPos[0]) * ease;
            var curLng = startPos[1] + (endPos[1] - startPos[1]) * ease;
            marker.setLatLng([curLat, curLng]);
            if (t < 1) {
                animFrame = requestAnimationFrame(step);
            } else {
                animFrame = null;
            }
        }
        animFrame = requestAnimationFrame(step);
    }

    var storeHtml = '<div class="pin-wrapper"><div class="pin-head store-head"><svg viewBox="0 0 24 24"><path d="M20 4H4v2h16V4zm1 10v-2l-1-5H4l-1 5v2h1v6h10v-6h4v6h2v-6h1zm-9 4H6v-4h6v4z"/></svg></div><div class="pin-needle store-needle"></div><div class="pin-dot"></div></div>';
    var customerHtml = '<div class="pin-wrapper"><div class="pin-head customer-head"><svg viewBox="0 0 24 24"><path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/></svg></div><div class="pin-needle customer-needle"></div><div class="pin-dot customer-dot"></div></div>';
    var bikeSvg = '<svg viewBox="0 0 24 24"><path d="M15.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM5 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5zm14-8.5c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5zm-8.2-7.5l-2.4-4H5v2h2.2l1.6 2.7c-.8.8-1.3 1.8-1.5 3h2.1c.2-.7.6-1.3 1.1-1.8l1.7 2.1h3.7v-2h-2.5l-1.9-2.4.9-2.6 1.8 1.4v2.6h2v-3.7l-2.8-2.2c-.3-.2-.7-.3-1.1-.3-.4 0-.8.2-1.1.5l-1.6 2.4z"/></svg>';

    var autoFollow = true;
    map.on('dragstart', function() { autoFollow = false; });
    map.on('zoomstart', function(e) { if (e && e.originalEvent) autoFollow = false; });

    function updateMapData(mLat, mLng, cLat, cLng, rider, waypoints) {
        boundsGroup = [];

        if (mLat && mLng) {
            if (!storeMarker) {
                var icon = L.divIcon({ className: '', html: storeHtml, iconSize: [44, 52], iconAnchor: [22, 52] });
                storeMarker = L.marker([mLat, mLng], { icon: icon }).addTo(map);
            } else {
                storeMarker.setLatLng([mLat, mLng]);
            }
            boundsGroup.push([mLat, mLng]);
        }

        if (cLat && cLng) {
            if (!customerMarker) {
                var icon = L.divIcon({ className: '', html: customerHtml, iconSize: [44, 52], iconAnchor: [22, 52] });
                customerMarker = L.marker([cLat, cLng], { icon: icon }).addTo(map);
            } else {
                customerMarker.setLatLng([cLat, cLng]);
            }
            boundsGroup.push([cLat, cLng]);
        }

        if (rider && rider.lat && rider.lng) {
            var ts = rider.timestamp || Date.now();
            if (ts >= lastAcceptedTimestamp) {
                lastAcceptedTimestamp = ts;
                var rot = (rider.heading != null) ? rider.heading : 0;
                var markerHtml = '<div class="biker-container" style="transform: rotate(' + rot + 'deg);">' +
                                 (rider.isStale ? '' : '<div class="biker-pulse"></div>') +
                                 '<div class="biker-core">' + bikeSvg + '</div>' +
                                 '</div>';
                var bikerIcon = L.divIcon({ className: '', html: markerHtml, iconSize: [34, 34], iconAnchor: [17, 17] });

                if (!riderMarker) {
                    riderMarker = L.marker([rider.lat, rider.lng], { icon: bikerIcon }).addTo(map);
                } else {
                    var prevLatLng = riderMarker.getLatLng();
                    riderMarker.setIcon(bikerIcon);
                    if (rider.isStale) {
                        if (animFrame) {
                            cancelAnimationFrame(animFrame);
                            animFrame = null;
                        }
                        riderMarker.setLatLng([rider.lat, rider.lng]);
                    } else {
                        interpolateMarker(riderMarker, [prevLatLng.lat, prevLatLng.lng], [rider.lat, rider.lng], 900);
                    }
                }
                boundsGroup.push([rider.lat, rider.lng]);

                if (autoFollow && !rider.isStale) {
                    map.panTo([rider.lat, rider.lng], { animate: true, duration: 0.8 });
                }
            }
        } else {
            if (riderMarker) {
                if (animFrame) {
                    cancelAnimationFrame(animFrame);
                    animFrame = null;
                }
                map.removeLayer(riderMarker);
                riderMarker = null;
            }
        }

        if (waypoints && waypoints.length > 0) {
            var latLngs = waypoints.map(function(pt) { return [pt.lat, pt.lng]; });
            if (!routePolyline) {
                routePolyline = L.polyline(latLngs, {
                    color: '#38BDF8',
                    weight: 4,
                    opacity: 0.8,
                    dashArray: '6, 8',
                    lineCap: 'round',
                    lineJoin: 'round'
                }).addTo(map);
            } else {
                routePolyline.setLatLngs(latLngs);
            }
        } else {
            if (routePolyline) {
                map.removeLayer(routePolyline);
                routePolyline = null;
            }
        }

        if (!initialFitted && boundsGroup.length >= 2) {
            initialFitted = true;
            map.fitBounds(L.latLngBounds(boundsGroup), { padding: [50, 50], maxZoom: 16 });
        }
    }

    function recenterMap() {
        if (boundsGroup.length > 0) {
            map.fitBounds(L.latLngBounds(boundsGroup), { padding: [50, 50], maxZoom: 16 });
        }
    }
</script>
</body>
</html>
    """.trimIndent()
}
