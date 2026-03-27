import SwiftUI

internal let ink950 = Color(hex: 0x0B1020)
internal let gray900 = Color(hex: 0x121A2B)
internal let gray800 = Color(hex: 0x1C2740)
internal let gray700 = Color(hex: 0x34425D)
internal let gray600 = Color(hex: 0x4B5870)
internal let gray500 = Color(hex: 0x71809B)
internal let gray300 = Color(hex: 0xBCC6D8)
internal let gray200 = Color(hex: 0xE7ECF4)
internal let gray100 = Color(hex: 0xF6F8FC)
internal let blue500 = Color(hex: 0x356CFF)
internal let blue400 = Color(hex: 0x5388FF)
internal let blue300 = Color(hex: 0x9CB8FF)
internal let amber300 = Color(hex: 0xFFC857)
internal let amberGradientLight = Color(hex: 0xFFD86B)
internal let red300 = Color(hex: 0xFF7A7A)

private extension Color {
    init(hex: UInt, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}
