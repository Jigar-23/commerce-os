import Foundation

// MARK: - Typo Correction Result (Ported from Android SearchTypoSynonymEngine.kt)
public struct TypoCorrectionResult: Equatable {
    public let originalQuery: String
    public let correctedQuery: String
    public let wasCorrected: Bool
    
    public init(originalQuery: String, correctedQuery: String, wasCorrected: Bool) {
        self.originalQuery = originalQuery
        self.correctedQuery = correctedQuery
        self.wasCorrected = wasCorrected
    }
}

// MARK: - Search Typo Correction & Synonym Expansion Engine
public enum SearchTypoSynonymEngine {
    private static let synonymMap: [String: [String]] = [
        "crocin": ["paracetamol", "fever medicine"],
        "dolo": ["paracetamol", "pain relief"],
        "pan": ["pantoprazole", "acidity relief"],
        "tshirt": ["t-shirt", "apparel"],
        "phone": ["smartphone", "mobile"],
        "laptop": ["notebook", "computer"],
        "plumber": ["pipe repair", "drainage"]
    ]
    
    private static let commonTypos: [String: String] = [
        "paracetaml": "paracetamol",
        "paracetmol": "paracetamol",
        "smartfone": "smartphone",
        "leptop": "laptop",
        "t-shrt": "t-shirt",
        "fasion": "fashion",
        "dollo": "dolo",
        "crocine": "crocin",
        "pan-d": "pand"
    ]
    
    public static func correctTypo(query: String) -> TypoCorrectionResult {
        let lower = query.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let corrected = commonTypos[lower] ?? query
        return TypoCorrectionResult(
            originalQuery: query,
            correctedQuery: corrected,
            wasCorrected: corrected != query
        )
    }
    
    public static func getSynonyms(query: String) -> [String] {
        let lower = query.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        return synonymMap[lower] ?? []
    }
    
    public static func getZeroResultSuggestions(query: String) -> [String] {
        let corrected = correctTypo(query: query).correctedQuery
        let synonyms = getSynonyms(query: corrected)
        var suggestions = [corrected] + synonyms + ["Top Medicines", "Popular Deals"]
        var seen = Set<String>()
        var unique: [String] = []
        for s in suggestions {
            if !seen.contains(s.lowercased()) {
                seen.insert(s.lowercased())
                unique.append(s)
            }
        }
        return Array(unique.prefix(4))
    }
}
