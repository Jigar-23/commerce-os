import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import UIKit

public struct PrescriptionVaultScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @State private var showingUploadSheet: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var prescriptions: [PrescriptionItem] = []
    @State private var previewItem: PrescriptionItem? = nil
    
    public var onBack: (() -> Void)? = nil
    
    public init(onBack: (() -> Void)? = nil) {
        self.onBack = onBack
    }
    
    public var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                // Header description & Refresh
                HStack(alignment: .top, spacing: 10) {
                    if let onBack = onBack {
                        Button(action: onBack) {
                            Image(systemName: "arrow.left")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                                .padding(8)
                                .background(Color(hex: "F1F5F9"))
                                .clipShape(Circle())
                        }
                        .buttonStyle(PlainButtonStyle())
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Prescriptions")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.primary)
                        Text("Saved prescriptions ready to share with pharmacy")
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
                        Text("Upload Prescription")
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
                                PrescriptionCardView(
                                    item: rx,
                                    onPreview: { previewItem = rx },
                                    onDelete: { deletePrescription(item: rx) }
                                )
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
            .sheet(item: $previewItem) { item in
                PrescriptionQuickPreviewModal(
                    prescription: item,
                    onAttach: nil
                )
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
            Text("Upload a clear photo of your prescription to share with the vendor pharmacy during order checkout.")
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
        let customerId = UserDefaults.standard.string(forKey: "customer_id") ?? "self"
        
        // 1. Instantly surface all local device prescriptions
        let localRecords = LocalPrescriptionManager.shared.getPrescriptions(customerId: customerId)
        var merged: [PrescriptionItem] = localRecords.map { $0.toPrescriptionItem() }
        self.prescriptions = merged
        
        Task {
            do {
                let list: [PrescriptionServerDto] = try await container.apiClient.get(endpoint: "/api/v1/prescriptions/customer/\(customerId)")
                let localIds = Set(localRecords.map { $0.id })
                let mappedRemote = list.filter { !localIds.contains($0.id) }.map { dto in
                    PrescriptionItem(
                        id: dto.id,
                        doctorName: dto.doctorName ?? "",
                        doctorRegNo: "",
                        patientName: dto.patientName ?? "Patient",
                        extractedMedicines: [],
                        pharmacistStatus: dto.status ?? "PENDING",
                        uploadedDate: dto.createdAt ?? "Recently uploaded"
                    )
                }
                await MainActor.run {
                    self.prescriptions = merged + mappedRemote
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }
    
    private func deletePrescription(item: PrescriptionItem) {
        let customerId = UserDefaults.standard.string(forKey: "customer_id") ?? "self"
        LocalPrescriptionManager.shared.deletePrescription(customerId: customerId, rxId: item.id)
        prescriptions.removeAll { $0.id == item.id }
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
    var onPreview: (() -> Void)? = nil
    var onDelete: (() -> Void)? = nil
    
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
                
                if let onPreview = onPreview {
                    Button(action: onPreview) {
                        HStack(spacing: 4) {
                            Image(systemName: "eye.fill")
                                .font(.system(size: 11))
                            Text("Preview")
                                .font(.system(size: 11, weight: .bold))
                        }
                        .foregroundColor(Color(hex: "0284C7"))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color(hex: "F0F9FF"))
                        .cornerRadius(6)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                
                if let onDelete = onDelete {
                    Button(action: onDelete) {
                        Image(systemName: "trash")
                            .font(.system(size: 12))
                            .foregroundColor(.red)
                            .padding(6)
                            .background(Color.red.opacity(0.1))
                            .clipShape(Circle())
                    }
                    .buttonStyle(PlainButtonStyle())
                }
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
    @State private var showingFilePicker: Bool = false
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
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
                        Text("Saving & Submitting to Pharmacist Desk...")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if showingManualInput {
                    Form {
                        Section(header: Text("Prescription Details")) {
                            TextField("Patient Full Name *", text: $manualPatientName)
                                .foregroundColor(Color(hex: "0F172A"))
                            TextField("Doctor / Clinic Name (Optional)", text: $manualDoctorName)
                                .foregroundColor(Color(hex: "0F172A"))
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
                        .padding(.top, 30)
                    
                    Text("Upload Prescription")
                        .font(.system(size: 20, weight: .bold))
                    
                    Text("Upload a clear photo or document of your prescription for pharmacist verification.")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                    
                    if let error = scanErrorMessage {
                        Text(error)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.red)
                            .padding(.horizontal)
                    }
                    
                    Spacer()
                    
                    // 4 Enterprise Upload Options: Camera, Photo Gallery, Files/Documents, Manual
                    VStack(spacing: 12) {
                        // 1. Scan with Camera
                        Button(action: {
                            if VisionKitDocumentScanner.isAvailable {
                                showingCameraScanner = true
                            } else {
                                showingManualInput = true
                            }
                        }) {
                            HStack {
                                Image(systemName: "camera.fill")
                                    .font(.system(size: 16))
                                Text("Scan with Camera")
                                    .font(.system(size: 15, weight: .bold))
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(CommerceOSTheme.Colors.brandPrimaryDark)
                            .cornerRadius(12)
                            .padding(.horizontal)
                        }
                        
                        // 2. Photo Gallery (PhotosPicker)
                        PhotosPicker(
                            selection: $selectedPhotoItem,
                            matching: .images,
                            photoLibrary: .shared()
                        ) {
                            HStack {
                                Image(systemName: "photo.on.rectangle.angled")
                                    .font(.system(size: 16))
                                Text("Choose from Photo Gallery")
                                    .font(.system(size: 15, weight: .bold))
                            }
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(CommerceOSTheme.Colors.brandPrimarySoft)
                            .cornerRadius(12)
                            .padding(.horizontal)
                        }
                        .onChange(of: selectedPhotoItem) { newItem in
                            guard let newItem = newItem else { return }
                            Task {
                                if let data = try? await newItem.loadTransferable(type: Data.self),
                                   let img = UIImage(data: data) {
                                    uploadImageRecord(image: img, sourceLabel: "Gallery Photo")
                                }
                            }
                        }
                        
                        // 3. Choose from Files / Documents (PDF / Images)
                        Button(action: { showingFilePicker = true }) {
                            HStack {
                                Image(systemName: "folder.fill")
                                    .font(.system(size: 16))
                                Text("Upload from Files (PDF, Images)")
                                    .font(.system(size: 15, weight: .semibold))
                            }
                            .foregroundColor(Color(hex: "0284C7"))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color(hex: "F0F9FF"))
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: "BAE6FD"), lineWidth: 1))
                            .padding(.horizontal)
                        }
                        
                        // 4. Manual entry fallback
                        Button(action: { showingManualInput = true }) {
                            HStack {
                                Image(systemName: "square.and.pencil")
                                Text("Enter Prescription Details Manually")
                            }
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.secondary)
                            .padding(.vertical, 8)
                        }
                    }
                    .padding(.bottom, 24)
                }
            }
            .navigationTitle("Upload Rx")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
            .fileImporter(
                isPresented: $showingFilePicker,
                allowedContentTypes: [.pdf, .image, .jpeg, .png],
                allowsMultipleSelection: false
            ) { result in
                switch result {
                case .success(let urls):
                    if let firstUrl = urls.first {
                        handleFilePicked(url: firstUrl)
                    }
                case .failure(let error):
                    self.scanErrorMessage = "File selection failed: \(error.localizedDescription)"
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
    
    private func handleFilePicked(url: URL) {
        guard url.startAccessingSecurityScopedResource() else { return }
        defer { url.stopAccessingSecurityScopedResource() }
        
        if let data = try? Data(contentsOf: url) {
            if let image = UIImage(data: data) {
                uploadImageRecord(image: image, sourceLabel: url.lastPathComponent)
            } else {
                // PDF document handling
                uploadRawDocument(data: data, fileName: url.lastPathComponent)
            }
        }
    }
    
    private func uploadImageRecord(image: UIImage, sourceLabel: String) {
        isAnalyzing = true
        let customerId = UserDefaults.standard.string(forKey: "customer_id") ?? "self"
        let record = LocalPrescriptionManager.shared.savePrescriptionImage(
            image: image,
            customerId: customerId,
            patientName: "Patient",
            doctorName: ""
        )
        
        let payload = PrescriptionUploadPayload(
            patientName: "Patient",
            doctorName: nil,
            doctorRegistrationNo: nil,
            attachments: record?.attachments ?? [],
            note: "Uploaded via \(sourceLabel)"
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
                        doctorName: "",
                        doctorRegNo: "",
                        patientName: "Patient",
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
                    // If backend is offline or mock, local prescription item is already safely persisted!
                    let item = record?.toPrescriptionItem() ?? PrescriptionItem(
                        id: UUID().uuidString,
                        doctorName: "",
                        doctorRegNo: "",
                        patientName: "Patient",
                        extractedMedicines: [],
                        pharmacistStatus: "PENDING_VERIFICATION",
                        uploadedDate: "Just Now"
                    )
                    onUploadSuccess(item)
                    presentationMode.wrappedValue.dismiss()
                }
            }
        }
    }
    
    private func uploadRawDocument(data: Data, fileName: String) {
        isAnalyzing = true
        let customerId = UserDefaults.standard.string(forKey: "customer_id") ?? "self"
        let safeName = "doc_\(Int(Date().timeIntervalSince1970))_\(fileName)"
        let targetURL = LocalPrescriptionManager.shared.prescriptionsDirectory.appendingPathComponent(safeName)
        try? data.write(to: targetURL)
        
        let record = LocalPrescriptionRecord(
            customerId: customerId,
            doctorName: "",
            patientName: "Patient",
            pharmacistStatus: "PENDING_VERIFICATION",
            attachments: [targetURL.path]
        )
        LocalPrescriptionManager.shared.savePrescription(record)
        
        let payload = PrescriptionUploadPayload(
            patientName: "Patient",
            doctorName: nil,
            doctorRegistrationNo: nil,
            attachments: [targetURL.path],
            note: "Document: \(fileName)"
        )
        
        Task {
            let _: PrescriptionUploadResponse? = try? await container.apiClient.post(
                endpoint: "/api/v1/prescriptions",
                body: payload
            )
            await MainActor.run {
                self.isAnalyzing = false
                onUploadSuccess(record.toPrescriptionItem())
                presentationMode.wrappedValue.dismiss()
            }
        }
    }
    
    private func uploadScannedResult(_ result: ScannedPrescriptionResult) {
        isAnalyzing = true
        let pName = result.patientName.isEmpty ? "Patient" : result.patientName
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
                        doctorName: dName ?? "",
                        doctorRegNo: regNo ?? "",
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
                        doctorName: dName,
                        doctorRegNo: "",
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
