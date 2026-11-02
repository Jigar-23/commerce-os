import SwiftUI
import MapKit

/**
 * 1:1 Parity with Android AddAddressFlow.kt and Zomato-Grade Address Experience.
 * Implements:
 * - 120Hz native Apple MapKit viewport with Zomato physical spring pin & drop shadow
 * - Real-time Google Places / Apple Maps autocomplete search overlay
 * - Floating "Use current location" GPS lock button
 * - Sliding bottom sheet with 2-tier locality hierarchy & smooth cross-fades
 * - Complete structured address form:
 *     * House / Flat / Society details with in-field clear button
 *     * Tag selector (Home, Work, Other)
 *     * Recipient selector (Myself vs Someone else)
 *     * 10-digit delivery contact phone validation
 *     * 6-digit India PIN code validation
 *     * Conflict detection & resolution banner
 *     * Delivery entrance selector (Main Gate, Gate 2, etc.)
 *     * Delivery instructions preset chips
 *     * Default address checkbox
 *     * Validation-bound Save address button in #0E675E
 */
public struct AddAddressFlowView: View {
    @Environment(\.presentationMode) private var presentationMode
    @ObservedObject var viewModel: AddressViewModel = .shared

    let onSaveAddress: (AddressDto) -> Void

    @State private var isForSomeoneElse: Bool = false
    @State private var makeDefault: Bool = false
    @State private var sheetHeight: CGFloat = 360
    @State private var isShowingSearchOverlay: Bool = false
    @State private var isMapMoving: Bool = false

    public init(onSaveAddress: @escaping (AddressDto) -> Void = { _ in }) {
        self.onSaveAddress = onSaveAddress
    }

    private var currentForm: StructuredAddress {
        viewModel.platformUiState.draftAddress
    }

    private var validationResult: AddressValidationResult {
        currentForm.validate()
    }

    private var conflictWarning: AddressConflictWarning {
        viewModel.platformUiState.addressConflict
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Top Navigation & Search Bar Header
            VStack(spacing: 8) {
                HStack(spacing: 12) {
                    Button(action: {
                        if isShowingSearchOverlay {
                            isShowingSearchOverlay = false
                            viewModel.searchLocationPlaces("")
                        } else {
                            viewModel.navigateBack()
                            presentationMode.wrappedValue.dismiss()
                        }
                    }) {
                        Image(systemName: "arrow.backward")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                            .padding(6)
                    }

                    Text("Select delivery location")
                        .font(CommerceOSTheme.Typography.titleMedium())
                        .foregroundColor(Color(hex: "0F172A"))

                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                // Search Bar
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(CommerceOSTheme.Colors.brandPrimary)

                    TextField("Search for area, street name...", text: Binding(
                        get: { viewModel.platformUiState.locationSearchQuery },
                        set: {
                            viewModel.searchLocationPlaces($0)
                            isShowingSearchOverlay = $0.trimmingCharacters(in: .whitespaces).count >= 2
                        }
                    ))
                    .font(.system(size: 13, weight: .medium))

                    if !viewModel.platformUiState.locationSearchQuery.isEmpty {
                        Button(action: {
                            viewModel.searchLocationPlaces("")
                            isShowingSearchOverlay = false
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 15))
                                .foregroundColor(Color(hex: "64748B"))
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Color(hex: "F8FAFC"))
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )
                .padding(.horizontal, 16)
                .padding(.bottom, 6)
            }
            .background(Color.white)

            // Map Area & Bottom Form Sheet
            ZStack(alignment: .bottom) {
                // Interactive 120Hz Native MapKit Viewport
                ZStack {
                    LocationMapViewport(
                        centerCoordinate: currentForm.geoLocation?.coordinate,
                        onCameraMoveStateChanged: { moving in
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.65)) {
                                isMapMoving = moving
                            }
                        },
                        onCameraSettled: { coord in
                            viewModel.onMapCameraSettled(lat: coord.latitude, lng: coord.longitude)
                        }
                    )
                    .ignoresSafeArea(.keyboard, edges: .bottom)

                    // Zomato-Grade Grounded Center Pin with Levitation & Landing Bounce
                    ZomatoDeliveryPinView(isMoving: isMapMoving)
                        .offset(y: -20)

                    // Floating "Use current location" GPS Pill
                    VStack {
                        Spacer()
                        Button(action: {
                            HapticFeedbackEngine.shared.triggerImpact(.medium)
                            viewModel.requestCurrentGpsLocation()
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: "location.fill")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                                Text("Use current location")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.white)
                            .cornerRadius(24)
                            .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 3)
                            .overlay(
                                RoundedRectangle(cornerRadius: 24)
                                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                            )
                        }
                        .padding(.bottom, sheetHeight + 12)
                    }
                }

                // Search Results Overlay (if searching)
                if isShowingSearchOverlay {
                    VStack(spacing: 0) {
                        if viewModel.platformUiState.isSearchingPlaces {
                            HStack {
                                Spacer()
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: CommerceOSTheme.Colors.brandPrimary))
                                    .padding(24)
                                Spacer()
                            }
                        } else if viewModel.platformUiState.placeSearchResults.isEmpty {
                            Text("No areas found. Try a landmark or city name.")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(Color(hex: "64748B"))
                                .padding(16)
                        } else {
                            ScrollView {
                                LazyVStack(spacing: 8) {
                                    ForEach(viewModel.platformUiState.placeSearchResults) { result in
                                        Button(action: {
                                            viewModel.selectPlaceSearchResult(result)
                                            isShowingSearchOverlay = false
                                        }) {
                                            HStack(spacing: 12) {
                                                Image(systemName: "location.fill")
                                                    .font(.system(size: 16))
                                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimary)

                                                VStack(alignment: .leading, spacing: 2) {
                                                    Text(result.primaryText)
                                                        .font(.system(size: 14, weight: .bold))
                                                        .foregroundColor(Color(hex: "0F172A"))
                                                    Text(result.secondaryText)
                                                        .font(.system(size: 12, weight: .medium))
                                                        .foregroundColor(Color(hex: "64748B"))
                                                        .lineLimit(1)
                                                }

                                                Spacer()
                                            }
                                            .padding(12)
                                            .background(Color(hex: "F8FAFC"))
                                            .cornerRadius(12)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 12)
                                                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                                            )
                                        }
                                        .buttonStyle(PlainButtonStyle())
                                    }
                                }
                                .padding(16)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.white)
                }

                // Sliding Bottom Sheet with Complete Delivery Details Form
                if !isShowingSearchOverlay {
                    VStack(spacing: 0) {
                        // Drag Handle
                        Capsule()
                            .fill(Color(hex: "CBD5E1"))
                            .frame(width: 44, height: 4.5)
                            .padding(.top, 10)
                            .padding(.bottom, 6)

                        ScrollView {
                            VStack(alignment: .leading, spacing: 14) {
                                Text("Delivery details")
                                    .font(CommerceOSTheme.Typography.titleMedium())
                                    .foregroundColor(Color(hex: "0F172A"))

                                // 2-Tier Location Card (Clickable to re-search)
                                Button(action: {
                                    isShowingSearchOverlay = true
                                    viewModel.searchLocationPlaces(" ")
                                }) {
                                    HStack(spacing: 12) {
                                        ZStack {
                                            Circle()
                                                .fill(CommerceOSTheme.Colors.brandPrimarySoft)
                                                .frame(width: 36, height: 36)
                                            Image(systemName: "location.fill")
                                                .font(.system(size: 16, weight: .bold))
                                                .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                                        }

                                        VStack(alignment: .leading, spacing: 2) {
                                            let primaryTitle: String = {
                                                if isMapMoving || viewModel.platformUiState.isReverseGeocoding {
                                                    return "Detecting location..."
                                                }
                                                return currentForm.subLocality.isEmpty ?
                                                    (currentForm.street.isEmpty ? (currentForm.locality.isEmpty ? "Selected Locality" : currentForm.locality) : currentForm.street) :
                                                    currentForm.subLocality
                                            }()

                                            let secondarySubtitle: String = {
                                                if isMapMoving || viewModel.platformUiState.isReverseGeocoding {
                                                    return "Pinpointing area on map..."
                                                }
                                                var parts: [String] = []
                                                if !currentForm.city.isEmpty { parts.append(currentForm.city) }
                                                if !currentForm.state.isEmpty { parts.append(currentForm.state) }
                                                if !currentForm.postalCode.isEmpty { parts.append(currentForm.postalCode) }
                                                return parts.joined(separator: ", ").isEmpty ? "India" : parts.joined(separator: ", ")
                                            }()

                                            Text(primaryTitle)
                                                .font(.system(size: 15, weight: .bold))
                                                .foregroundColor(Color(hex: "0F172A"))
                                                .lineLimit(1)

                                            Text(secondarySubtitle)
                                                .font(.system(size: 12, weight: .medium))
                                                .foregroundColor(Color(hex: "64748B"))
                                                .lineLimit(1)
                                        }

                                        Spacer()

                                        Text("Change")
                                            .font(.system(size: 12, weight: .bold))
                                            .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 4)
                                            .background(CommerceOSTheme.Colors.brandPrimarySoft)
                                            .cornerRadius(6)
                                    }
                                    .padding(12)
                                    .background(Color(hex: "F8FAFC"))
                                    .cornerRadius(14)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14)
                                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                                    )
                                }
                                .buttonStyle(PlainButtonStyle())

                                // Address Details* (House / Floor / Apartment) with In-Field Clear
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Address details*")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(Color(hex: "64748B"))

                                    HStack {
                                        TextField("House / Flat / Block / Floor", text: Binding(
                                            get: { currentForm.houseNumber },
                                            set: {
                                                var copy = currentForm
                                                copy.houseNumber = $0
                                                viewModel.updateFormAddress(copy)
                                            }
                                        ))
                                        .font(.system(size: 14, weight: .medium))

                                        if !currentForm.houseNumber.isEmpty {
                                            Button(action: {
                                                var copy = currentForm
                                                copy.houseNumber = ""
                                                viewModel.updateFormAddress(copy)
                                            }) {
                                                Image(systemName: "xmark.circle.fill")
                                                    .font(.system(size: 16))
                                                    .foregroundColor(Color(hex: "94A3B8"))
                                            }
                                        }
                                    }
                                    .padding(12)
                                    .background(Color.white)
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(validationResult.houseNumberError != nil ? Color(hex: "EF4444") : Color(hex: "CBD5E1"), lineWidth: 1)
                                    )

                                    Text("E.g. A-504, 5th Floor, Shanti Heights, Near City Mall")
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(Color(hex: "64748B"))
                                        .padding(.leading, 4)
                                }

                                // Save address as (Home / Work / Other tags)
                                AddressTagSelector(
                                    selectedTag: currentForm.tag,
                                    onTagSelected: { tag in
                                        var copy = currentForm
                                        copy.tag = tag
                                        viewModel.updateFormAddress(copy)
                                    }
                                )

                                // Contact Details (RecipientSelector 1:1 Android Parity)
                                RecipientSelector(
                                    recipientType: currentForm.recipientType,
                                    contactName: currentForm.contactName,
                                    contactPhone: currentForm.contactPhone,
                                    nameError: validationResult.contactNameError,
                                    phoneError: validationResult.contactPhoneError,
                                    onRecipientTypeChanged: { rType in
                                        isForSomeoneElse = (rType == .someoneElse)
                                        var copy = currentForm
                                        copy.recipientType = rType
                                        if rType == .me {
                                            copy.contactName = ""
                                        }
                                        viewModel.updateFormAddress(copy)
                                    },
                                    onContactNameChanged: { name in
                                        var copy = currentForm
                                        copy.contactName = name
                                        viewModel.updateFormAddress(copy)
                                    },
                                    onContactPhoneChanged: { phone in
                                        var copy = currentForm
                                        copy.contactPhone = phone
                                        viewModel.updateFormAddress(copy)
                                    }
                                )

                                // Conflict Warning Alert Banner (Address ↔ Geocode Conflict)
                                if conflictWarning.hasConflict {
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack(spacing: 6) {
                                            Image(systemName: "exclamationmark.triangle.fill")
                                                .font(.system(size: 14))
                                                .foregroundColor(Color(hex: "D97706"))
                                            Text("Address details conflict with selected map location.")
                                                .font(.system(size: 12, weight: .bold))
                                                .foregroundColor(Color(hex: "92400E"))
                                        }

                                        if let msg = conflictWarning.message {
                                            Text(msg)
                                                .font(.system(size: 11, weight: .medium))
                                                .foregroundColor(Color(hex: "78350F"))
                                        }

                                        Button(action: {
                                            viewModel.applyConflictSuggestion()
                                        }) {
                                            Text("Use map location")
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundColor(.white)
                                                .padding(.horizontal, 10)
                                                .padding(.vertical, 5)
                                                .background(Color(hex: "D97706"))
                                                .cornerRadius(6)
                                        }
                                        .padding(.top, 2)
                                    }
                                    .padding(12)
                                    .background(Color(hex: "FEF3C7"))
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(Color(hex: "FDE68A"), lineWidth: 1)
                                    )
                                }

                                // Delivery Entrance Selector
                                DeliveryEntranceSelector(
                                    selectedEntrance: currentForm.entrance,
                                    customEntranceDetails: currentForm.customEntranceDetails,
                                    customEntranceDetailsError: validationResult.customEntranceDetailsError,
                                    onEntranceSelected: { entrance in
                                        var copy = currentForm
                                        copy.entrance = entrance
                                        viewModel.updateFormAddress(copy)
                                    },
                                    onCustomDetailsChanged: { details in
                                        var copy = currentForm
                                        copy.customEntranceDetails = details
                                        viewModel.updateFormAddress(copy)
                                    }
                                )

                                // Delivery Instructions Field
                                DeliveryInstructionsField(
                                    instructions: currentForm.deliveryInstructions,
                                    onInstructionsChanged: { inst in
                                        var copy = currentForm
                                        copy.deliveryInstructions = inst
                                        viewModel.updateFormAddress(copy)
                                    }
                                )

                                // Make default delivery address toggle
                                Button(action: {
                                    makeDefault.toggle()
                                    var copy = currentForm
                                    copy.isDefault = makeDefault
                                    viewModel.updateFormAddress(copy)
                                }) {
                                    HStack(spacing: 8) {
                                        Image(systemName: makeDefault ? "checkmark.square.fill" : "square")
                                            .font(.system(size: 16))
                                            .foregroundColor(makeDefault ? CommerceOSTheme.Colors.brandPrimary : Color(hex: "94A3B8"))
                                        Text("Make this my default delivery address")
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundColor(Color(hex: "0F172A"))
                                        Spacer()
                                    }
                                }
                                .buttonStyle(PlainButtonStyle())
                                .padding(.vertical, 4)

                                // Save Address Button
                                Button(action: {
                                    HapticFeedbackEngine.shared.triggerImpact(.medium)
                                    viewModel.submitSaveFormAddress { success in
                                        if success {
                                            if let saved = viewModel.selectedAddress {
                                                onSaveAddress(saved)
                                            }
                                            presentationMode.wrappedValue.dismiss()
                                        }
                                    }
                                }) {
                                    HStack(spacing: 8) {
                                        if viewModel.platformUiState.saveState == .saving {
                                            ProgressView()
                                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                            Text("Saving address...")
                                                .font(.system(size: 15, weight: .bold))
                                                .foregroundColor(.white)
                                        } else {
                                            Text("Save address")
                                                .font(.system(size: 15, weight: .bold))
                                                .foregroundColor(validationResult.isValid ? .white : Color(hex: "94A3B8"))
                                        }
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 50)
                                    .background(validationResult.isValid ? CommerceOSTheme.Colors.brandPrimary : Color(hex: "E2E8F0"))
                                    .cornerRadius(12)
                                }
                                .disabled(!validationResult.isValid || viewModel.platformUiState.saveState == .saving)
                                .padding(.bottom, 24)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 4)
                        }
                    }
                    .frame(height: sheetHeight)
                    .background(Color.white)
                    .cornerRadius(24, corners: [.topLeft, .topRight])
                    .shadow(color: Color.black.opacity(0.14), radius: 16, x: 0, y: -4)
                }
            }
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear {
            let existingPhone = currentForm.contactPhone
            if !existingPhone.isEmpty {
                isForSomeoneElse = currentForm.recipientType == .someoneElse
            }
            makeDefault = currentForm.isDefault
        }
    }
}
