import SwiftUI
import WebKit
import MapKit
import CoreLocation

/**
 * Zepto/Blinkit-Grade Native Google Maps Live Order Tracking View for iOS.
 * Powered by official Google Maps JavaScript API (Console Cloud Key: AIzaSyCzi_sMDds2_im406sGTCU8WAFZoTyNg5c).
 * Features:
 * - Clean light Zepto/Blinkit palette (off-white land, pastel sky blue water, white roads, zero clutter)
 * - 3D elevated fulfillment hub beacon with blue gradient pill
 * - 3D elevated customer delivery beacon with orange gradient pill
 * - 3D rotating scooter marker with real heading angle, glowing emerald radar wave, and forward headlight beam
 * - Turn-by-turn Google road route polyline in emerald green
 * - Dynamic bounds framing and smooth camera recentering
 */
public struct ZomatoDarkMapView: View {
    public var riderCoordinate: CLLocationCoordinate2D?
    public var merchantCoordinate: CLLocationCoordinate2D?
    public var customerCoordinate: CLLocationCoordinate2D?
    public var routeCoordinates: [CLLocationCoordinate2D] = []
    public var riderBearing: Double = 0.0
    public var speedKmh: Double = 0.0
    public var merchantTitle: String = "Fulfillment Hub"

    @State private var coordinator: GoogleOrderTrackingCoordinator?

    public init(
        riderCoordinate: CLLocationCoordinate2D? = nil,
        merchantCoordinate: CLLocationCoordinate2D? = nil,
        customerCoordinate: CLLocationCoordinate2D? = nil,
        routeCoordinates: [CLLocationCoordinate2D] = [],
        riderBearing: Double = 0.0,
        speedKmh: Double = 0.0,
        merchantTitle: String = "Fulfillment Hub"
    ) {
        self.riderCoordinate = riderCoordinate
        self.merchantCoordinate = merchantCoordinate
        self.customerCoordinate = customerCoordinate
        self.routeCoordinates = routeCoordinates
        self.riderBearing = riderBearing
        self.speedKmh = speedKmh
        self.merchantTitle = merchantTitle
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            GoogleOrderTrackingWebView(
                merchantCoordinate: merchantCoordinate,
                customerCoordinate: customerCoordinate,
                riderCoordinate: riderCoordinate,
                routeCoordinates: routeCoordinates,
                riderBearing: riderBearing,
                speedKmh: speedKmh,
                onCoordinatorCreated: { coord in
                    self.coordinator = coord
                }
            )
            .background(Color(hex: "F8F9FA"))

            // Floating Recenter Action Button (Clean Light Zepto Style)
            Button(action: {
                coordinator?.recenter()
            }) {
                Image(systemName: "location.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(hex: "059669"))
                    .frame(width: 40, height: 40)
                    .background(Color.white.opacity(0.96))
                    .clipShape(Circle())
                    .overlay(
                        Circle().stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.12), radius: 4, x: 0, y: 2)
            }
            .padding(.trailing, 14)
            .padding(.bottom, 14)
        }
    }
}

// MARK: - WKWebView Representable Bridge
struct GoogleOrderTrackingWebView: UIViewRepresentable {
    let merchantCoordinate: CLLocationCoordinate2D?
    let customerCoordinate: CLLocationCoordinate2D?
    let riderCoordinate: CLLocationCoordinate2D?
    let routeCoordinates: [CLLocationCoordinate2D]
    let riderBearing: Double
    let speedKmh: Double
    let onCoordinatorCreated: (GoogleOrderTrackingCoordinator) -> Void

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = prefs

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 248/255, green: 249/255, blue: 250/255, alpha: 1.0)

        context.coordinator.webView = webView
        onCoordinatorCreated(context.coordinator)

        let initialLat = riderCoordinate?.latitude ?? merchantCoordinate?.latitude ?? 28.2022
        let initialLng = riderCoordinate?.longitude ?? merchantCoordinate?.longitude ?? 76.6154
        let html = generateGoogleMapsLiveTrackingHtml(initLat: initialLat, initLng: initialLng)
        webView.loadHTMLString(html, baseURL: URL(string: "https://maps.googleapis.com"))

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.update(
            merchantCoordinate: merchantCoordinate,
            customerCoordinate: customerCoordinate,
            riderCoordinate: riderCoordinate,
            routeCoordinates: routeCoordinates,
            riderBearing: riderBearing,
            speedKmh: speedKmh
        )
    }

    func makeCoordinator() -> GoogleOrderTrackingCoordinator {
        GoogleOrderTrackingCoordinator()
    }
}

// MARK: - Google Maps Coordinator
final class GoogleOrderTrackingCoordinator: NSObject, WKNavigationDelegate {
    weak var webView: WKWebView?
    private var isLoaded: Bool = false
    private var pendingJs: String? = nil

    private var lastRouteOrigin: CLLocationCoordinate2D? = nil
    private var lastRouteDest: CLLocationCoordinate2D? = nil
    private var fallbackWaypointsJson: String = "[]"

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        isLoaded = true
        if let js = pendingJs {
            webView.evaluateJavaScript(js, completionHandler: nil)
            pendingJs = nil
        }
    }

    func recenter() {
        webView?.evaluateJavaScript("recenterMap();", completionHandler: nil)
    }

    func update(
        merchantCoordinate: CLLocationCoordinate2D?,
        customerCoordinate: CLLocationCoordinate2D?,
        riderCoordinate: CLLocationCoordinate2D?,
        routeCoordinates: [CLLocationCoordinate2D],
        riderBearing: Double,
        speedKmh: Double
    ) {
        let waypointsJson: String
        if !routeCoordinates.isEmpty {
            let pts = routeCoordinates.map { "[\($0.latitude),\($0.longitude)]" }.joined(separator: ",")
            waypointsJson = "[\(pts)]"
        } else {
            let origin = riderCoordinate ?? merchantCoordinate
            let dest = customerCoordinate
            if let orig = origin, let dst = dest {
                let origDelta = lastRouteOrigin.map { abs($0.latitude - orig.latitude) + abs($0.longitude - orig.longitude) } ?? 1.0
                let destDelta = lastRouteDest.map { abs($0.latitude - dst.latitude) + abs($0.longitude - dst.longitude) } ?? 1.0
                if origDelta > 0.0008 || destDelta > 0.0001 {
                    self.lastRouteOrigin = orig
                    self.lastRouteDest = dst
                    computeRoadPolyline(from: orig, to: dst)
                }
            }
            waypointsJson = fallbackWaypointsJson
        }

        let mLatStr = merchantCoordinate != nil ? "\(merchantCoordinate!.latitude)" : "null"
        let mLngStr = merchantCoordinate != nil ? "\(merchantCoordinate!.longitude)" : "null"
        let cLatStr = customerCoordinate != nil ? "\(customerCoordinate!.latitude)" : "null"
        let cLngStr = customerCoordinate != nil ? "\(customerCoordinate!.longitude)" : "null"

        let riderJson: String
        if let r = riderCoordinate {
            let ts = Int(Date().timeIntervalSince1970 * 1000)
            riderJson = "{\"lat\":\(r.latitude),\"lng\":\(r.longitude),\"heading\":\(riderBearing),\"speedKmh\":\(speedKmh),\"timestamp\":\(ts)}"
        } else {
            riderJson = "null"
        }

        let js = "if (window.updateMapData) { window.updateMapData(\(mLatStr), \(mLngStr), \(cLatStr), \(cLngStr), \(riderJson), \(waypointsJson)); }"

        if isLoaded {
            webView?.evaluateJavaScript(js, completionHandler: nil)
        } else {
            pendingJs = js
        }
    }

    private func computeRoadPolyline(from origin: CLLocationCoordinate2D, to dest: CLLocationCoordinate2D) {
        let req = MKDirections.Request()
        req.source = MKMapItem(placemark: MKPlacemark(coordinate: origin))
        req.destination = MKMapItem(placemark: MKPlacemark(coordinate: dest))
        req.transportType = .automobile

        let directions = MKDirections(request: req)
        directions.calculate { [weak self] response, _ in
            guard let self = self, let route = response?.routes.first else { return }
            let pointCount = route.polyline.pointCount
            var coords = [CLLocationCoordinate2D](repeating: kCLLocationCoordinate2DInvalid, count: pointCount)
            route.polyline.getCoordinates(&coords, range: NSRange(location: 0, length: pointCount))

            let waypointsArray = coords.map { "[\($0.latitude),\($0.longitude)]" }.joined(separator: ",")
            self.fallbackWaypointsJson = "[\(waypointsArray)]"

            if self.isLoaded {
                let js = "if (window.updateWaypoints) { window.updateWaypoints(\(self.fallbackWaypointsJson)); }"
                self.webView?.evaluateJavaScript(js, completionHandler: nil)
            }
        }
    }
}

// MARK: - Zepto/Blinkit Light Google Maps HTML Template
private func generateGoogleMapsLiveTrackingHtml(initLat: Double, initLng: Double) -> String {
    return #"""
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <style>
        html, body, #map {
            margin: 0;
            padding: 0;
            width: 100%;
            height: 100%;
            background: #f8f9fa;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            overflow: hidden;
            -webkit-user-select: none;
            user-select: none;
        }

        /* 3D Elevated Store Hub Beacon */
        .store-marker-3d {
            position: absolute;
            width: 44px;
            height: 44px;
            transform: translate(-50%, -50%);
            display: flex;
            align-items: center;
            justify-content: center;
            pointer-events: none;
        }
        .store-beacon {
            position: absolute;
            width: 44px;
            height: 44px;
            border-radius: 50%;
            background: rgba(2, 132, 199, 0.22);
            border: 1.5px solid rgba(2, 132, 199, 0.7);
            animation: pulseBeacon 2.5s infinite ease-out;
        }
        .store-pill {
            position: relative;
            width: 32px;
            height: 32px;
            background: linear-gradient(135deg, #0284C7 0%, #0369A1 100%);
            border: 2.5px solid #FFFFFF;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 14px rgba(2, 132, 199, 0.4), 0 2px 6px rgba(0, 0, 0, 0.15);
        }
        .store-pill svg { width: 17px; height: 17px; fill: #FFFFFF; }

        /* 3D Elevated Customer Destination Beacon */
        .customer-marker-3d {
            position: absolute;
            width: 44px;
            height: 44px;
            transform: translate(-50%, -50%);
            display: flex;
            align-items: center;
            justify-content: center;
            pointer-events: none;
        }
        .customer-beacon {
            position: absolute;
            width: 44px;
            height: 44px;
            border-radius: 50%;
            background: rgba(249, 115, 22, 0.22);
            border: 1.5px solid rgba(249, 115, 22, 0.7);
            animation: pulseBeacon 2.5s infinite ease-out 0.5s;
        }
        .customer-pill {
            position: relative;
            width: 32px;
            height: 32px;
            background: linear-gradient(135deg, #F97316 0%, #C2410C 100%);
            border: 2.5px solid #FFFFFF;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 14px rgba(249, 115, 22, 0.4), 0 2px 6px rgba(0, 0, 0, 0.15);
        }
        .customer-pill svg { width: 17px; height: 17px; fill: #FFFFFF; }

        /* 3D Animated Scooter Puck with Headlight Beam and Radar Wave */
        .biker-anchor {
            position: absolute;
            width: 60px;
            height: 60px;
            transform: translate(-50%, -50%);
            display: flex;
            align-items: center;
            justify-content: center;
            pointer-events: none;
        }
        .biker-rotator {
            position: relative;
            width: 60px;
            height: 60px;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: transform 0.4s cubic-bezier(0.2, 0.8, 0.2, 1);
        }
        .biker-headlight {
            position: absolute;
            top: -24px;
            left: 50%;
            transform: translateX(-50%);
            width: 46px;
            height: 34px;
            background: radial-gradient(ellipse at 50% 100%, rgba(16, 185, 129, 0.5) 0%, rgba(16, 185, 129, 0.15) 50%, rgba(16, 185, 129, 0) 80%);
            clip-path: polygon(35% 100%, 65% 100%, 100% 0%, 0% 0%);
            pointer-events: none;
        }
        .biker-pulse-primary {
            position: absolute;
            width: 48px;
            height: 48px;
            border-radius: 50%;
            background: rgba(16, 185, 129, 0.2);
            border: 1.5px solid rgba(16, 185, 129, 0.8);
            animation: radarWave 2s cubic-bezier(0.1, 0.7, 0.1, 1) infinite;
        }
        .biker-pulse-secondary {
            position: absolute;
            width: 48px;
            height: 48px;
            border-radius: 50%;
            background: rgba(16, 185, 129, 0.12);
            animation: radarWave 2s cubic-bezier(0.1, 0.7, 0.1, 1) infinite 0.7s;
        }
        .biker-core-puck {
            position: relative;
            width: 36px;
            height: 36px;
            background: linear-gradient(135deg, #10B981 0%, #059669 100%);
            border: 2.5px solid #FFFFFF;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 14px rgba(16, 185, 129, 0.6), 0 2px 6px rgba(0, 0, 0, 0.2);
        }
        .biker-core-puck svg {
            width: 20px;
            height: 20px;
            fill: #FFFFFF;
            filter: drop-shadow(0 1px 2px rgba(0,0,0,0.3));
        }

        @keyframes radarWave {
            0% { transform: scale(0.6); opacity: 0.9; }
            70% { transform: scale(1.4); opacity: 0.3; }
            100% { transform: scale(1.6); opacity: 0; }
        }
        @keyframes pulseBeacon {
            0% { transform: scale(0.8); opacity: 0.8; }
            100% { transform: scale(1.5); opacity: 0; }
        }
    </style>
    <script src="https://maps.googleapis.com/maps/api/js?key=AIzaSyCzi_sMDds2_im406sGTCU8WAFZoTyNg5c&libraries=geometry"></script>
</head>
<body>
<div id="map"></div>
<script>
    var ZEPTO_LIGHT_STYLE = [
        { "elementType": "geometry", "stylers": [{ "color": "#f8f9fa" }] },
        { "elementType": "labels.icon", "stylers": [{ "visibility": "off" }] },
        { "elementType": "labels.text.fill", "stylers": [{ "color": "#616161" }] },
        { "elementType": "labels.text.stroke", "stylers": [{ "color": "#f5f5f5" }] },
        { "featureType": "administrative.land_parcel", "elementType": "labels.text.fill", "stylers": [{ "color": "#bdbdbd" }] },
        { "featureType": "poi", "elementType": "geometry", "stylers": [{ "color": "#eeeeee" }] },
        { "featureType": "poi", "elementType": "labels.text.fill", "stylers": [{ "color": "#757575" }] },
        { "featureType": "poi.park", "elementType": "geometry", "stylers": [{ "color": "#e8f5e9" }] },
        { "featureType": "poi.park", "elementType": "labels.text.fill", "stylers": [{ "color": "#9e9e9e" }] },
        { "featureType": "road", "elementType": "geometry", "stylers": [{ "color": "#ffffff" }] },
        { "featureType": "road.arterial", "elementType": "labels.text.fill", "stylers": [{ "color": "#757575" }] },
        { "featureType": "road.highway", "elementType": "geometry", "stylers": [{ "color": "#dadada" }] },
        { "featureType": "road.highway", "elementType": "labels.text.fill", "stylers": [{ "color": "#616161" }] },
        { "featureType": "road.local", "elementType": "labels.text.fill", "stylers": [{ "color": "#9e9e9e" }] },
        { "featureType": "transit.line", "elementType": "geometry", "stylers": [{ "color": "#e5e5e5" }] },
        { "featureType": "transit.station", "elementType": "geometry", "stylers": [{ "color": "#eeeeee" }] },
        { "featureType": "water", "elementType": "geometry", "stylers": [{ "color": "#c8e0f4" }] },
        { "featureType": "water", "elementType": "labels.text.fill", "stylers": [{ "color": "#9e9e9e" }] }
    ];

    var map = null;
    var directionsService = null;
    var routePolyline = null;
    var routeGlowPolyline = null;
    var storeOverlay = null;
    var customerOverlay = null;
    var riderOverlay = null;

    var storePos = null;
    var customerPos = null;
    var riderPos = null;
    var lastAcceptedTimestamp = 0;
    var autoFollow = true;

    var storeSvg = '<svg viewBox="0 0 24 24"><path d="M4 4h16v3H4zm0 5h16v11H4zm3 2v7h10v-7z"/></svg>';
    var customerSvg = '<svg viewBox="0 0 24 24"><path d="M12 3L2 12h3v8h14v-8h3L12 3zm0 4.7l4 3.6V18h-8v-6.7l4-3.6z"/></svg>';
    var scooterSvg = '<svg viewBox="0 0 24 24"><path d="M19 7c0-1.1-.9-2-2-2h-3v2h3v2.65L13.52 14H10V9H6c-2.21 0-4 1.79-4 4v3h2c0 1.66 1.34 3 3 3s3-1.34 3-3h4.18c.41 1.16 1.51 2 2.82 2 1.66 0 3-1.34 3-3h1v-4.5L19 7zM7 17c-.55 0-1-.45-1-1h2c0 .55-.45 1-1 1zm11 0c-.55 0-1-.45-1-1h2c0 .55-.45 1-1 1z"/></svg>';

    function initMap() {
        var initialCenter = { lat: INIT_LAT, lng: INIT_LNG };
        map = new google.maps.Map(document.getElementById('map'), {
            center: initialCenter,
            zoom: 15,
            styles: ZEPTO_LIGHT_STYLE,
            disableDefaultUI: true,
            zoomControl: false,
            mapTypeControl: false,
            streetViewControl: false,
            fullscreenControl: false,
            gestureHandling: 'greedy'
        });

        directionsService = new google.maps.DirectionsService();

        routeGlowPolyline = new google.maps.Polyline({
            path: [],
            geodesic: true,
            strokeColor: '#059669',
            strokeOpacity: 0.25,
            strokeWeight: 10,
            map: map
        });

        routePolyline = new google.maps.Polyline({
            path: [],
            geodesic: true,
            strokeColor: '#10B981',
            strokeOpacity: 0.95,
            strokeWeight: 6,
            map: map
        });

        map.addListener('dragstart', function() { autoFollow = false; });
    }

    // Google Maps Custom HTML Overlay
    function CustomHtmlOverlay(lat, lng, html) {
        this.lat = lat;
        this.lng = lng;
        this.html = html;
        this.div = null;
        this.setMap(map);
    }
    CustomHtmlOverlay.prototype = new google.maps.OverlayView();

    CustomHtmlOverlay.prototype.onAdd = function() {
        var div = document.createElement('div');
        div.style.position = 'absolute';
        div.style.cursor = 'default';
        div.innerHTML = this.html;
        this.div = div;
        var panes = this.getPanes();
        panes.overlayMouseTarget.appendChild(div);
    };

    CustomHtmlOverlay.prototype.draw = function() {
        var overlayProjection = this.getProjection();
        if (!overlayProjection || !this.div) return;
        var position = new google.maps.LatLng(this.lat, this.lng);
        var point = overlayProjection.fromLatLngToDivPixel(position);
        if (point) {
            this.div.style.left = point.x + 'px';
            this.div.style.top = point.y + 'px';
        }
    };

    CustomHtmlOverlay.prototype.onRemove = function() {
        if (this.div && this.div.parentNode) {
            this.div.parentNode.removeChild(this.div);
            this.div = null;
        }
    };

    CustomHtmlOverlay.prototype.setPosition = function(lat, lng) {
        this.lat = lat;
        this.lng = lng;
        this.draw();
    };

    CustomHtmlOverlay.prototype.setHtml = function(html) {
        this.html = html;
        if (this.div) {
            this.div.innerHTML = html;
        }
    };

    // Smooth Marker Interpolation
    var animFrame = null;
    function interpolateRiderPosition(startLat, startLng, endLat, endLng, durationMs) {
        if (animFrame) { cancelAnimationFrame(animFrame); animFrame = null; }
        var startTime = performance.now();
        function step(now) {
            var elapsed = now - startTime;
            var t = Math.min(1, elapsed / durationMs);
            var ease = 1 - Math.pow(1 - t, 3);
            var curLat = startLat + (endLat - startLat) * ease;
            var curLng = startLng + (endLng - startLng) * ease;
            if (riderOverlay) {
                riderOverlay.setPosition(curLat, curLng);
            }
            if (t < 1) {
                animFrame = requestAnimationFrame(step);
            } else {
                animFrame = null;
            }
        }
        animFrame = requestAnimationFrame(step);
    }

    function buildRiderHtml(heading) {
        var rot = heading != null ? heading : 0;
        return '<div class="biker-anchor">' +
               '<div class="biker-rotator" style="transform: rotate(' + rot + 'deg);">' +
               '<div class="biker-headlight"></div>' +
               '<div class="biker-pulse-primary"></div><div class="biker-pulse-secondary"></div>' +
               '<div class="biker-core-puck">' + scooterSvg + '</div>' +
               '</div></div>';
    }

    function renderWaypoints(waypoints) {
        if (!routePolyline) return;
        if (waypoints && waypoints.length > 1) {
            var path = waypoints.map(function(pt) {
                return new google.maps.LatLng(pt[0] || pt.lat, pt[1] || pt.lng);
            });
            routePolyline.setPath(path);
            routeGlowPolyline.setPath(path);
        } else if (storePos && customerPos && directionsService) {
            var origin = riderPos || storePos;
            directionsService.route({
                origin: origin,
                destination: customerPos,
                travelMode: google.maps.TravelMode.DRIVING
            }, function(response, status) {
                if (status === 'OK' && response && response.routes && response.routes[0]) {
                    var routePath = response.routes[0].overview_path;
                    routePolyline.setPath(routePath);
                    routeGlowPolyline.setPath(routePath);
                }
            });
        }
    }
    window.updateWaypoints = renderWaypoints;

    function fitBoundsIfAppropriate() {
        if (!map) return;
        var bounds = new google.maps.LatLngBounds();
        var count = 0;
        if (storePos) { bounds.extend(new google.maps.LatLng(storePos.lat, storePos.lng)); count++; }
        if (customerPos) { bounds.extend(new google.maps.LatLng(customerPos.lat, customerPos.lng)); count++; }
        if (riderPos) { bounds.extend(new google.maps.LatLng(riderPos.lat, riderPos.lng)); count++; }
        if (count >= 2) {
            map.fitBounds(bounds, { top: 60, right: 60, bottom: 60, left: 60 });
        } else if (count === 1) {
            var target = riderPos || customerPos || storePos;
            map.setCenter(new google.maps.LatLng(target.lat, target.lng));
            map.setZoom(16);
        }
    }

    function updateMapData(mLat, mLng, cLat, cLng, rider, waypoints) {
        if (!map) {
            initMap();
        }

        // Store Hub
        if (mLat && mLng && mLat !== 0) {
            storePos = { lat: mLat, lng: mLng };
            var storeHtml = '<div class="store-marker-3d"><div class="store-beacon"></div><div class="store-pill">' + storeSvg + '</div></div>';
            if (!storeOverlay) {
                storeOverlay = new CustomHtmlOverlay(mLat, mLng, storeHtml);
            } else {
                storeOverlay.setPosition(mLat, mLng);
            }
        }

        // Customer Location
        if (cLat && cLng && cLat !== 0) {
            customerPos = { lat: cLat, lng: cLng };
            var custHtml = '<div class="customer-marker-3d"><div class="customer-beacon"></div><div class="customer-pill">' + customerSvg + '</div></div>';
            if (!customerOverlay) {
                customerOverlay = new CustomHtmlOverlay(cLat, cLng, custHtml);
            } else {
                customerOverlay.setPosition(cLat, cLng);
            }
        }

        // Live Rider Puck
        if (rider && rider.lat && rider.lng) {
            var ts = rider.timestamp || Date.now();
            if (ts >= lastAcceptedTimestamp) {
                lastAcceptedTimestamp = ts;
                var prevPos = riderPos;
                riderPos = { lat: rider.lat, lng: rider.lng };
                var rot = (rider.heading != null) ? rider.heading : 0;
                var bikerHtml = buildRiderHtml(rot);

                if (!riderOverlay) {
                    riderOverlay = new CustomHtmlOverlay(rider.lat, rider.lng, bikerHtml);
                    fitBoundsIfAppropriate();
                } else {
                    riderOverlay.setHtml(bikerHtml);
                    if (prevPos) {
                        interpolateRiderPosition(prevPos.lat, prevPos.lng, rider.lat, rider.lng, 900);
                    } else {
                        riderOverlay.setPosition(rider.lat, rider.lng);
                    }
                }

                if (autoFollow && map) {
                    map.panTo(new google.maps.LatLng(rider.lat, rider.lng));
                }
            }
        }

        renderWaypoints(waypoints);
    }
    window.updateMapData = updateMapData;

    function recenterMap() {
        autoFollow = true;
        if (!map) return;
        if (riderPos) {
            map.panTo(new google.maps.LatLng(riderPos.lat, riderPos.lng));
            map.setZoom(16);
        } else {
            fitBoundsIfAppropriate();
        }
    }
    window.recenterMap = recenterMap;

    // Launch map on load
    if (typeof google !== 'undefined' && google.maps) {
        initMap();
    } else {
        window.addEventListener('load', initMap);
    }
</script>
</body>
</html>
"""#.replacingOccurrences(of: "INIT_LAT", with: "\(initLat)")
   .replacingOccurrences(of: "INIT_LNG", with: "\(initLng)")
}

// MARK: - Compatibility Definitions
public enum MapAnnotationType {
    case merchant
    case customer
    case rider
}

public class CustomMapAnnotation: NSObject, MKAnnotation {
    public let coordinate: CLLocationCoordinate2D
    public let title: String?
    public let type: MapAnnotationType
    public let bearing: Double

    public init(coordinate: CLLocationCoordinate2D, title: String?, type: MapAnnotationType, bearing: Double = 0.0) {
        self.coordinate = coordinate
        self.title = title
        self.type = type
        self.bearing = bearing
    }
}
