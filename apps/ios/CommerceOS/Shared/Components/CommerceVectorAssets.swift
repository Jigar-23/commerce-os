import SwiftUI

/**
 * 1:1 Swift equivalents for Android XML Vector Drawables:
 * - ic_thin_search.xml
 * - ic_mic.xml
 * - ic_camera.xml
 * - ic_medicine.xml
 * - ic_doctor.xml
 * - ic_lab_test.xml
 * - ic_wellness.xml
 * - ic_visibility.xml
 */
public enum CommerceVectorAsset {
    case search
    case mic
    case camera
    case medicine
    case doctor
    case labTest
    case wellness
    case visibility(isVisible: Bool)
}

public struct CommerceVectorIcon: View {
    public let asset: CommerceVectorAsset
    public var size: CGFloat = 24
    public var color: Color = Color(hex: "0F172A")

    public init(_ asset: CommerceVectorAsset, size: CGFloat = 24, color: Color = Color(hex: "0F172A")) {
        self.asset = asset
        self.size = size
        self.color = color
    }

    public var body: some View {
        Group {
            switch asset {
            case .search:
                // ic_thin_search.xml: stroke 1.8, circle at 10.5, line to 21.5
                ZStack {
                    Circle()
                        .stroke(color, lineWidth: size * (1.8 / 24.0))
                        .frame(width: size * (15.0 / 24.0), height: size * (15.0 / 24.0))
                        .offset(x: -size * (1.5 / 24.0), y: -size * (1.5 / 24.0))

                    Path { path in
                        path.move(to: CGPoint(x: size * (16.0 / 24.0), y: size * (16.0 / 24.0)))
                        path.addLine(to: CGPoint(x: size * (21.5 / 24.0), y: size * (21.5 / 24.0)))
                    }
                    .stroke(color, style: StrokeStyle(lineWidth: size * (1.8 / 24.0), lineCap: .round))
                }
                .frame(width: size, height: size)

            case .mic:
                Image(systemName: "mic.fill")
                    .resizable()
                    .scaledToFit()
                    .foregroundColor(color)
                    .frame(width: size, height: size)

            case .camera:
                Image(systemName: "camera.fill")
                    .resizable()
                    .scaledToFit()
                    .foregroundColor(color)
                    .frame(width: size, height: size)

            case .medicine:
                Image(systemName: "pills.fill")
                    .resizable()
                    .scaledToFit()
                    .foregroundColor(color)
                    .frame(width: size, height: size)

            case .doctor:
                Image(systemName: "stethoscope")
                    .resizable()
                    .scaledToFit()
                    .foregroundColor(color)
                    .frame(width: size, height: size)

            case .labTest:
                Image(systemName: "cross.vial.fill")
                    .resizable()
                    .scaledToFit()
                    .foregroundColor(color)
                    .frame(width: size, height: size)

            case .wellness:
                Image(systemName: "heart.text.square.fill")
                    .resizable()
                    .scaledToFit()
                    .foregroundColor(color)
                    .frame(width: size, height: size)

            case .visibility(let isVisible):
                Image(systemName: isVisible ? "eye.fill" : "eye.slash.fill")
                    .resizable()
                    .scaledToFit()
                    .foregroundColor(color)
                    .frame(width: size, height: size)
            }
        }
        .frame(width: size, height: size)
    }
}
