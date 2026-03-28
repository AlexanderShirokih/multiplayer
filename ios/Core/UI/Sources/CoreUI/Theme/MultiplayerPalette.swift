import SwiftUI

internal let ink950 = Color(hex: 0x08101F)
internal let gray900 = Color(hex: 0x121A2B)
internal let gray850 = Color(hex: 0x14213B)
internal let gray800 = Color(hex: 0x1C2740)
internal let gray750 = Color(hex: 0x1A2A48)
internal let gray700 = Color(hex: 0x34425D)
internal let gray650 = Color(hex: 0x3A4A6A)
internal let gray600 = Color(hex: 0x4B5870)
internal let gray500 = Color(hex: 0x71809B)
internal let gray400 = Color(hex: 0x9FB0CF)
internal let gray300 = Color(hex: 0xBCC6D8)
internal let gray200 = Color(hex: 0xDCE6F7)
internal let gray100 = Color(hex: 0xF6F8FC)
internal let blue500 = Color(hex: 0x356CFF)
internal let blue400 = Color(hex: 0x5388FF)
internal let blue300 = Color(hex: 0x9CB8FF)
internal let amber300 = Color(hex: 0xFFC857)
internal let amberGradientLight = Color(hex: 0xFFD86B)
internal let red300 = Color(hex: 0xFF7A7A)
internal let navy600 = Color(hex: 0x172744)
internal let blueGray50 = Color(hex: 0xEAF1FF)
internal let indigo900 = Color(hex: 0x161E34)
internal let indigo700 = Color(hex: 0x243260)
internal let slate900 = Color(hex: 0x182236)
internal let slate600 = Color(hex: 0x3B3350)
internal let lavender50 = Color(hex: 0xF5F2FF)
internal let lavender100 = Color(hex: 0xE9EEFF)
internal let peach50 = Color(hex: 0xFFF7F1)
internal let neutralBlue50 = Color(hex: 0xEEF1FF)
internal let purple400 = Color(hex: 0x7A70FF)
internal let purple300 = Color(hex: 0xA79CFF)
internal let purple200 = Color(hex: 0xC8C0FF)
internal let miniPlayerLightStart = Color(hex: 0xF8FBFF)
internal let miniPlayerLightEnd = Color(hex: 0xEAF1FF)
internal let miniPlayerDarkStart = Color(hex: 0x1A253F)
internal let miniPlayerDarkEnd = Color(hex: 0x23395E)
internal let miniPlayerLightSecondary = Color(hex: 0x536583)
internal let miniPlayerDarkSecondary = Color(hex: 0xC4D0E4)

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
