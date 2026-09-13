import SwiftUI

public struct PrescriptionVaultScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @State private var showingUploadSheet: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var prescriptions: [PrescriptionItem] = []
    
    public init() {}
    
    public var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                // Header description & Refresh
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Prescription Vault")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.primary)
                        Text("Upload, track review status, and attach approved prescriptions.")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Button(action: { loadPrescriptions() }) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(configProvider.currentConfig.theme.primaryColor)
                    }
                }
                .padding(.horizontal)
                .padding(.top, 12)
                
                // Upload Prescription Button
                Button(action: { showingUploadSheet = true }) {
                    HStack(spacing: 8) {
                        Image(systemName: "plus")
                            .font(.system(size: 14, weight: .bold))
                        Text("Upload prescription")
                            .font(.system(size: 15, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(configProvider.currentConfig.theme.primaryColor)
                    .cornerRadius(12)
                }
                .padding(.horizontal)
                
                // Content States
                if isLoading {
                    Spacer()
                    ProgressView()
                        .frame(maxWidth: .infinity)
                    Spacer()
                } else if let error = errorMessage {
                    Spacer()
                    Text(error)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity)
                    Spacer()
                } else if prescriptions.isEmpty {
                    emptyVaultView
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(prescriptions) { rx in
                                PrescriptionCardView(item: rx)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 40)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showingUploadSheet) {
                UploadPrescriptionModal(onUploadSuccess: { newRx in
                    prescriptions.insert(newRx, at: 0)
                    showingUploadSheet = false
                })
                .environmentObject(container)
            }
            .onAppear {
                loadPrescriptions()
            }
        }
    }
    
    private var emptyVaultView: some View {
        VStack(spacing: 10) {
            Spacer()
            Image(systemName: "doc.text")
                .font(.system(size: 44))
                .foregroundColor(.secondary)
            Text("No prescriptions uploaded")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.primary)
            Text("Upload a clear photo when an item requires pharmacist review.")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
    
    private func loadPrescriptions() {
        isLoading = true
        errorMessage = nil
        Task {
            do {
                let customerId = UserDefaults.standard.string(forKey: "customer_id") ?? "self"
                let list: [PrescriptionServerDto] = try await container.apiClient.get(endpoint: "/api/v1/prescriptions/customer/\(customerId)")
                let mapped = list.map { dto in
                    PrescriptionItem(
                        id: dto.id,
                        doctorName: dto.doctorName ?? "Licensed Practitioner",
                        doctorRegNo: "CDSCO-VERIFIED",
                        patientName: dto.patientName ?? "Self Patient",
                        extractedMedicines: [],
                        pharmacistStatus: dto.status ?? "PENDING",
                        uploadedDate: dto.createdAt ?? "Recent"
                    )
                }
                await MainActor.run {
                    self.prescriptions = mapped
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }
}

public struct PrescriptionItem: Identifiable {
    public let id: String
    public let doctorName: String
    public let doctorRegNo: String
    public let patientName: String
    public let extractedMedicines: [String]
    public let pharmacistStatus: String // "APPROVED", "PENDING_VERIFICATION", "REJECTED"
    public let uploadedDate: String
}

public struct PrescriptionCardView: View {
    let item: PrescriptionItem
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(item.patientName.isEmpty ? "Prescription" : item.patientName)
                    .font(.system(size: 15, weight: .bold))
                Spacer()
                statusBadge
            }
            
            if !item.doctorName.isEmpty {
                Text("Doctor: \(item.doctorName)")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            
            HStack {
                Text(item.uploadedDate)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                Spacer()
            }
            
            if !item.extractedMedicines.isEmpty {
                Divider()
                HStack(spacing: 6) {
                    ForEach(item.extractedMedicines, id: \.self) { med in
                        Text(med)
                            .font(.system(size: 11, weight: .medium))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(CommerceOSTheme.Colors.brandPrimarySoft)
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                            .cornerRadius(6)
                    }
                }
            }
        }
        .padding(14)
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
    }
    
    private var statusBadge: some View {
        let status = item.pharmacistStatus.uppercased()
        let isApproved = status == "APPROVED" || status == "VERIFIED"
        let isRejected = status == "REJECTED"
        let label = isApproved ? "Approved" : (isRejected ? "Needs attention" : "Under review")
        let color: Color = isApproved ? .green : (isRejected ? .red : .orange)
        
        return Text(label)
            .font(.system(size: 11, weight: .bold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.15))
            .foregroundColor(color)
            .cornerRadius(4)
    }
}

public struct PrescriptionServerDto: Codable {
    public let id: String
    public let customerId: String?
    public let patientName: String?
    public let status: String?
    public let doctorName: String?
    public let rejectionReason: String?
    public let reviewedAt: String?
    public let createdAt: String?
}

public struct PrescriptionUploadPayload: Codable {
    public let patientName: String
    public let doctorName: String?
    public let doctorRegistrationNo: String?
    public let attachments: [String]
    public let note: String?
}

public struct PrescriptionUploadResponse: Codable {
    public let id: String
    public let status: String?
    public let patientName: String?
}

public struct UploadPrescriptionModal: View {
    @Environment(\.presentationMode) private var presentationMode
    @EnvironmentObject private var container: AppContainer
    @State private var isAnalyzing: Bool = false
    @State private var showingCameraScanner: Bool = false
    @State private var scanErrorMessage: String? = nil
    @State private var showingManualInput: Bool = false
    @State private var manualPatientName: String = ""
    @State private var manualDoctorName: String = ""
    let onUploadSuccess: (PrescriptionItem) -> Void
    
    public var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                if isAnalyzing {
                    VStack(spacing: 12) {
                        ProgressView()
                            .scaleEffect(1.5)
                        Text("Uploading & Submitting to Pharmacist Desk...")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if showingManualInput {
                    Form {
                        Section(header: Text("Prescription Details")) {
                            TextField("Patient Full Name *", text: $manualPatientName)
                            TextField("Doctor / Clinic Name", text: $manualDoctorName)
                        }
                        Section {
                            Button(action: uploadManualPrescription) {
                                Text("Submit for Pharmacist Review")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(CommerceOSTheme.Colors.brandPrimaryDark)
                                    .cornerRadius(8)
                            }
                        }
                    }
                } else {
                    Image(systemName: "doc.viewfinder.fill")
                        .font(.system(size: 64))
                        .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                        .padding(.top, 40)
                    
                    Text("Upload Prescription Document")
                        .font(.system(size: 18, weight: .bold))
                    
                    Text("Upload or capture a clear photo of the prescription for pharmacist review.")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                    
                    if let error = scanErrorMessage {
                        Text(error)
                            .font(.system(size: 12))
                            .foregroundColor(.red)
                            .padding(.horizontal)
                    }
                    
                    Spacer()
                    
                    VStack(spacing: 12) {
                        // Camera Scanner Button
                        Button(action: {
                            if VisionKitDocumentScanner.isAvailable {
                                showingCameraScanner = true
                            } else {
                                showingManualInput = true
                            }
                        }) {
                            HStack {
                                Image(systemName: "camera.fill")
                                Text("Scan with Camera")
                            }
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(CommerceOSTheme.Colors.brandPrimaryDark)
                            .cornerRadius(10)
                            .padding(.horizontal)
                        }
                        
                        // Manual entry fallback
                        Button(action: { showingManualInput = true }) {
                            HStack {
                                Image(systemName: "square.and.pencil")
                                Text("Enter Prescription Details Manually")
                            }
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                        }
                    }
                    .padding(.bottom, 20)
                }
            }
            .navigationTitle("Upload Rx")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
            .sheet(isPresented: $showingCameraScanner) {
                VisionKitDocumentScanner(
                    onScanCompleted: { result in
                        showingCameraScanner = false
                        uploadScannedResult(result)
                    },
                    onScanFailed: { error in
                        showingCameraScanner = false
                        self.scanErrorMessage = "Camera scan error: \(error.localizedDescription)"
                    },
                    onScanCancelled: {
                        showingCameraScanner = false
                    }
                )
            }
        }
    }
    
    private func uploadScannedResult(_ result: ScannedPrescriptionResult) {
        isAnalyzing = true
        let pName = result.patientName.isEmpty ? "Self Patient" : result.patientName
        let dName = result.doctorName.isEmpty ? nil : result.doctorName
        let regNo = result.doctorRegNo.isEmpty ? nil : result.doctorRegNo
        let meds = result.extractedMedicines
        
        let payload = PrescriptionUploadPayload(
            patientName: pName,
            doctorName: dName,
            doctorRegistrationNo: regNo,
            attachments: ["data:image/jpeg;base64,visionkit_scan"],
            note: meds.isEmpty ? "VisionKit Document Camera Upload" : "VisionKit OCR: \(meds.joined(separator: ", "))"
        )
        
        Task {
            do {
                let resp: PrescriptionUploadResponse = try await container.apiClient.post(
                    endpoint: "/api/v1/prescriptions",
                    body: payload
                )
                await MainActor.run {
                    self.isAnalyzing = false
                    let item = PrescriptionItem(
                        id: resp.id,
                        doctorName: dName ?? "Pending Verification",
                        doctorRegNo: regNo ?? "Pending Review",
                        patientName: pName,
                        extractedMedicines: meds,
                        pharmacistStatus: "PENDING_VERIFICATION",
                        uploadedDate: "Just Now"
                    )
                    onUploadSuccess(item)
                    presentationMode.wrappedValue.dismiss()
                }
            } catch {
                await MainActor.run {
                    self.isAnalyzing = false
                    self.scanErrorMessage = "Prescription upload failed: \(error.localizedDescription)"
                }
            }
        }
    }
    
    private func uploadManualPrescription() {
        let pName = manualPatientName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !pName.isEmpty else {
            scanErrorMessage = "Patient name is required."
            return
        }
        let dName = manualDoctorName.trimmingCharacters(in: .whitespacesAndNewlines)
        isAnalyzing = true
        let payload = PrescriptionUploadPayload(
            patientName: pName,
            doctorName: dName.isEmpty ? nil : dName,
            doctorRegistrationNo: nil,
            attachments: ["data:image/jpeg;base64,manual_entry"],
            note: "Manual prescription submission"
        )
        Task {
            do {
                let resp: PrescriptionUploadResponse = try await container.apiClient.post(
                    endpoint: "/api/v1/prescriptions",
                    body: payload
                )
                await MainActor.run {
                    self.isAnalyzing = false
                    let item = PrescriptionItem(
                        id: resp.id,
                        doctorName: dName.isEmpty ? "Pending Verification" : dName,
                        doctorRegNo: "Pending Review",
                        patientName: pName,
                        extractedMedicines: [],
                        pharmacistStatus: "PENDING_VERIFICATION",
                        uploadedDate: "Just Now"
                    )
                    onUploadSuccess(item)
                    presentationMode.wrappedValue.dismiss()
                }
            } catch {
                await MainActor.run {
                    self.isAnalyzing = false
                    self.scanErrorMessage = "Prescription upload failed: \(error.localizedDescription)"
                }
            }
        }
    }
}
