import UIKit
import Foundation

/**
 * 1:1 Parity with Android LocalPrescriptionManager.kt (245 lines).
 * Manages customer prescriptions entirely within the app's personal local sandbox.
 * Physical prescription slips and camera captures are saved in `Application Support/prescriptions/`,
 * completely eliminating backend memory overhead, cloud file hosting bills, and large Base64 payloads.
 */
public struct LocalPrescriptionRecord: Codable, Identifiable, Equatable {
    public let id: String
    public let customerId: String
    public let doctorName: String
    public let patientName: String
    public let doctorRegNo: String?
    public let pharmacistStatus: String
    public let attachments: [String]
    public let extractedMedicines: [String]
    public let createdAt: Int64

    public init(
        id: String = UUID().uuidString,
        customerId: String,
        doctorName: String = "",
        patientName: String = "",
        doctorRegNo: String? = nil,
        pharmacistStatus: String = "APPROVED",
        attachments: [String] = [],
        extractedMedicines: [String] = [],
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.customerId = customerId
        self.doctorName = doctorName
        self.patientName = patientName
        self.doctorRegNo = doctorRegNo
        self.pharmacistStatus = pharmacistStatus
        self.attachments = attachments
        self.extractedMedicines = extractedMedicines
        self.createdAt = createdAt
    }

    public func toPrescriptionItem() -> PrescriptionItem {
        let date = Date(timeIntervalSince1970: Double(createdAt) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return PrescriptionItem(
            id: id,
            doctorName: doctorName,
            doctorRegNo: doctorRegNo ?? "",
            patientName: patientName.isEmpty ? "Prescription" : patientName,
            extractedMedicines: extractedMedicines,
            pharmacistStatus: pharmacistStatus,
            uploadedDate: formatter.string(from: date)
        )
    }
}

public final class LocalPrescriptionManager {
    public static let shared = LocalPrescriptionManager()

    private let userDefaults: UserDefaults
    private let prefsNameKey = "commerce_os_local_prescriptions"
    private let keyPrefix = "rx_records_"
    private let prescriptionsDirName = "prescriptions"

    public init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }

    private func sanitizeCustomerId(_ customerId: String) -> String {
        let clean = customerId.replacingOccurrences(of: "[^a-zA-Z0-9_]", with: "_", options: .regularExpression)
        return clean.isEmpty ? "default_user" : clean
    }

    public var prescriptionsDirectory: URL {
        let fileManager = FileManager.default
        let baseDir = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = baseDir.appendingPathComponent(prescriptionsDirName)
        if !fileManager.fileExists(atPath: dir.path) {
            try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }

    /**
     * Saves a captured prescription UIImage to the app's private files directory.
     * Retains pristine clarity for clinical readability (95% JPEG quality).
     */
    public func savePrescriptionImage(
        image: UIImage,
        customerId: String,
        patientName: String = "",
        doctorName: String = "",
        extractedMedicines: [String] = []
    ) -> LocalPrescriptionRecord? {
        guard let data = image.jpegData(compressionQuality: 0.95) else { return nil }
        let fileName = "rx_\(Int64(Date().timeIntervalSince1970 * 1000))_\(UUID().uuidString.prefix(6)).jpg"
        let targetURL = prescriptionsDirectory.appendingPathComponent(fileName)

        do {
            try data.write(to: targetURL, options: .atomic)
            let record = LocalPrescriptionRecord(
                id: UUID().uuidString,
                customerId: customerId,
                doctorName: doctorName,
                patientName: patientName,
                pharmacistStatus: "APPROVED",
                attachments: [targetURL.path],
                extractedMedicines: extractedMedicines,
                createdAt: Int64(Date().timeIntervalSince1970 * 1000)
            )
            savePrescription(record)
            return record
        } catch {
            return nil
        }
    }

    /**
     * Retrieves all prescriptions stored on this device across all customer keys.
     */
    public func getAllPrescriptions() -> [LocalPrescriptionRecord] {
        var results: [LocalPrescriptionRecord] = []
        let allKeys = userDefaults.dictionaryRepresentation().keys
        for key in allKeys where key.hasPrefix(keyPrefix) {
            if let data = userDefaults.data(forKey: key),
               let list = try? JSONDecoder().decode([LocalPrescriptionRecord].self, from: data) {
                results.append(contentsOf: list)
            }
        }

        // Filter records whose physical files exist if local attachment is present
        let filtered = results.filter { rx in
            guard let firstAtt = rx.attachments.first, firstAtt.hasPrefix("/") else { return true }
            return FileManager.default.fileExists(atPath: firstAtt)
        }

        var seen = Set<String>()
        return filtered.filter { seen.insert($0.id).inserted }.sorted { $0.createdAt > $1.createdAt }
    }

    /**
     * Retrieves all prescriptions stored locally for a specific customerId.
     */
    public func getPrescriptions(customerId: String) -> [LocalPrescriptionRecord] {
        let safeId = sanitizeCustomerId(customerId)
        guard let data = userDefaults.data(forKey: keyPrefix + safeId),
              let list = try? JSONDecoder().decode([LocalPrescriptionRecord].self, from: data) else {
            return getAllPrescriptions()
        }

        let filtered = list.filter { rx in
            guard let firstAtt = rx.attachments.first, firstAtt.hasPrefix("/") else { return true }
            return FileManager.default.fileExists(atPath: firstAtt)
        }

        return filtered.isEmpty ? getAllPrescriptions() : filtered.sorted { $0.createdAt > $1.createdAt }
    }

    /**
     * Stores or updates a Prescription in personal storage.
     */
    public func savePrescription(_ record: LocalPrescriptionRecord) {
        let safeId = sanitizeCustomerId(record.customerId)
        var existing = getPrescriptions(customerId: record.customerId)
        existing.removeAll { $0.id == record.id }
        existing.insert(record, at: 0)

        if let data = try? JSONEncoder().encode(existing) {
            userDefaults.set(data, forKey: keyPrefix + safeId)
        }
    }

    /**
     * Removes a prescription and its associated image files from device storage.
     */
    public func deletePrescription(customerId: String, rxId: String) {
        let safeId = sanitizeCustomerId(customerId)
        var existing = getPrescriptions(customerId: customerId)
        if let target = existing.first(where: { $0.id == rxId }) {
            for path in target.attachments where path.hasPrefix("/") {
                try? FileManager.default.removeItem(atPath: path)
            }
            existing.removeAll { $0.id == rxId }
            if let data = try? JSONEncoder().encode(existing) {
                userDefaults.set(data, forKey: keyPrefix + safeId)
            }
        }
    }
}
