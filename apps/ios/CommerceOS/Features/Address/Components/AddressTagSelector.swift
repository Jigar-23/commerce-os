import SwiftUI

/**
 * 1:1 Parity with Android AddressTagSelector.kt
 */
public struct AddressTagSelector: View {
    public let selectedTag: String
    public let onTagSelected: (String) -> Void

    private let availableTags = ["Home", "Work", "Family", "Other"]

    public init(selectedTag: String, onTagSelected: @escaping (String) -> Void) {
        self.selectedTag = selectedTag
        self.onTagSelected = onTagSelected
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Save as:")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Color(hex: "64748B"))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(availableTags, id: \.self) { tag in
                        let isSelected = selectedTag.caseInsensitiveCompare(tag) == .orderedSame
                        Button(action: { onTagSelected(tag) }) {
                            HStack(spacing: 5) {
                                Image(systemName: iconForTag(tag))
                                    .font(.system(size: 11, weight: .semibold))
                                Text(tag)
                                    .font(.system(size: 12, weight: .bold))
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(isSelected ? Color(hex: "16A34A") : Color(hex: "F1F5F9"))
                            .foregroundColor(isSelected ? .white : Color(hex: "334155"))
                            .cornerRadius(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(isSelected ? Color(hex: "15803D") : Color(hex: "E2E8F0"), lineWidth: 1)
                            )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
    }

    private func iconForTag(_ tag: String) -> String {
        switch tag.lowercased() {
        case "work": return "briefcase.fill"
        case "family": return "person.2.fill"
        case "other": return "star.fill"
        default: return "house.fill"
        }
    }
}
