import Foundation

// MARK: - Search History Manager (Ported from Android SearchHistoryManager.kt)
public class SearchHistoryManager: ObservableObject {
    public static let shared = SearchHistoryManager()
    
    private let userDefaultsKey = "commerce_os_recent_searches"
    private let maxHistoryItems = 10
    
    @Published public private(set) var history: [String] = []
    
    public init() {
        loadHistory()
    }
    
    private func loadHistory() {
        if let saved = UserDefaults.standard.stringArray(forKey: userDefaultsKey) {
            self.history = saved
        } else {
            self.history = ["Dolo 650", "Pan-D", "Crocin 650", "Augmentin 625"]
        }
    }
    
    public func addQuery(_ query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        
        var current = history.filter { $0.caseInsensitiveCompare(trimmed) != .orderedSame }
        current.insert(trimmed, at: 0)
        
        if current.count > maxHistoryItems {
            current = Array(current.prefix(maxHistoryItems))
        }
        
        self.history = current
        UserDefaults.standard.set(current, forKey: userDefaultsKey)
    }
    
    public func removeQuery(_ query: String) {
        let updated = history.filter { $0 != query }
        self.history = updated
        UserDefaults.standard.set(updated, forKey: userDefaultsKey)
    }
    
    public func clearAll() {
        self.history = []
        UserDefaults.standard.removeObject(forKey: userDefaultsKey)
    }
}
