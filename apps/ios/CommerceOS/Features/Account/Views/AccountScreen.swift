import SwiftUI

public struct AccountScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var orderRepository: OrderRepository
    @Environment(\.dismiss) private var dismiss
    
    @State private var showRefillAlert: Bool = false
    @State private var showNotificationAlert: Bool = false
    
    private enum AccountSheetDestination: Identifiable {
        case orders
        case prescriptions
        case addresses
        case addAddress
        case serverSettings

        var id: String {
            switch self {
            case .orders: return "orders"
            case .prescriptions: return "prescriptions"
            case .addresses: return "addresses"
            case .addAddress: return "addAddress"
            case .serverSettings: return "serverSettings"
            }
        }
    }
    
    @State private var activeSheet: AccountSheetDestination? = nil
    
    private let storeContactPhone: String = "+91 1800-208-9999"
    
    public init() {}
    
    private var displayName: String {
        let name = container.customerSession?.name?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (name != nil && !name!.isEmpty) ? name! : "Customer"
    }
    
    private var rawPhone: String {
        container.customerSession?.phone ?? ""
    }
    
    private var maskedPhone: String {
        let digits = rawPhone.filter { $0.isNumber }
        if digits.count < 4 { return "Verified mobile number" }
        let suffix = String(digits.suffix(4))
        let prefix = digits.count >= 10 ? "+91 " : ""
        return "\(prefix)XXXXX \(suffix)"
    }
    
    private var initialLetter: String {
        String(displayName.trimmingCharacters(in: .whitespaces).first ?? "C").uppercased()
    }
    
    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Top Header matching Android
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("My Account")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("Manage your pharmacy profile, orders & addresses")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                    
                    Spacer()
                    
                    // Notification Bell Button
                    Button(action: { showNotificationAlert = true }) {
                        ZStack {
                            Circle()
                                .fill(Color.white)
                                .frame(width: 38, height: 38)
                                .overlay(
                                    Circle()
                                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                                .shadow(color: Color.black.opacity(0.04), radius: 2, x: 0, y: 1)
                            
                            Image(systemName: "bell.fill")
                                .font(.system(size: 16))
                                .foregroundColor(Color(hex: "059669"))
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 14)
                .padding(.bottom, 12)
                
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 14) {
                        // 1. Clean Customer Identification Card
                        customerIdentificationCard
                        
                        // 2. Core Management Pillars (3 Pillars: Your Orders, Prescriptions, Saved Addresses)
                        corePillarsRow
                        
                        // 3. Store & Pharmacy Services Card
                        storeAndPharmacyServicesCard
                        
                        // 4. Pharmacy Hub License Footer
                        pharmacyLicenseFooter
                        
                        // 5. Professional Logout Button
                        logoutButton
                        
                        Spacer(minLength: 30)
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 20)
                }
            }
            .background(Color(hex: "F4F5F7").ignoresSafeArea())
            .navigationBarHidden(true)
        }
        .navigationViewStyle(.stack)
        .sheet(item: $activeSheet) { destination in
            switch destination {
            case .orders:
                NavigationView {
                    OrderHistoryScreen()
                        .toolbar {
                            ToolbarItem(placement: .navigationBarTrailing) {
                                Button("Done") { activeSheet = nil }
                            }
                        }
                }
            case .prescriptions:
                NavigationView {
                    PrescriptionVaultScreen()
                        .toolbar {
                            ToolbarItem(placement: .navigationBarTrailing) {
                                Button("Done") { activeSheet = nil }
                            }
                        }
                }
            case .addresses:
                AddressBookView(
                    customerId: container.customerSession?.customerId ?? "",
                    onSelectAddress: { addr in
                        container.addressRepository.selectAddress(addr)
                        activeSheet = nil
                    },
                    onAddNewAddress: {
                        activeSheet = .addAddress
                    },
                    onBack: { activeSheet = nil },
                    fromProfile: true
                )
            case .addAddress:
                AddAddressFlowView(onSaveAddress: { newAddr in
                    container.addressRepository.selectAddress(newAddr)
                    activeSheet = nil
                })
            case .serverSettings:
                ServerSettingsSheet()
            }
        }
        .alert("Refill Reminders Active", isPresented: $showRefillAlert) {
            Button("Understood", role: .cancel) {}
        } message: {
            Text("Automatic reminders are configured for your chronic medications. You will be notified 3 days prior to your refill cycle to ensure uninterrupted dosage.")
        }
        .alert("Pharmacy Notifications", isPresented: $showNotificationAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("No unread alerts. Your registered medicines and prescription reviews are up to date.")
        }
    }
    
    // MARK: - Components
    
    private var customerIdentificationCard: some View {
        HStack(spacing: 14) {
            // Circular Initial Avatar
            ZStack {
                Circle()
                    .fill(Color(hex: "ECFDF5"))
                    .frame(width: 54, height: 54)
                    .overlay(
                        Circle()
                            .stroke(Color(hex: "10B981"), lineWidth: 1.5)
                    )
                
                Text(initialLetter)
                    .font(.system(size: 22, weight: .black))
                    .foregroundColor(Color(hex: "065F46"))
            }
            
            VStack(alignment: .leading, spacing: 3) {
                Text(displayName)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                
                HStack(spacing: 8) {
                    Text(maskedPhone)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(Color(hex: "475569"))
                    
                    // Verified Badge
                    HStack(spacing: 3) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 10))
                            .foregroundColor(Color(hex: "166534"))
                        Text("Verified")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Color(hex: "166534"))
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color(hex: "DCFCE7"))
                    .cornerRadius(6)
                }
            }
            
            Spacer()
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.02), radius: 4, x: 0, y: 1)
    }
    
    private var corePillarsRow: some View {
        HStack(spacing: 10) {
            // Pillar 1: Orders
            pillarCard(
                icon: "cart.fill",
                title: "Your Orders",
                subtitle: "\(orderRepository.customerOrders.count) Placed",
                badgeColor: Color(hex: "2563EB")
            ) {
                activeSheet = .orders
            }
            
            // Pillar 2: Prescriptions
            pillarCard(
                icon: "doc.text.fill",
                title: "Prescriptions",
                subtitle: "Vault",
                badgeColor: Color(hex: "059669")
            ) {
                activeSheet = .prescriptions
            }
            
            // Pillar 3: Addresses
            pillarCard(
                icon: "mappin.and.ellipse",
                title: "Addresses",
                subtitle: "Saved",
                badgeColor: Color(hex: "D97706")
            ) {
                activeSheet = .addresses
            }
        }
    }
    
    private func pillarCard(
        icon: String,
        title: String,
        subtitle: String,
        badgeColor: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(badgeColor.opacity(0.12))
                        .frame(width: 36, height: 36)
                    
                    Image(systemName: icon)
                        .font(.system(size: 17))
                        .foregroundColor(badgeColor)
                }
                
                Spacer(minLength: 4)
                
                Text(title)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                    .lineLimit(1)
                
                Text(subtitle)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color(hex: "64748B"))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 10)
            .padding(.vertical, 12)
            .background(Color.white)
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.02), radius: 4, x: 0, y: 1)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var storeAndPharmacyServicesCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("PHARMACY SERVICES & STORE HELPDESK")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(Color(hex: "94A3B8"))
                .padding(.horizontal, 16)
                .padding(.top, 14)
                .padding(.bottom, 8)
            
            // Row 1: Refill Reminders
            serviceMenuRow(
                icon: "calendar.badge.clock",
                title: "Medicine Refill Reminders",
                subtitle: "Automatic schedules for recurring prescriptions"
            ) {
                showRefillAlert = true
            }
            
            Divider()
                .padding(.leading, 56)
            
            // Row 2: Pharmacy Store Contact
            serviceMenuRow(
                icon: "phone.fill",
                title: "Pharmacy Help & Store Contact",
                subtitle: "Call store directly: \(storeContactPhone)"
            ) {
                if let url = URL(string: "tel://18002089999") {
                    UIApplication.shared.open(url)
                }
            }
            
            Divider()
                .padding(.leading, 56)
            
            // Row 3: Server & Backend Settings
            serviceMenuRow(
                icon: "gearshape.fill",
                title: "Server & Developer Settings",
                subtitle: "Configure API endpoints and environment"
            ) {
                activeSheet = .serverSettings
            }
        }
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.02), radius: 4, x: 0, y: 1)
    }
    
    private func serviceMenuRow(
        icon: String,
        title: String,
        subtitle: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color(hex: "F8FAFC"))
                        .frame(width: 34, height: 34)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color(hex: "E2E8F0"), lineWidth: 0.5)
                        )
                    
                    Image(systemName: icon)
                        .font(.system(size: 15))
                        .foregroundColor(Color(hex: "059669"))
                }
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Color(hex: "0F172A"))
                    Text(subtitle)
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: "64748B"))
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color(hex: "94A3B8"))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var pharmacyLicenseFooter: some View {
        VStack(spacing: 3) {
            Text("CommerceOS Health • Central Licensed Pharmacy")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Color(hex: "64748B"))
            Text("Drug License: DL-HR-2024-009182 • WHO-GMP Certified Hub")
                .font(.system(size: 10))
                .foregroundColor(Color(hex: "94A3B8"))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
    }
    
    private var logoutButton: some View {
        Button(action: {
            container.logout()
            dismiss()
        }) {
            HStack(spacing: 8) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(hex: "DC2626"))
                
                Text("Log Out of Account")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(hex: "DC2626"))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(Color.white)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "FCA5A5"), lineWidth: 1)
            )
        }
    }
}
