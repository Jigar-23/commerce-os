import Foundation
import UIKit

/**
 * 1:1 Parity with Android LocalProfileManager.kt.
 * Zero remote bandwidth/storage customer avatar & custom name on-device store.
 */
public final class LocalProfileManager {
    public static let shared = LocalProfileManager()
    
    private let keyPrefixName = "custom_name_"
    private let profilesDirName = "profiles"
    
    private init() {}
    
    private func sanitizeCustomerId(_ customerId: String) -> String {
        let clean = customerId.replacingOccurrences(of: "[^a-zA-Z0-9_]", with: "_", options: .regularExpression)
        return clean.isEmpty ? "default_user" : clean
    }
    
    private func getProfilesDir() -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent(profilesDirName, isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }
    
    public func getCustomName(customerId: String) -> String? {
        let safeId = sanitizeCustomerId(customerId)
        guard let saved = UserDefaults.standard.string(forKey: keyPrefixName + safeId)?.trimmingCharacters(in: .whitespacesAndNewlines), !saved.isEmpty else {
            return nil
        }
        if saved.lowercased().hasPrefix("customer") {
            UserDefaults.standard.removeObject(forKey: keyPrefixName + safeId)
            return nil
        }
        return saved
    }
    
    public func saveCustomName(customerId: String, name: String) {
        let safeId = sanitizeCustomerId(customerId)
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty {
            UserDefaults.standard.removeObject(forKey: keyPrefixName + safeId)
        } else {
            UserDefaults.standard.set(clean, forKey: keyPrefixName + safeId)
        }
    }
    
    public func getAvatarImage(customerId: String) -> UIImage? {
        let safeId = sanitizeCustomerId(customerId)
        let fileUrl = getProfilesDir().appendingPathComponent("avatar_\(safeId).jpg")
        guard FileManager.default.fileExists(atPath: fileUrl.path),
              let data = try? Data(contentsOf: fileUrl),
              let image = UIImage(data: data) else {
            return nil
        }
        return image
    }
    
    public func saveAvatarImage(customerId: String, image: UIImage) -> Bool {
        let safeId = sanitizeCustomerId(customerId)
        let fileUrl = getProfilesDir().appendingPathComponent("avatar_\(safeId).jpg")
        guard let data = image.jpegData(compressionQuality: 0.85) else { return false }
        do {
            try data.write(to: fileUrl)
            return true
        } catch {
            return false
        }
    }
    
    public func removeAvatar(customerId: String) {
        let safeId = sanitizeCustomerId(customerId)
        let fileUrl = getProfilesDir().appendingPathComponent("avatar_\(safeId).jpg")
        try? FileManager.default.removeItem(at: fileUrl)
    }
}
