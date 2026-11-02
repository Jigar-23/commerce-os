import SwiftUI

/**
 * 1:1 Parity with Android AddressTagSelector.kt and Zomato-grade Pill Chips.
 */
public struct AddressTagSelector: View {
    public let selectedTag: String
    public let onTagSelected: (String) -> Void

    private let availableTags = ["Home", "Work", "Other"]

    public init(selectedTag: String, onTagSelected: @escaping (String) -> Void) {
        self.selectedTag = selectedTag
        self.onTagSelected = onTagSelected
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Save address as:")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Color(hex: "64748B"))

            HStack(spacing: 8) {
                ForEach(availableTags, id: \.self) { tag in
                    let isSelected = selectedTag.caseInsensitiveCompare(tag) == .orderedSame
                    Button(action: {
                        HapticFeedbackEngine.shared.triggerSelection()
                        onTagSelected(tag)
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: iconForTag(tag))
                                .font(.system(size: 13, weight: .bold))
                            Text(tag)
                                .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(isSelected ? CommerceOSTheme.Colors.brandPrimarySoft : Color(hex: "F8FAFC"))
                        .foregroundColor(isSelected ? CommerceOSTheme.Colors.brandPrimary : Color(hex: "334155"))
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(isSelected ? CommerceOSTheme.Colors.brandPrimary : Color(hex: "E2E8F0"), lineWidth: isSelected ? 1.5 : 1)
                        )
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
    }

    private func iconForTag(_ tag: String) -> String {
        switch tag.lowercased() {
        case "work": return "briefcase.fill"
        case "other": return "mappin.and.ellipse"
        default: return "house.fill"
        }
    }
}
