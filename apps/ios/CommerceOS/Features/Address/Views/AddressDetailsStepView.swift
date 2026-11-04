import SwiftUI

/**
 * 1:1 Parity with Android AddressDetailsStep.kt
 * Complete structured address form:
 * - Geocode conflict alert banner with one-tap sync
 * - Tag selector (Home, Work, Other, Friends)
 * - House / Flat / Society details with field-level validation
 * - Delivery entrance selector
 * - Delivery instructions preset chips
 * - Recipient selector (Myself vs Someone else)
 * - Default address toggle
 * - Save Address action button
 */
public struct AddressDetailsStepView: View {
    public let formAddress: StructuredAddress
    public let conflictWarning: AddressConflictWarning?
    public let validationResult: AddressValidationResult
    public let isSaving: Bool
    public let onFormAddressChanged: (StructuredAddress) -> Void
    public let onApplyConflictSuggestion: () -> Void
    public let onSaveSubmitted: () -> Void

    public init(
        formAddress: StructuredAddress,
        conflictWarning: AddressConflictWarning?,
        validationResult: AddressValidationResult,
        isSaving: Bool,
        onFormAddressChanged: @escaping (StructuredAddress) -> Void,
        onApplyConflictSuggestion: @escaping () -> Void,
        onSaveSubmitted: @escaping () -> Void
    ) {
        self.formAddress = formAddress
        self.conflictWarning = conflictWarning
        self.validationResult = validationResult
        self.isSaving = isSaving
        self.onFormAddressChanged = onFormAddressChanged
        self.onApplyConflictSuggestion = onApplyConflictSuggestion
        self.onSaveSubmitted = onSaveSubmitted
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Address ↔ Geocode Conflict Alert Banner
                if let warning = conflictWarning, warning.hasConflict {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 6) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(Color(hex: "D97706"))
                                .font(.system(size: 14))

                            Text("Address details conflict with selected map location.")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }

                        let enteredCity = formAddress.city.isEmpty ? "-" : formAddress.city
                        let enteredPin = formAddress.postalCode.isEmpty ? "-" : formAddress.postalCode
                        let mapCity = warning.suggestedCity ?? "-"
                        let mapPin = warning.suggestedPostalCode ?? "-"

                        Text("Entered: \(enteredCity) (\(enteredPin))\nMap Pin: \(mapCity) (\(mapPin))")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "475569"))

                        Button(action: onApplyConflictSuggestion) {
                            Text("Use map location")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color(hex: "D97706"))
                                .cornerRadius(8)
                        }
                        .padding(.top, 4)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(hex: "FFFBEB"))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color(hex: "FDE68A"), lineWidth: 1)
                    )
                }

                // Tag Selector
                VStack(alignment: .leading, spacing: 6) {
                    Text("Save address as *")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))

                    AddressTagSelector(
                        selectedTag: formAddress.tag,
                        onTagSelected: { tag in
                            var updated = formAddress
                            updated.tag = tag
                            onFormAddressChanged(updated)
                        }
                    )
                }

                // Flat / House No. / Apartment *
                VStack(alignment: .leading, spacing: 4) {
                    Text("Flat / House No. / Apartment *")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))

                    TextField("e.g. 402, Tower B", text: Binding(
                        get: { formAddress.houseNumber },
                        set: {
                            var updated = formAddress
                            updated.houseNumber = $0
                            onFormAddressChanged(updated)
                        }
                    ))
                    .font(.system(size: 14))
                    .foregroundColor(Color(hex: "0F172A"))
                    .accentColor(Color(hex: "0E675E"))
                    .padding(12)
                    .background(Color.white)
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(validationResult.houseNumberError != nil ? Color(hex: "DC2626") : Color(hex: "E2E8F0"), lineWidth: 1)
                    )

                    if let err = validationResult.houseNumberError {
                        Text(err)
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "DC2626"))
                    }
                }

                // Building / Tower / Society Name
                VStack(alignment: .leading, spacing: 4) {
                    Text("Building / Tower / Society Name")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))

                    TextField("e.g. Prestige Falcon City", text: Binding(
                        get: { formAddress.building },
                        set: {
                            var updated = formAddress
                            updated.building = $0
                            onFormAddressChanged(updated)
                        }
                    ))
                    .font(.system(size: 14))
                    .foregroundColor(Color(hex: "0F172A"))
                    .accentColor(Color(hex: "0E675E"))
                    .padding(12)
                    .background(Color.white)
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                }

                // Floor No. + Street / Area / Sector *
                HStack(alignment: .top, spacing: 10) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Floor No.")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "64748B"))

                        TextField("e.g. 4th", text: Binding(
                            get: { formAddress.floor },
                            set: {
                                var updated = formAddress
                                updated.floor = $0
                                onFormAddressChanged(updated)
                            }
                        ))
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "0F172A"))
                        .accentColor(Color(hex: "0E675E"))
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                        )
                    }
                    .frame(width: 90)

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Street / Area / Sector *")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "64748B"))

                        TextField("e.g. Kanakapura Main Road", text: Binding(
                            get: { formAddress.street },
                            set: {
                                var updated = formAddress
                                updated.street = $0
                                onFormAddressChanged(updated)
                            }
                        ))
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "0F172A"))
                        .accentColor(Color(hex: "0E675E"))
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(validationResult.streetError != nil ? Color(hex: "DC2626") : Color(hex: "E2E8F0"), lineWidth: 1)
                        )

                        if let err = validationResult.streetError {
                            Text(err)
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: "DC2626"))
                        }
                    }
                }

                // City * + State + PIN code
                HStack(alignment: .top, spacing: 8) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("City *")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "64748B"))

                        TextField("City", text: Binding(
                            get: { formAddress.city },
                            set: {
                                var updated = formAddress
                                updated.city = $0
                                onFormAddressChanged(updated)
                            }
                        ))
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "0F172A"))
                        .accentColor(Color(hex: "0E675E"))
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(validationResult.cityError != nil ? Color(hex: "DC2626") : Color(hex: "E2E8F0"), lineWidth: 1)
                        )

                        if let err = validationResult.cityError {
                            Text(err)
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: "DC2626"))
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("State")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "64748B"))

                        TextField("State", text: Binding(
                            get: { formAddress.state },
                            set: {
                                var updated = formAddress
                                updated.state = $0
                                onFormAddressChanged(updated)
                            }
                        ))
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "0F172A"))
                        .accentColor(Color(hex: "0E675E"))
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                        )
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("PIN Code *")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "64748B"))

                        TextField("PIN", text: Binding(
                            get: { formAddress.postalCode },
                            set: { input in
                                let digits = String(input.filter { $0.isNumber }.prefix(6))
                                var updated = formAddress
                                updated.postalCode = digits
                                onFormAddressChanged(updated)
                            }
                        ))
                        .keyboardType(.numberPad)
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "0F172A"))
                        .accentColor(Color(hex: "0E675E"))
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(validationResult.postalCodeError != nil ? Color(hex: "DC2626") : Color(hex: "E2E8F0"), lineWidth: 1)
                        )

                        if let err = validationResult.postalCodeError {
                            Text(err)
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: "DC2626"))
                        }
                    }
                    .frame(width: 90)
                }

                // Landmark (Optional)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Nearby Landmark (Optional)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))

                    TextField("e.g. Opposite Metro Pillar 140", text: Binding(
                        get: { formAddress.landmark },
                        set: {
                            var updated = formAddress
                            updated.landmark = $0
                            onFormAddressChanged(updated)
                        }
                    ))
                    .font(.system(size: 14))
                    .foregroundColor(Color(hex: "0F172A"))
                    .accentColor(Color(hex: "0E675E"))
                    .padding(12)
                    .background(Color.white)
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                }

                // Delivery Entrance Selector
                VStack(alignment: .leading, spacing: 6) {
                    Text("Delivery Entrance")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))

                    DeliveryEntranceSelector(
                        selectedEntrance: formAddress.entrance,
                        customEntranceDetails: formAddress.customEntranceDetails,
                        customEntranceDetailsError: validationResult.customEntranceDetailsError,
                        onEntranceSelected: { entrance in
                            var updated = formAddress
                            updated.entrance = entrance
                            onFormAddressChanged(updated)
                        },
                        onCustomDetailsChanged: { details in
                            var updated = formAddress
                            updated.customEntranceDetails = details
                            onFormAddressChanged(updated)
                        }
                    )
                }

                // Delivery Instructions Field
                VStack(alignment: .leading, spacing: 6) {
                    Text("Delivery Instructions")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))

                    DeliveryInstructionsField(
                        instructions: formAddress.deliveryInstructions,
                        onInstructionsChanged: { instructions in
                            var updated = formAddress
                            updated.deliveryInstructions = instructions
                            onFormAddressChanged(updated)
                        }
                    )
                }

                // Recipient Selector
                RecipientSelector(
                    recipientType: formAddress.recipientType,
                    contactName: formAddress.contactName,
                    contactPhone: formAddress.contactPhone,
                    nameError: validationResult.contactNameError,
                    phoneError: validationResult.contactPhoneError,
                    onRecipientTypeChanged: { type in
                        var updated = formAddress
                        updated.recipientType = type
                        onFormAddressChanged(updated)
                    },
                    onContactNameChanged: { name in
                        var updated = formAddress
                        updated.contactName = name
                        onFormAddressChanged(updated)
                    },
                    onContactPhoneChanged: { phone in
                        var updated = formAddress
                        updated.contactPhone = phone
                        onFormAddressChanged(updated)
                    }
                )

                // Save Address Action CTA
                Button(action: onSaveSubmitted) {
                    HStack(spacing: 8) {
                        if isSaving {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.8)
                        }
                        Text(isSaving ? "Saving address..." : "Save Address")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(validationResult.isValid ? Color(hex: "0E675E") : Color(hex: "94A3B8"))
                    .cornerRadius(12)
                }
                .disabled(!validationResult.isValid || isSaving)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
        }
    }
}
