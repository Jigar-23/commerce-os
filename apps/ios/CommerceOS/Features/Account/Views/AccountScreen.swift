import SwiftUI
import UIKit

/**
 * 1:1 Parity with Android AccountScreen.kt (1,077 lines).
 * Production Healthcare & Pharmacy Profile & Account Management Screen.
 * 
 * Features:
 * 1. Customer Identification Card with on-device photo & name editing, verified mobile badge, and Fast Pharmacy banner.
 * 2. Dual Hero Action Tiles (My Orders with real-time placed count vs 24x7 Need Help Pharmacist hotline).
 * 3. Prescriptions Vault feature row.
 * 4. Manage Account & Healthcare group (Addresses, Notifications, Refill Reminders, Registered Pharmacy Helpdesk).
 * 5. Central Licensed Pharmacy Footer (DL-HR-2024-009182 WHO-GMP notice).
 * 6. High-assurance Logout with session cleanup.
 */
public struct AccountScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var orderRepository: OrderRepository
    @EnvironmentObject private var addressRepository: AddressRepository
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @ObservedObject private var navigator = AppNavigator.shared
    
    @State private var localName: String? = nil
    @State private var avatarImage: UIImage? = nil
    @State private var showEditProfileDialog: Bool = false
    @State private var showRefillDialog: Bool = false
    @State private var showLogoutConfirm: Bool = false
    
    // Edit Profile Sheet State
    @State private var editNameInput: String = ""
    @State private var showingImagePicker: Bool = false
    @State private var imagePickerSourceType: UIImagePickerController.SourceType = .photoLibrary
    
    private let storeContactPhone: String = "+91 1800-208-9999"
    
    public init() {}
    
    private var effectiveCustomerId: String {
        if let id = container.customerSession?.customerId, !id.isEmpty {
            return id
        }
        let digits = (container.customerSession?.phone ?? "").filter { $0.isNumber }
        return digits.isEmpty ? "default_user" : digits
    }
    
    private var hasCustomName: Bool {
        guard let name = localName?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty else {
            return false
        }
        return !name.lowercased().hasPrefix("customer")
    }
    
    private var rawPhone: String {
        container.customerSession?.phone ?? ""
    }
    
    private var formattedPhone: String {
        let digits = rawPhone.filter { $0.isNumber }
        if digits.count >= 10 {
            let last10 = String(digits.suffix(10))
            let prefix = last10.prefix(5)
            let suffix = last10.suffix(5)
            return "+91 \(prefix) \(suffix)"
        }
        return digits.isEmpty ? "+91 Verified User" : "+91 \(digits)"
    }
    
    private var displayName: String {
        if hasCustomName, let name = localName?.trimmingCharacters(in: .whitespacesAndNewlines) {
            return name
        }
        if let sessionName = container.customerSession?.name?.trimmingCharacters(in: .whitespacesAndNewlines), !sessionName.isEmpty, !sessionName.lowercased().hasPrefix("customer") {
            return sessionName
        }
        return formattedPhone
    }
    
    private var initialLetter: String {
        String(displayName.trimmingCharacters(in: .whitespaces).first ?? "U").uppercased()
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            // Top Header matching Home & Orders
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Profile & Account")
                        .font(.system(size: 20, weight: .black))
                        .foregroundColor(Color(hex: "0F172A"))
                    Text("Manage your healthcare orders, addresses & prescriptions")
                        .font(.system(size: 11.5))
                        .foregroundColor(Color(hex: "64748B"))
                        .lineLimit(1)
                }
                
                Spacer()
                
                // Notification Bell Button (Comfortable 42pt circle)
                Button(action: {
                    navigator.push(.notifications)
                }) {
                    ZStack {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 42, height: 42)
                            .overlay(
                                Circle()
                                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                            )
                            .shadow(color: Color.black.opacity(0.04), radius: 2, x: 0, y: 1)
                        
                        Image(systemName: "bell.fill")
                            .font(.system(size: 17))
                            .foregroundColor(Color(hex: "0E675E"))
                        
                        if LocalNotificationStorage.shared.notifications.contains(where: { !$0.isRead }) {
                            Circle()
                                .fill(Color(hex: "DC2626"))
                                .frame(width: 8, height: 8)
                                .offset(x: 10, y: -10)
                        }
                    }
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 12)
            
            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    // 1. Customer Identification Card with Edit Button & On-Device Avatar
                    customerIdentificationCard
                    
                    // 2. Dual Hero Action Tiles (My Orders vs Need Help? — PharmEasy Benchmark)
                    dualHeroActionTiles
                    
                    // 3. Healthcare Hub Grouped Features (Prescriptions Vault)
                    prescriptionsVaultCard
                    
                    // 4. Manage Account & Preferences (Addresses, Notifications, Refill Reminders, Contact)
                    manageAccountPreferencesCard
                    
                    // 5. Central Licensed Pharmacy Footer
                    centralLicensedPharmacyFooter
                    
                    // 6. Professional Logout Button
                    logoutButton
                    
                    Spacer(minLength: 32)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
        }
        .background(Color(hex: "F4F6F8").ignoresSafeArea())
        .onAppear {
            refreshLocalProfile()
        }
        .sheet(isPresented: $showEditProfileDialog) {
            editProfileSheetView
        }
        .sheet(isPresented: $showingImagePicker) {
            ImagePicker(sourceType: imagePickerSourceType) { selectedImage in
                if let img = selectedImage {
                    _ = LocalProfileManager.shared.saveAvatarImage(customerId: effectiveCustomerId, image: img)
                    avatarImage = img
                }
            }
        }
        .alert("Medicine Refill Reminders", isPresented: $showRefillDialog) {
            Button("Got it", role: .cancel) {}
        } message: {
            Text("Automatic reminders are active for your recurring healthcare prescriptions. You will receive an SMS and in-app reminder 3 days before your dosage runs low to ensure uninterrupted care.")
        }
        .alert("Log Out", isPresented: $showLogoutConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Log Out", role: .destructive) {
                executeLogout()
            }
        } message: {
            Text("Are you sure you want to log out of CommerceOS?")
        }
    }
    
    // MARK: - 1. Customer Identification Card
    private var customerIdentificationCard: some View {
        VStack(spacing: 12) {
            HStack(spacing: 14) {
                // Profile Avatar with camera/edit badge
                Button(action: {
                    openEditProfileDialog()
                }) {
                    ZStack(alignment: .bottomTrailing) {
                        if let img = avatarImage {
                            Image(uiImage: img)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 56, height: 56)
                                .clipShape(Circle())
                                .overlay(
                                    Circle()
                                        .stroke(Color(hex: "0E675E"), lineWidth: 1.5)
                                )
                        } else if hasCustomName {
                            Circle()
                                .fill(Color(hex: "ECFDF5"))
                                .frame(width: 56, height: 56)
                                .overlay(
                                    Circle()
                                        .stroke(Color(hex: "0E675E"), lineWidth: 1.5)
                                )
                                .overlay(
                                    Text(initialLetter)
                                        .font(.system(size: 22, weight: .black))
                                        .foregroundColor(Color(hex: "0E675E"))
                                )
                        } else {
                            Circle()
                                .fill(Color(hex: "ECFDF5"))
                                .frame(width: 56, height: 56)
                                .overlay(
                                    Circle()
                                        .stroke(Color(hex: "0E675E"), lineWidth: 1.5)
                                )
                                .overlay(
                                    Image(systemName: "person.fill")
                                        .font(.system(size: 24))
                                        .foregroundColor(Color(hex: "0E675E"))
                                )
                        }

                        // Edit overlay badge
                        Circle()
                            .fill(Color(hex: "0E675E"))
                            .frame(width: 20, height: 20)
                            .overlay(
                                Image(systemName: "pencil")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.white)
                            )
                    }
                }
                .buttonStyle(PlainButtonStyle())

                // Name & Phone
                VStack(alignment: .leading, spacing: 2) {
                    if hasCustomName {
                        Text(displayName)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text(formattedPhone)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Color(hex: "64748B"))
                    } else {
                        Text(formattedPhone)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Button(action: openEditProfileDialog) {
                            Text("+ Add your name")
                                .font(.system(size: 12.5, weight: .bold))
                                .foregroundColor(Color(hex: "0E675E"))
                        }
                    }
                }

                Spacer()

                // Edit Profile Button
                Button(action: openEditProfileDialog) {
                    HStack(spacing: 4) {
                        Image(systemName: "pencil")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(hex: "0E675E"))
                        Text("Edit")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color(hex: "CBD5E1"), lineWidth: 1)
                    )
                    .cornerRadius(8)
                }
                .buttonStyle(PlainButtonStyle())
            }

            // PharmEasy Style Inner Savings Banner
            HStack(spacing: 8) {
                Text("⚡")
                    .font(.system(size: 13))
                VStack(alignment: .leading, spacing: 1) {
                    Text("CommerceOS Fast Pharmacy")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "1E40AF"))
                    Text("10-Minute SLA Delivery • Certified Pharmacists")
                        .font(.system(size: 10.5))
                        .foregroundColor(Color(hex: "3B82F6"))
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(hex: "1E40AF"))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(Color(hex: "EFF6FF"))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color(hex: "DBEAFE"), lineWidth: 1)
            )
            .cornerRadius(10)
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
    }
    
    // MARK: - 2. Dual Hero Action Tiles (My Orders vs Need Help?)
    private var dualHeroActionTiles: some View {
        HStack(spacing: 10) {
            // Tile 1: My Orders
            Button(action: {
                navigator.switchTab(to: 2)
            }) {
                HStack(spacing: 10) {
                    Circle()
                        .fill(Color(hex: "EFF6FF"))
                        .frame(width: 40, height: 40)
                        .overlay(
                            Image(systemName: "bag.fill")
                                .font(.system(size: 18))
                                .foregroundColor(Color(hex: "2563EB"))
                        )

                    VStack(alignment: .leading, spacing: 2) {
                        Text("My Orders")
                            .font(.system(size: 13.5, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text(orderRepository.customerOrders.isEmpty ? "Track & reorder" : "\(orderRepository.customerOrders.count) placed")
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                    Spacer()
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(16)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
            }
            .buttonStyle(PlainButtonStyle())

            // Tile 2: Need Help?
            Button(action: {
                if let url = URL(string: "tel:\(storeContactPhone.filter { $0.isNumber || $0 == "+" })") {
                    UIApplication.shared.open(url)
                }
            }) {
                HStack(spacing: 10) {
                    Circle()
                        .fill(Color(hex: "ECFDF5"))
                        .frame(width: 40, height: 40)
                        .overlay(
                            Image(systemName: "headphones")
                                .font(.system(size: 18))
                                .foregroundColor(Color(hex: "0E675E"))
                        )

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Need Help?")
                            .font(.system(size: 13.5, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("24×7 support")
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                    Spacer()
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(16)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
            }
            .buttonStyle(PlainButtonStyle())
        }
    }
    
    // MARK: - 3. Prescriptions Vault Card
    private var prescriptionsVaultCard: some View {
        Button(action: {
            navigator.push(.prescriptions)
        }) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(hex: "ECFDF5"))
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: "doc.text.fill")
                            .font(.system(size: 17))
                            .foregroundColor(Color(hex: "0E675E"))
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text("Prescriptions Vault")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                    Text("Manage verified doctor slips & chronic refilling")
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: "64748B"))
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color(hex: "94A3B8"))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Color.white)
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    // MARK: - 4. Manage Account & Preferences
    private var manageAccountPreferencesCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("MANAGE ACCOUNT & HEALTHCARE")
                .font(.system(size: 10.5, weight: .black))
                .foregroundColor(Color(hex: "94A3B8"))
                .tracking(0.6)
                .padding(.horizontal, 16)
                .padding(.top, 14)
                .padding(.bottom, 8)

            // Row 1: Manage Addresses
            profileMenuRow(
                icon: "mappin.and.ellipse",
                iconColor: Color(hex: "059669"),
                title: "Manage Addresses",
                subtitle: "\(addressRepository.addresses.count) delivery address(es) saved",
                action: {
                    navigator.push(.addressSelection(fromCheckout: false, fromProfile: true))
                }
            )

            Divider().padding(.horizontal, 16)

            // Row 2: Notification Preferences
            profileMenuRow(
                icon: "bell.fill",
                iconColor: Color(hex: "0E675E"),
                title: "Notification Preferences",
                subtitle: "Order updates, OTP & delivery SMS",
                action: {
                    navigator.push(.notifications)
                }
            )

            Divider().padding(.horizontal, 16)

            // Row 3: Medicine Refill Reminders
            profileMenuRow(
                icon: "calendar.badge.clock",
                iconColor: Color(hex: "2563EB"),
                title: "Medicine Refill Reminders",
                subtitle: "Automatic schedules for recurring prescriptions",
                action: {
                    showRefillDialog = true
                }
            )

            Divider().padding(.horizontal, 16)

            // Row 4: Pharmacy Helpdesk
            profileMenuRow(
                icon: "phone.fill",
                iconColor: Color(hex: "0E675E"),
                title: "Pharmacy Helpdesk",
                subtitle: "Call registered pharmacist: \(storeContactPhone)",
                action: {
                    if let url = URL(string: "tel:\(storeContactPhone.filter { $0.isNumber || $0 == "+" })") {
                        UIApplication.shared.open(url)
                    }
                }
            )
        }
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
    }

    private func profileMenuRow(
        icon: String,
        iconColor: Color,
        title: String,
        subtitle: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Circle()
                    .fill(Color(hex: "F8FAFC"))
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: icon)
                            .font(.system(size: 15))
                            .foregroundColor(iconColor)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 13.5, weight: .bold))
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
    
    // MARK: - 5. Central Licensed Pharmacy Footer
    private var centralLicensedPharmacyFooter: some View {
        VStack(spacing: 3) {
            Text("CommerceOS Health • Central Licensed Pharmacy")
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundColor(Color(hex: "64748B"))
            Text("Drug License: DL-HR-2024-009182 • WHO-GMP Certified Quality")
                .font(.system(size: 10))
                .foregroundColor(Color(hex: "94A3B8"))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
    }
    
    // MARK: - 6. Professional Logout Button
    private var logoutButton: some View {
        Button(action: {
            showLogoutConfirm = true
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
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "FCA5A5"), lineWidth: 1)
            )
            .cornerRadius(12)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    // MARK: - Edit Profile Sheet
    private var editProfileSheetView: some View {
        NavigationView {
            VStack(spacing: 20) {
                // Avatar preview with change photo actions
                VStack(spacing: 10) {
                    if let img = avatarImage {
                        Image(uiImage: img)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 80, height: 80)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color(hex: "0E675E"), lineWidth: 2))
                    } else {
                        Circle()
                            .fill(Color(hex: "ECFDF5"))
                            .frame(width: 80, height: 80)
                            .overlay(Circle().stroke(Color(hex: "0E675E"), lineWidth: 2))
                            .overlay(
                                Image(systemName: "person.fill")
                                    .font(.system(size: 36))
                                    .foregroundColor(Color(hex: "0E675E"))
                            )
                    }

                    HStack(spacing: 12) {
                        Button(action: {
                            imagePickerSourceType = .photoLibrary
                            showingImagePicker = true
                        }) {
                            Text("Choose Photo")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "0E675E"))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color(hex: "ECFDF5"))
                                .cornerRadius(6)
                        }

                        if avatarImage != nil {
                            Button(action: {
                                LocalProfileManager.shared.removeAvatar(customerId: effectiveCustomerId)
                                avatarImage = nil
                            }) {
                                Text("Remove")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Color(hex: "DC2626"))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(Color(hex: "FEE2E2"))
                                    .cornerRadius(6)
                            }
                        }
                    }
                }
                .padding(.top, 16)

                // Name TextField with explicit dark color
                VStack(alignment: .leading, spacing: 6) {
                    Text("Full Name")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))

                    TextField("Enter your full name", text: $editNameInput)
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "0F172A"))
                        .accentColor(Color(hex: "0E675E"))
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color(hex: "CBD5E1"), lineWidth: 1)
                        )
                }
                .padding(.horizontal, 16)

                // Phone Display (read-only)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Verified Mobile Number")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))

                    HStack {
                        Text(formattedPhone)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(Color(hex: "64748B"))
                        Spacer()
                        Image(systemName: "checkmark.seal.fill")
                            .foregroundColor(Color(hex: "059669"))
                    }
                    .padding(12)
                    .background(Color(hex: "F8FAFC"))
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                }
                .padding(.horizontal, 16)

                Spacer()
            }
            .navigationTitle("Edit Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        showEditProfileDialog = false
                    }
                    .foregroundColor(Color(hex: "64748B"))
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        saveProfileChanges()
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(hex: "0E675E"))
                }
            }
        }
    }

    private func openEditProfileDialog() {
        editNameInput = localName ?? ""
        showEditProfileDialog = true
    }

    private func saveProfileChanges() {
        let clean = editNameInput.trimmingCharacters(in: .whitespacesAndNewlines)
        LocalProfileManager.shared.saveCustomName(customerId: effectiveCustomerId, name: clean)
        localName = clean.isEmpty ? nil : clean
        showEditProfileDialog = false
    }

    private func refreshLocalProfile() {
        localName = LocalProfileManager.shared.getCustomName(customerId: effectiveCustomerId)
        avatarImage = LocalProfileManager.shared.getAvatarImage(customerId: effectiveCustomerId)
    }

    private func executeLogout() {
        container.logout()
        LocalNotificationStorage.shared.clear()
        navigator.switchTab(to: 0)
    }
}

// MARK: - Native ImagePicker Helper
public struct ImagePicker: UIViewControllerRepresentable {
    public var sourceType: UIImagePickerController.SourceType = .photoLibrary
    public var onImagePicked: (UIImage?) -> Void
    @Environment(\.presentationMode) private var presentationMode

    public init(sourceType: UIImagePickerController.SourceType = .photoLibrary, onImagePicked: @escaping (UIImage?) -> Void) {
        self.sourceType = sourceType
        self.onImagePicked = onImagePicked
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    public func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = sourceType
        picker.allowsEditing = true
        return picker
    }

    public func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    public final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: ImagePicker

        init(_ parent: ImagePicker) {
            self.parent = parent
        }

        public func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = (info[.editedImage] as? UIImage) ?? (info[.originalImage] as? UIImage) {
                parent.onImagePicked(image)
            } else {
                parent.onImagePicked(nil)
            }
            parent.presentationMode.wrappedValue.dismiss()
        }

        public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onImagePicked(nil)
            parent.presentationMode.wrappedValue.dismiss()
        }
    }
}
