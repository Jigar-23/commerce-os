import SwiftUI

/**
 * 1:1 Parity with Android HeaderCanopy (com.commerceos.android.ui.home.HeaderCanopy).
 * Shared flagship canopy header between Home and Categories.
 * Features:
 * - PharmEasy deep medical teal canopy (#0E675E) with bottom rounded corners (24pt).
 * - Location Pin button with live locating spinner, address tag, chevron down, and ⚡ 10 MINS SLA badge.
 * - Live unread notification bell badge.
 * - Stadium floating search pill with auto-cycling placeholder hints and camera prescription upload action.
 */
public struct HeaderCanopy: View {
    public let homeContext: HomeContext?
    public let selectedAddress: ApiAddress?
    public let calculatedEtaMinutes: Int
    public let locationHeaderLabel: String?
    public let isLocating: Bool
    public let unreadNotificationCount: Int
    public let onChangeAddress: () -> Void
    public let onProfileClick: () -> Void
    public let onNotificationsClick: () -> Void
    public let onSearchClick: (String) -> Void
    public let onUploadPrescription: () -> Void

    @State private var hintIndex: Int = 0
    private let searchHints = [
        "Search medicines, e.g. Dolo 650...",
        "Search for Paracetamol, Crocin...",
        "Search for Augmentin 625 Duo...",
        "Search for Benadryl Cough syrup...",
        "Search for Glycomet, Insulin...",
        "Search for Pan 40, Digene..."
    ]

    private let timer = Timer.publish(every: 3.0, on: .main, in: .common).autoconnect()

    public init(
        homeContext: HomeContext? = nil,
        selectedAddress: ApiAddress? = nil,
        calculatedEtaMinutes: Int = 0,
        locationHeaderLabel: String? = nil,
        isLocating: Bool = false,
        unreadNotificationCount: Int = 0,
        onChangeAddress: @escaping () -> Void = {},
        onProfileClick: @escaping () -> Void = {},
        onNotificationsClick: @escaping () -> Void = {},
        onSearchClick: @escaping (String) -> Void = { _ in },
        onUploadPrescription: @escaping () -> Void = {}
    ) {
        self.homeContext = homeContext
        self.selectedAddress = selectedAddress
        self.calculatedEtaMinutes = calculatedEtaMinutes
        self.locationHeaderLabel = locationHeaderLabel
        self.isLocating = isLocating
        self.unreadNotificationCount = unreadNotificationCount
        self.onChangeAddress = onChangeAddress
        self.onProfileClick = onProfileClick
        self.onNotificationsClick = onNotificationsClick
        self.onSearchClick = onSearchClick
        self.onUploadPrescription = onUploadPrescription
    }

    private var hasAddress: Bool {
        homeContext?.hasAddress == true || selectedAddress != nil || !(locationHeaderLabel?.isEmpty ?? true)
    }

    private var titleTag: String {
        if let tag = selectedAddress?.tag, !tag.isEmpty { return tag }
        if let label = locationHeaderLabel, !label.isEmpty, label != "Select Delivery Location" {
            return String(label.prefix(20))
        }
        return hasAddress ? "Home" : "Deliver to"
    }

    private var addressLine: String {
        if let line = selectedAddress?.addressLine, !line.isEmpty { return line }
        if let display = homeContext?.displayLabel, !display.isEmpty { return display }
        if let label = locationHeaderLabel, !label.isEmpty, label != "Select Delivery Location" { return label }
        return "Select delivery location"
    }

    public var body: some View {
        VStack(spacing: 12) {
            // Location Bar & Notification Action
            HStack(alignment: .center) {
                // Address Tap Action
                Button(action: onChangeAddress) {
                    HStack(spacing: 10) {
                        // Location icon circle
                        ZStack {
                            Circle()
                                .fill(Color.white.opacity(0.18))
                                .overlay(Circle().stroke(Color.white.opacity(0.25), lineWidth: 1))
                                .frame(width: 38, height: 38)

                            if isLocating && selectedAddress == nil {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .scaleEffect(0.8)
                            } else {
                                Image(systemName: "location.fill")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text("DELIVERING TO")
                                .font(.system(size: 9.5, weight: .bold))
                                .foregroundColor(Color.white.opacity(0.75))
                                .tracking(0.6)

                            HStack(spacing: 4) {
                                Text(titleTag)
                                    .font(.system(size: 15, weight: .heavy))
                                    .foregroundColor(.white)
                                    .lineLimit(1)

                                Image(systemName: "chevron.down")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Color.white.opacity(0.85))

                                // SLA Badge
                                Text(calculatedEtaMinutes > 0 ? "⚡ \(calculatedEtaMinutes) MINS" : "⚡ 10 MINS")
                                    .font(.system(size: 9, weight: .black))
                                    .foregroundColor(Color(hex: "166534"))
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 2)
                                    .background(Color(hex: "DCFCE7"))
                                    .cornerRadius(4)
                            }

                            Text(addressLine)
                                .font(.system(size: 11.5))
                                .foregroundColor(Color.white.opacity(0.82))
                                .lineLimit(1)
                        }
                    }
                }
                .buttonStyle(PlainButtonStyle())

                Spacer()

                // Notification Bell with Badge
                ZStack(alignment: .topTrailing) {
                    Button(action: onNotificationsClick) {
                        ZStack {
                            Circle()
                                .fill(Color.white.opacity(0.18))
                                .overlay(Circle().stroke(Color.white.opacity(0.25), lineWidth: 1))
                                .frame(width: 38, height: 38)

                            Image(systemName: "bell.fill")
                                .font(.system(size: 16))
                                .foregroundColor(.white)
                        }
                    }
                    .buttonStyle(PlainButtonStyle())

                    if unreadNotificationCount > 0 {
                        Text(unreadNotificationCount > 99 ? "99+" : "\(unreadNotificationCount)")
                            .font(.system(size: 9, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(Color(hex: "EF4444"))
                            .clipShape(Capsule())
                            .offset(x: 4, y: -4)
                    }
                }
            }

            // High-Utility Stadium Search Bar
            HStack(spacing: 8) {
                // Search Pill
                Button(action: { onSearchClick("") }) {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(Color(hex: "E84335"))

                        Text(searchHints[hintIndex])
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Color(hex: "64748B"))
                            .lineLimit(1)
                            .id(hintIndex)
                            .transition(.opacity.combined(with: .move(edge: .bottom)))

                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 48)
                    .background(Color.white)
                    .cornerRadius(24)
                    .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 1)
                }
                .buttonStyle(PlainButtonStyle())

                // Upload Rx Pill Button
                Button(action: onUploadPrescription) {
                    HStack(spacing: 4) {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 13, weight: .bold))
                        Text("Upload Rx")
                            .font(.system(size: 11.5, weight: .heavy))
                    }
                    .foregroundColor(Color(hex: "EA580C"))
                    .padding(.horizontal, 10)
                    .frame(height: 48)
                    .background(Color(hex: "FFF7ED"))
                    .overlay(
                        RoundedRectangle(cornerRadius: 24)
                            .stroke(Color(hex: "FED7AA"), lineWidth: 1)
                    )
                    .cornerRadius(24)
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 16)
        .background(Color(hex: "0E675E"))
        .clipShape(RoundedCornerShape(corners: [.bottomLeft, .bottomRight], radius: 24))
        .onReceive(timer) { _ in
            withAnimation(.easeInOut(duration: 0.35)) {
                hintIndex = (hintIndex + 1) % searchHints.count
            }
        }
    }
}

// Rounded corner helper for selective corners
public struct RoundedCornerShape: Shape {
    public var corners: UIRectCorner = .allCorners
    public var radius: CGFloat = .infinity

    public func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}
