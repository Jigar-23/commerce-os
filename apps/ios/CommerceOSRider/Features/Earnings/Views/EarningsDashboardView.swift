import SwiftUI

public struct EarningsDashboardView: View {
    @EnvironmentObject private var container: RiderContainer
    @EnvironmentObject private var sessionManager: RiderSessionManager
    
    @State private var trips: [TripHistoryItem] = []
    @State private var isLoadingTrips: Bool = false
    
    public init() {}
    
    private var todayEarnings: Double {
        sessionManager.profile?.todayEarnings ?? 0.0
    }
    
    private var completedOrders: Int {
        sessionManager.profile?.todayCompletedOrders ?? 0
    }
    
    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    // Today's Total Card
                    VStack(spacing: 6) {
                        Text("TODAY'S TOTAL EARNINGS")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color.white.opacity(0.8))
                        Text("$\(String(format: "%.2f", todayEarnings))")
                            .font(.system(size: 36, weight: .black))
                            .foregroundColor(.white)
                        Text("\(completedOrders) Completed Trips • Rating: \(String(format: "%.2f", sessionManager.profile?.rating ?? 5.0)) ★")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
                    .background(
                        LinearGradient(
                            colors: [RiderTheme.Colors.hudBackground, RiderTheme.Colors.hudSurface],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .cornerRadius(16)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(RiderTheme.Colors.hudBorder.opacity(0.4), lineWidth: 1)
                    )
                    .padding(.horizontal)
                    
                    // Daily Incentive Progress
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Daily Milestone Incentive")
                                .font(.system(size: 14, weight: .bold))
                            Spacer()
                            Text("\(completedOrders)/12 Trips")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(RiderTheme.Colors.safetyGreen)
                        }
                        
                        ProgressView(value: min(Double(completedOrders), 12.0), total: 12.0)
                            .accentColor(RiderTheme.Colors.safetyGreen)
                            .scaleEffect(x: 1, y: 1.5, anchor: .center)
                        
                        Text(completedOrders >= 12 ? "Daily milestone incentive achieved! +$15.00 surge bonus added." : "Complete \(max(0, 12 - completedOrders)) more orders today to earn +$15.00 surge bonus!")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                    .padding(14)
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                    .padding(.horizontal)
                    
                    // Completed Orders History List
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Recent Trips Today")
                            .font(.system(size: 16, weight: .bold))
                            .padding(.horizontal)
                        
                        if isLoadingTrips {
                            HStack {
                                Spacer()
                                ProgressView("Loading trips...")
                                    .font(.system(size: 12))
                                Spacer()
                            }
                            .padding()
                        } else if trips.isEmpty {
                            VStack(spacing: 8) {
                                Image(systemName: "bicycle")
                                    .font(.system(size: 32))
                                    .foregroundColor(.secondary)
                                Text("No trips completed yet today.")
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundColor(.secondary)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 24)
                        } else {
                            ForEach(trips) { trip in
                                HStack {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(trip.orderId)
                                            .font(.system(size: 13, weight: .bold))
                                        Text("\(trip.customerArea) • \(String(format: "%.1f", trip.distanceKm)) km • \(trip.durationMins) mins")
                                            .font(.system(size: 11))
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Text("+$\(String(format: "%.2f", trip.payout))")
                                        .font(.system(size: 15, weight: .black))
                                        .foregroundColor(RiderTheme.Colors.safetyGreen)
                                }
                                .padding(12)
                                .background(Color(.systemBackground))
                                .cornerRadius(10)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(Color(.separator).opacity(0.3), lineWidth: 1)
                                )
                                .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
                                .padding(.horizontal)
                            }
                        }
                    }
                }
                .padding(.top, 10)
            }
            .navigationTitle("Earnings & Trips")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                fetchTripsHistory()
            }
        }
    }
    
    private func fetchTripsHistory() {
        isLoadingTrips = true
        Task {
            do {
                let fetched: [TripHistoryItem] = try await RiderAPIClient.shared.request(endpoint: .getTripsHistory)
                await MainActor.run {
                    self.trips = fetched
                    self.isLoadingTrips = false
                }
            } catch {
                await MainActor.run {
                    self.trips = []
                    self.isLoadingTrips = false
                }
            }
        }
    }
}

public struct TripHistoryItem: Identifiable, Codable {
    public let id: String
    public let orderId: String
    public let customerArea: String
    public let distanceKm: Double
    public let durationMins: Int
    public let payout: Double
    
    public init(id: String, orderId: String, customerArea: String, distanceKm: Double, durationMins: Int, payout: Double) {
        self.id = id
        self.orderId = orderId
        self.customerArea = customerArea
        self.distanceKm = distanceKm
        self.durationMins = durationMins
        self.payout = payout
    }
}
