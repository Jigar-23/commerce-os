import SwiftUI

public struct RiderOfferCardView: View {
    @EnvironmentObject private var container: RiderContainer
    @EnvironmentObject private var pipeline: RiderOfferEventPipeline
    let offer: DispatchOfferDto
    
    @State private var isProcessing: Bool = false
    
    public var body: some View {
        VStack(spacing: 16) {
            // Countdown Bar (30 seconds)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color.gray.opacity(0.3))
                        .frame(height: 6)
                    
                    RoundedRectangle(cornerRadius: 3)
                        .fill(pipeline.offerTimeRemainingSeconds > 10 ? RiderTheme.Colors.safetyGreen : RiderTheme.Colors.urgentRed)
                        .frame(width: geo.size.width * CGFloat(pipeline.offerTimeRemainingSeconds) / 30.0, height: 6)
                        .animation(.linear(duration: 1.0), value: pipeline.offerTimeRemainingSeconds)
                }
            }
            .frame(height: 6)
            
            // Header: Payout & SLA
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("NEW DISPATCH OFFER")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(RiderTheme.Colors.safetyYellow)
                    Text("10-Min Guaranteed Delivery")
                        .font(.system(size: 13, weight: .bold))
                }
                Spacer()
                Text("$\(String(format: "%.2f", offer.payoutAmount))")
                    .font(.system(size: 26, weight: .black))
                    .foregroundColor(RiderTheme.Colors.safetyGreen)
            }
            
            Divider()
            
            // Route Details
            VStack(spacing: 12) {
                // Pickup
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(RiderTheme.Colors.safetyGreen)
                        .frame(width: 10, height: 10)
                        .padding(.top, 4)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("PICKUP • \(offer.merchantName)")
                            .font(.system(size: 12, weight: .bold))
                        Text(offer.merchantAddress)
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }
                
                // Drop
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(RiderTheme.Colors.speedAccent)
                        .frame(width: 10, height: 10)
                        .padding(.top, 4)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("DROP • \(offer.customerName)")
                            .font(.system(size: 12, weight: .bold))
                        Text(offer.customerAddress)
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }
            }
            .padding(12)
            .background(Color(.systemGray6))
            .cornerRadius(10)
            
            // Distance & Estimate
            HStack {
                Label("\(String(format: "%.1f", offer.totalDistanceKm)) km Total", systemImage: "road.lanes")
                    .font(.system(size: 12, weight: .semibold))
                Spacer()
                Label("\(pipeline.offerTimeRemainingSeconds)s remaining", systemImage: "timer")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(RiderTheme.Colors.safetyYellow)
            }
            
            // Action Buttons
            HStack(spacing: 12) {
                Button(action: declineOffer) {
                    Text("Decline")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(RiderTheme.Colors.urgentRed)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(RiderTheme.Colors.urgentRed.opacity(0.12))
                        .cornerRadius(10)
                }
                
                Button(action: acceptOffer) {
                    if isProcessing {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Text("ACCEPT OFFER")
                            .font(.system(size: 15, weight: .black))
                            .foregroundColor(.white)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(RiderTheme.Colors.safetyGreen)
                .cornerRadius(10)
                .disabled(isProcessing)
            }
        }
        .padding(20)
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(RiderTheme.Colors.hudBorder.opacity(0.3), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.15), radius: 12, x: 0, y: 4)
        .padding(.horizontal, 16)
    }
    
    private func acceptOffer() {
        isProcessing = true
        Task {
            do {
                let session: ActiveDeliverySessionDto = try await container.apiClient.post(
                    endpoint: .acceptOffer(id: offer.offerId),
                    body: ["offer_id": offer.offerId]
                )
                await MainActor.run {
                    pipeline.dismissActiveOffer(reason: "ACCEPTED")
                    container.sessionManager.activeSession = session
                    container.locationManager.activeDeliveryId = session.deliveryId
                    isProcessing = false
                }
            } catch {
                await MainActor.run {
                    pipeline.dismissActiveOffer(reason: "EXPIRED_OR_REASSIGNED")
                    isProcessing = false
                }
            }
        }
    }
    
    private func declineOffer() {
        Task {
            let _: [String: String]? = try? await container.apiClient.post(
                endpoint: .declineOffer(id: offer.offerId),
                body: ["offer_id": offer.offerId]
            )
            await MainActor.run {
                pipeline.dismissActiveOffer(reason: "DECLINED")
            }
        }
    }
}
