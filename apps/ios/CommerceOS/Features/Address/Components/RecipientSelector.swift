import SwiftUI

/**
 * 1:1 Parity with Android RecipientSelector.kt
 */
public struct RecipientSelector: View {
    public let recipientType: RecipientType
    public let contactName: String
    public let contactPhone: String
    public let nameError: String?
    public let phoneError: String?
    public let onRecipientTypeChanged: (RecipientType) -> Void
    public let onContactNameChanged: (String) -> Void
    public let onContactPhoneChanged: (String) -> Void

    public init(
        recipientType: RecipientType,
        contactName: String,
        contactPhone: String,
        nameError: String? = nil,
        phoneError: String? = nil,
        onRecipientTypeChanged: @escaping (RecipientType) -> Void,
        onContactNameChanged: @escaping (String) -> Void,
        onContactPhoneChanged: @escaping (String) -> Void
    ) {
        self.recipientType = recipientType
        self.contactName = contactName
        self.contactPhone = contactPhone
        self.nameError = nameError
        self.phoneError = phoneError
        self.onRecipientTypeChanged = onRecipientTypeChanged
        self.onContactNameChanged = onContactNameChanged
        self.onContactPhoneChanged = onContactPhoneChanged
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Deliver to:")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Color(hex: "64748B"))

            HStack(spacing: 8) {
                Button(action: { onRecipientTypeChanged(.me) }) {
                    Text("Me")
                        .font(.system(size: 12, weight: .bold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(recipientType == .me ? Color(hex: "16A34A") : Color(hex: "F1F5F9"))
                        .foregroundColor(recipientType == .me ? .white : Color(hex: "334155"))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(recipientType == .me ? Color(hex: "15803D") : Color(hex: "E2E8F0"), lineWidth: 1)
                        )
                }
                .buttonStyle(PlainButtonStyle())

                Button(action: { onRecipientTypeChanged(.someoneElse) }) {
                    Text("Someone else")
                        .font(.system(size: 12, weight: .bold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(recipientType == .someoneElse ? Color(hex: "16A34A") : Color(hex: "F1F5F9"))
                        .foregroundColor(recipientType == .someoneElse ? .white : Color(hex: "334155"))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(recipientType == .someoneElse ? Color(hex: "15803D") : Color(hex: "E2E8F0"), lineWidth: 1)
                        )
                }
                .buttonStyle(PlainButtonStyle())
            }

            if recipientType == .someoneElse {
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Recipient Full Name *", text: Binding(
                        get: { contactName },
                        set: { onContactNameChanged($0) }
                    ))
                    .font(.system(size: 13))
                    .padding(10)
                    .background(Color.white)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(nameError != nil ? Color(hex: "EF4444") : Color(hex: "CBD5E1"), lineWidth: 1)
                    )

                    if let err = nameError {
                        Text(err)
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "EF4444"))
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    TextField("Recipient Mobile Number (+91) *", text: Binding(
                        get: { contactPhone },
                        set: { onContactPhoneChanged($0) }
                    ))
                    .keyboardType(.phonePad)
                    .font(.system(size: 13))
                    .padding(10)
                    .background(Color.white)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(phoneError != nil ? Color(hex: "EF4444") : Color(hex: "CBD5E1"), lineWidth: 1)
                    )

                    if let err = phoneError {
                        Text(err)
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "EF4444"))
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Phone Number for Delivery Updates *", text: Binding(
                        get: { contactPhone },
                        set: { onContactPhoneChanged($0) }
                    ))
                    .keyboardType(.phonePad)
                    .font(.system(size: 13))
                    .padding(10)
                    .background(Color.white)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(phoneError != nil ? Color(hex: "EF4444") : Color(hex: "CBD5E1"), lineWidth: 1)
                    )

                    if let err = phoneError {
                        Text(err)
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "EF4444"))
                    }
                }
            }
        }
    }
}
