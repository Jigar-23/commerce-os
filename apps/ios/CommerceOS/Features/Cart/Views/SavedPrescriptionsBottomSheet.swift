import SwiftUI

/**
 * 2026 Healthcare Saved Prescriptions Bottom Sheet for iOS Cart.
 * 1:1 Parity with Android OptionalPrescriptionCard BottomSheet & Tata 1mg standard.
 */
public struct SavedPrescriptionsBottomSheet: View {
    @Environment(\.presentationMode) private var presentationMode
    public let prescriptions: [PrescriptionItem]
    public let selectedRxId: String?
    public let onSelect: (PrescriptionItem) -> Void
    public let onUploadNew: () -> Void
    public let onPreview: (PrescriptionItem) -> Void

    public init(
        prescriptions: [PrescriptionItem],
        selectedRxId: String?,
        onSelect: @escaping (PrescriptionItem) -> Void,
        onUploadNew: @escaping () -> Void,
        onPreview: @escaping (PrescriptionItem) -> Void
    ) {
        self.prescriptions = prescriptions
        self.selectedRxId = selectedRxId
        self.onSelect = onSelect
        self.onUploadNew = onUploadNew
        self.onPreview = onPreview
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Drag Handle
            Capsule()
                .fill(Color(hex: "CBD5E1"))
                .frame(width: 44, height: 4.5)
                .padding(.top, 12)
                .padding(.bottom, 8)

            // Header Row
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Select from Saved Prescriptions")
                        .font(CommerceOSTheme.Typography.titleMedium())
                        .foregroundColor(Color(hex: "0F172A"))
                    Text("Choose a prescription already uploaded in your vault")
                        .font(CommerceOSTheme.Typography.caption())
                        .foregroundColor(Color(hex: "64748B"))
                }
                Spacer()
                Button(action: { presentationMode.wrappedValue.dismiss() }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))
                        .padding(8)
                        .background(Color(hex: "F1F5F9"))
                        .clipShape(Circle())
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 14)

            Divider()
                .background(Color(hex: "E2E8F0"))

            if prescriptions.isEmpty {
                VStack(spacing: 12) {
                    Spacer()
                    ZStack {
                        Circle()
                            .fill(CommerceOSTheme.Colors.brandPrimarySoft)
                            .frame(width: 64, height: 64)
                        Image(systemName: "doc.text.fill")
                            .font(.system(size: 30))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                    }

                    Text("No Saved Prescriptions")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    Text("Upload a prescription to save it in your vault for instant one-tap reuse.")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(Color(hex: "64748B"))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)

                    Button(action: {
                        presentationMode.wrappedValue.dismiss()
                        onUploadNew()
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "plus")
                                .font(.system(size: 14, weight: .bold))
                            Text("Upload Prescription Now")
                                .font(.system(size: 14, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 22)
                        .padding(.vertical, 12)
                        .background(CommerceOSTheme.Colors.brandPrimary)
                        .cornerRadius(12)
                    }
                    .padding(.top, 6)
                    Spacer()
                }
                .padding(.vertical, 40)
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(prescriptions) { rx in
                            let isSelected = rx.id == selectedRxId

                            Button(action: {
                                HapticFeedbackEngine.shared.triggerSelection()
                                onSelect(rx)
                                presentationMode.wrappedValue.dismiss()
                            }) {
                                HStack(spacing: 12) {
                                    // Thumbnail Container
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 10)
                                            .fill(CommerceOSTheme.Colors.brandPrimarySoft)
                                            .frame(width: 52, height: 52)

                                        Image(systemName: "doc.text.image")
                                            .font(.system(size: 22))
                                            .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                                    }

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(rx.patientName.isEmpty ? "Prescription #\(rx.id.suffix(6).uppercased())" : rx.patientName)
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(Color(hex: "0F172A"))
                                            .lineLimit(1)

                                        if !rx.doctorName.isEmpty {
                                            Text("Dr. \(rx.doctorName)")
                                                .font(.system(size: 12, weight: .medium))
                                                .foregroundColor(Color(hex: "475569"))
                                                .lineLimit(1)
                                        }

                                        Text("Rx #\(rx.id.suffix(6).uppercased()) • Attached for Pharmacy")
                                            .font(.system(size: 11, weight: .semibold))
                                            .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                                    }

                                    Spacer()

                                    // Eye Quick Preview Button
                                    Button(action: {
                                        onPreview(rx)
                                    }) {
                                        Image(systemName: "eye.fill")
                                            .font(.system(size: 14, weight: .semibold))
                                            .foregroundColor(Color(hex: "0284C7"))
                                            .padding(8)
                                            .background(Color(hex: "E0F2FE"))
                                            .clipShape(Circle())
                                    }
                                    .buttonStyle(PlainButtonStyle())

                                    // Selection Radio Circle
                                    ZStack {
                                        Circle()
                                            .stroke(isSelected ? CommerceOSTheme.Colors.brandPrimary : Color(hex: "CBD5E1"), lineWidth: isSelected ? 2 : 1.5)
                                            .frame(width: 24, height: 24)

                                        if isSelected {
                                            Circle()
                                                .fill(CommerceOSTheme.Colors.brandPrimary)
                                                .frame(width: 14, height: 14)
                                        }
                                    }
                                }
                                .padding(12)
                                .background(isSelected ? CommerceOSTheme.Colors.brandPrimarySoft : Color.white)
                                .cornerRadius(14)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(isSelected ? CommerceOSTheme.Colors.brandPrimary : Color(hex: "E2E8F0"), lineWidth: isSelected ? 1.5 : 1)
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 14)
                }

                // Bottom Upload New Button
                Button(action: {
                    presentationMode.wrappedValue.dismiss()
                    onUploadNew()
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "plus")
                            .font(.system(size: 14, weight: .bold))
                        Text("Upload New Prescription")
                            .font(.system(size: 14, weight: .bold))
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                    .background(Color.white)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(CommerceOSTheme.Colors.brandPrimary, lineWidth: 1.5)
                    )
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .background(Color.white)
    }
}

// MARK: - Prescription Quick Preview Modal
public struct PrescriptionQuickPreviewModal: View {
    @Environment(\.presentationMode) private var presentationMode
    public let prescription: PrescriptionItem
    public let onAttach: (() -> Void)?

    public init(prescription: PrescriptionItem, onAttach: (() -> Void)? = nil) {
        self.prescription = prescription
        self.onAttach = onAttach
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                HStack(spacing: 10) {
                    ZStack {
                        Circle()
                            .fill(CommerceOSTheme.Colors.brandPrimarySoft)
                            .frame(width: 36, height: 36)
                        Text("Rx")
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Prescription Preview")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("ID: #\(prescription.id.suffix(6).uppercased())")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                }
                Spacer()
                Button(action: { presentationMode.wrappedValue.dismiss() }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))
                        .padding(8)
                        .background(Color(hex: "F1F5F9"))
                        .clipShape(Circle())
                }
            }
            .padding(16)

            Divider()

            ScrollView {
                VStack(spacing: 16) {
                    // Preview Card
                    ZStack {
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color(hex: "0F172A"))
                            .frame(height: 240)

                        VStack(spacing: 8) {
                            Image(systemName: "doc.text.fill")
                                .font(.system(size: 48))
                                .foregroundColor(Color(hex: "34D399"))
                            Text("Verified Clinical Prescription")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                            Text("Ready for Licensed Pharmacist Review")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(Color(hex: "94A3B8"))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)

                    // Meta Details Strip
                    VStack(spacing: 8) {
                        HStack {
                            Text("Patient Name")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color(hex: "64748B"))
                            Spacer()
                            Text(prescription.patientName.isEmpty ? "Patient" : prescription.patientName)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }

                        if !prescription.doctorName.isEmpty {
                            HStack {
                                Text("Prescribing Doctor")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(Color(hex: "64748B"))
                                Spacer()
                                Text("Dr. \(prescription.doctorName)")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(Color(hex: "0F172A"))
                            }
                        }

                        HStack {
                            Text("Verification Status")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color(hex: "64748B"))
                            Spacer()
                            Text("READY TO SHARE WITH PHARMACY")
                                .font(.system(size: 10, weight: .heavy))
                                .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(CommerceOSTheme.Colors.brandPrimarySoft)
                                .cornerRadius(6)
                        }
                    }
                    .padding(14)
                    .background(Color(hex: "F8FAFC"))
                    .cornerRadius(12)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: "E2E8F0"), lineWidth: 1))
                    .padding(.horizontal, 16)
                }
            }

            // Bottom Actions
            HStack(spacing: 12) {
                Button(onAttach != nil ? "Close" : "Done") {
                    presentationMode.wrappedValue.dismiss()
                }
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(onAttach != nil ? Color(hex: "64748B") : .white)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(onAttach != nil ? Color(hex: "F1F5F9") : CommerceOSTheme.Colors.brandPrimary)
                .cornerRadius(12)

                if let onAttach = onAttach {
                    Button("Attach to Order") {
                        HapticFeedbackEngine.shared.triggerNotification(.success)
                        onAttach()
                        presentationMode.wrappedValue.dismiss()
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(CommerceOSTheme.Colors.brandPrimary)
                    .cornerRadius(12)
                }
            }
            .padding(16)
        }
        .background(Color.white)
    }
}
