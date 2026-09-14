import SwiftUI

/**
 * 1:1 Parity with Android DeliveryInstructionsField.kt
 */
public struct DeliveryInstructionsField: View {
    public let instructions: String
    public let onInstructionsChanged: (String) -> Void

    private let presetChips = [
        "Leave with security",
        "Call on arrival",
        "Don't ring bell",
        "Leave at door"
    ]

    public init(instructions: String, onInstructionsChanged: @escaping (String) -> Void) {
        self.instructions = instructions
        self.onInstructionsChanged = onInstructionsChanged
    }

    private var activePresets: Set<String> {
        let parts = instructions.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        return Set(parts)
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Delivery instructions:")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Color(hex: "64748B"))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(presetChips, id: \.self) { preset in
                        let isPresent = activePresets.contains(where: { $0.caseInsensitiveCompare(preset) == .orderedSame })
                        Button(action: {
                            var current = activePresets
                            if isPresent {
                                current = current.filter { $0.caseInsensitiveCompare(preset) != .orderedSame }
                            } else {
                                current.insert(preset)
                            }
                            onInstructionsChanged(current.joined(separator: ", "))
                        }) {
                            Text(preset)
                                .font(.system(size: 11, weight: .bold))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(isPresent ? Color(hex: "DCFCE7") : Color(hex: "F8FAFC"))
                                .foregroundColor(isPresent ? Color(hex: "166534") : Color(hex: "334155"))
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(isPresent ? Color(hex: "16A34A") : Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }

            TextField("Specific instructions (optional)", text: Binding(
                get: { instructions },
                set: { onInstructionsChanged($0) }
            ))
            .font(.system(size: 13))
            .padding(10)
            .background(Color.white)
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color(hex: "CBD5E1"), lineWidth: 1)
            )
        }
    }
}
