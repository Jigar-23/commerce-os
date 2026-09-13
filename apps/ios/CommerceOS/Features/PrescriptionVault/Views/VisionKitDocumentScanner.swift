import SwiftUI
import VisionKit
import Vision

/// Structured result extracted via Apple Vision ML text recognition
public struct ScannedPrescriptionResult {
    public let doctorName: String
    public let doctorRegNo: String
    public let patientName: String
    public let extractedMedicines: [String]
    public let rawRecognizedText: String
    public let scannedImageCount: Int
    
    public init(
        doctorName: String,
        doctorRegNo: String,
        patientName: String,
        extractedMedicines: [String],
        rawRecognizedText: String,
        scannedImageCount: Int
    ) {
        self.doctorName = doctorName
        self.doctorRegNo = doctorRegNo
        self.patientName = patientName
        self.extractedMedicines = extractedMedicines
        self.rawRecognizedText = rawRecognizedText
        self.scannedImageCount = scannedImageCount
    }
}

/// Native SwiftUI wrapper for Apple VisionKit VNDocumentCameraViewController
public struct VisionKitDocumentScanner: UIViewControllerRepresentable {
    @Environment(\.presentationMode) private var presentationMode
    
    public var onScanCompleted: (ScannedPrescriptionResult) -> Void
    public var onScanFailed: (Error) -> Void
    public var onScanCancelled: () -> Void
    
    public init(
        onScanCompleted: @escaping (ScannedPrescriptionResult) -> Void,
        onScanFailed: @escaping (Error) -> Void,
        onScanCancelled: @escaping () -> Void
    ) {
        self.onScanCompleted = onScanCompleted
        self.onScanFailed = onScanFailed
        self.onScanCancelled = onScanCancelled
    }
    
    public static var isAvailable: Bool {
        VNDocumentCameraViewController.isSupported
    }
    
    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    public func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let scanner = VNDocumentCameraViewController()
        scanner.delegate = context.coordinator
        return scanner
    }
    
    public func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}
    
    public final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        private let parent: VisionKitDocumentScanner
        
        public init(_ parent: VisionKitDocumentScanner) {
            self.parent = parent
        }
        
        public func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            controller.dismiss(animated: true) { [weak self] in
                guard let self = self else { return }
                self.processDocumentScan(scan)
            }
        }
        
        public func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            controller.dismiss(animated: true) { [weak self] in
                self?.parent.onScanCancelled()
            }
        }
        
        public func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
            controller.dismiss(animated: true) { [weak self] in
                self?.parent.onScanFailed(error)
            }
        }
        
        /// Processes camera document scans via VNRecognizeTextRequest
        private func processDocumentScan(_ scan: VNDocumentCameraScan) {
            var combinedText = ""
            let pageCount = scan.pageCount
            
            for pageIndex in 0..<pageCount {
                let image = scan.imageOfPage(at: pageIndex)
                guard let cgImage = image.cgImage else { continue }
                
                let requestHandler = VNImageRequestHandler(cgImage: cgImage, options: [:])
                let request = VNRecognizeTextRequest()
                request.recognitionLevel = .accurate
                request.usesLanguageCorrection = true
                
                do {
                    try requestHandler.perform([request])
                    if let observations = request.results {
                        let pageText = observations
                            .compactMap { $0.topCandidates(1).first?.string }
                            .joined(separator: "\n")
                        combinedText += pageText + "\n"
                    }
                } catch {
                    print("[VisionKit Error] OCR failed for page \(pageIndex): \(error.localizedDescription)")
                }
            }
            
            let parsed = self.extractPrescriptionMetadata(from: combinedText, imageCount: pageCount)
            DispatchQueue.main.async {
                self.parent.onScanCompleted(parsed)
            }
        }
        
        /// Extracts doctor credentials, license registration, and medicine lines from OCR text
        private func extractPrescriptionMetadata(from rawText: String, imageCount: Int) -> ScannedPrescriptionResult {
            let lines = rawText.components(separatedBy: .newlines)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            
            var doctorName = ""
            var doctorRegNo = ""
            var patientName = ""
            var medicines: [String] = []
            
            for line in lines {
                let lower = line.lowercased()
                
                // Doctor Name Identification
                if (lower.contains("dr.") || lower.contains("doctor") || lower.contains("md") || lower.contains("mbbs")) && !lower.contains("prescribe") {
                    doctorName = line
                }
                
                // Doctor Reg / License Identification
                if lower.contains("reg") || lower.contains("mci") || lower.contains("lic") {
                    doctorRegNo = line
                }
                
                // Patient Name Identification
                if lower.contains("patient:") || lower.contains("name:") || lower.contains("mr.") || lower.contains("mrs.") || lower.contains("ms.") {
                    patientName = line.replacingOccurrences(of: "Patient:", with: "", options: .caseInsensitive)
                        .replacingOccurrences(of: "Name:", with: "", options: .caseInsensitive)
                        .trimmingCharacters(in: .whitespaces)
                }
                
                // Medicine & Dosage Line Detection (e.g. mg, ml, tab, cap, syrup)
                if lower.contains("mg") || lower.contains("ml") || lower.contains("tablet") || lower.contains("tab") || lower.contains("capsule") || lower.contains("cap") || lower.contains("syrup") || lower.contains("od") || lower.contains("bd") || lower.contains("tid") {
                    if !medicines.contains(line) && medicines.count < 6 {
                        medicines.append(line)
                    }
                }
            }
            
            return ScannedPrescriptionResult(
                doctorName: doctorName,
                doctorRegNo: doctorRegNo,
                patientName: patientName,
                extractedMedicines: medicines,
                rawRecognizedText: rawText,
                scannedImageCount: imageCount
            )
        }
    }
}
