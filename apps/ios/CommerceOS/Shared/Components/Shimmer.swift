import SwiftUI

// MARK: - Shimmer Loading Effect (Ported from Android Shimmer.kt)
/// Skeleton / shimmer loading language. Placeholders establish structure (size,
/// text hierarchy) before network data arrives instead of a blank spinner.
public struct ShimmerModifier: ViewModifier {
    @State private var phase: CGFloat = -1.0
    var duration: Double = 1.2
    var bounce: Bool = false
    
    public func body(content: Content) -> some View {
        content
            .overlay(
                GeometryReader { geo in
                    let width = geo.size.width
                    let height = geo.size.height
                    
                    LinearGradient(
                        gradient: Gradient(stops: [
                            .init(color: Color.white.opacity(0.0), location: 0.0),
                            .init(color: Color.white.opacity(0.45), location: 0.5),
                            .init(color: Color.white.opacity(0.0), location: 1.0)
                        ]),
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: max(width * 1.5, 200), height: height)
                    .offset(x: phase * (width + 200) - 100)
                    .mask(content)
                }
            )
            .onAppear {
                withAnimation(
                    Animation.linear(duration: duration)
                        .repeatForever(autoreverses: bounce)
                ) {
                    phase = 1.5
                }
            }
    }
}

public extension View {
    func shimmering(duration: Double = 1.2, bounce: Bool = false) -> some View {
        modifier(ShimmerModifier(duration: duration, bounce: bounce))
    }
}

// MARK: - Shimmer Box
public struct ShimmerBox: View {
    public var width: CGFloat? = nil
    public var height: CGFloat = 20
    public var cornerRadius: CGFloat = 8
    public var color: Color
    
    public init(width: CGFloat? = nil, height: CGFloat = 20, cornerRadius: CGFloat = 8, color: Color? = nil) {
        self.width = width
        self.height = height
        self.cornerRadius = cornerRadius
        self.color = color ?? Color(hex: "F1F5F9")
    }
    
    public var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(color)
            .frame(width: width, height: height)
            .shimmering()
    }
}

// MARK: - Skeleton Text
public struct SkeletonText: View {
    public var width: CGFloat = 120
    public var height: CGFloat = 14
    public var cornerRadius: CGFloat = 4
    
    public init(width: CGFloat = 120, height: CGFloat = 14, cornerRadius: CGFloat = 4) {
        self.width = width
        self.height = height
        self.cornerRadius = cornerRadius
    }
    
    public var body: some View {
        ShimmerBox(width: width, height: height, cornerRadius: cornerRadius)
    }
}

// MARK: - Skeleton Product Image
public struct SkeletonProductImage: View {
    public var size: CGFloat = 92
    public var cornerRadius: CGFloat = 10
    
    public init(size: CGFloat = 92, cornerRadius: CGFloat = 10) {
        self.size = size
        self.cornerRadius = cornerRadius
    }
    
    public var body: some View {
        ShimmerBox(width: size, height: size, cornerRadius: cornerRadius, color: Color.gray.opacity(0.15))
    }
}
