import SwiftUI
import WebKit
import CoreLocation

/**
 * 1:1 Parity with Android RealLocationMapViewport in MapProvider.kt
 * Interactive Google Maps Viewport powered by Google Maps JavaScript API (Console Cloud Key: AIzaSyCzi_sMDds2_im406sGTCU8WAFZoTyNg5c).
 * Uses clean Zepto/Blinkit light styling and reports camera settle events for address geocoding.
 */
public struct LocationMapViewport: UIViewRepresentable {
    public var centerCoordinate: CLLocationCoordinate2D?
    public var onCameraSettled: (CLLocationCoordinate2D) -> Void

    public init(
        centerCoordinate: CLLocationCoordinate2D? = nil,
        onCameraSettled: @escaping (CLLocationCoordinate2D) -> Void = { _ in }
    ) {
        self.centerCoordinate = centerCoordinate
        self.onCameraSettled = onCameraSettled
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    public func makeUIView(context: Context) -> WKWebView {
        let contentController = WKUserContentController()
        contentController.add(context.coordinator, name: "cameraSettled")

        let config = WKWebViewConfiguration()
        config.userContentController = contentController
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = prefs

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        webView.isOpaque = true
        webView.backgroundColor = UIColor(red: 248/255, green: 249/255, blue: 250/255, alpha: 1.0)

        context.coordinator.webView = webView

        let lat = centerCoordinate?.latitude ?? 28.1970
        let lng = centerCoordinate?.longitude ?? 76.6190
        let html = generateGoogleMapsViewportHtml(lat: lat, lng: lng)
        webView.loadHTMLString(html, baseURL: URL(string: "https://maps.googleapis.com"))

        return webView
    }

    public func updateUIView(_ uiView: WKWebView, context: Context) {
        if let target = centerCoordinate {
            let dLat = abs(context.coordinator.lastSettledLat - target.latitude)
            let dLng = abs(context.coordinator.lastSettledLng - target.longitude)
            if dLat > 0.0002 || dLng > 0.0002 {
                context.coordinator.panTo(lat: target.latitude, lng: target.longitude)
            }
        }
    }

    public class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: LocationMapViewport
        weak var webView: WKWebView?
        var lastSettledLat: Double = 0.0
        var lastSettledLng: Double = 0.0
        private var isLoaded = false
        private var debounceTimer: Timer?

        init(_ parent: LocationMapViewport) {
            self.parent = parent
            self.lastSettledLat = parent.centerCoordinate?.latitude ?? 28.1970
            self.lastSettledLng = parent.centerCoordinate?.longitude ?? 76.6190
        }

        public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            isLoaded = true
        }

        public func panTo(lat: Double, lng: Double) {
            lastSettledLat = lat
            lastSettledLng = lng
            if isLoaded {
                let js = "if (window.panToCoordinates) { window.panToCoordinates(\(lat), \(lng)); }"
                webView?.evaluateJavaScript(js, completionHandler: nil)
            }
        }

        public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == "cameraSettled",
                  let body = message.body as? [String: Any],
                  let lat = body["lat"] as? Double,
                  let lng = body["lng"] as? Double else { return }

            lastSettledLat = lat
            lastSettledLng = lng

            debounceTimer?.invalidate()
            debounceTimer = Timer.scheduledTimer(withTimeInterval: 0.35, repeats: false) { [weak self] _ in
                guard let self = self else { return }
                self.parent.onCameraSettled(CLLocationCoordinate2D(latitude: lat, longitude: lng))
            }
        }
    }
}

// MARK: - Light Google Maps HTML Template for Address Viewport
private func generateGoogleMapsViewportHtml(lat: Double, lng: Double) -> String {
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
            overflow: hidden;
            -webkit-user-select: none;
            user-select: none;
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
    function initMap() {
        var center = { lat: INIT_LAT, lng: INIT_LNG };
        map = new google.maps.Map(document.getElementById('map'), {
            center: center,
            zoom: 16,
            styles: ZEPTO_LIGHT_STYLE,
            disableDefaultUI: true,
            zoomControl: false,
            mapTypeControl: false,
            streetViewControl: false,
            fullscreenControl: false,
            gestureHandling: 'greedy'
        });

        map.addListener('idle', function() {
            var c = map.getCenter();
            if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.cameraSettled) {
                window.webkit.messageHandlers.cameraSettled.postMessage({
                    lat: c.lat(),
                    lng: c.lng()
                });
            }
        });
    }

    function panToCoordinates(lat, lng) {
        if (map) {
            map.panTo(new google.maps.LatLng(lat, lng));
        }
    }
    window.panToCoordinates = panToCoordinates;

    if (typeof google !== 'undefined' && google.maps) {
        initMap();
    } else {
        window.addEventListener('load', initMap);
    }
</script>
</body>
</html>
"""#.replacingOccurrences(of: "INIT_LAT", with: "\(lat)")
   .replacingOccurrences(of: "INIT_LNG", with: "\(lng)")
}
