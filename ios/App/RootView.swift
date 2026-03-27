import SwiftUI

struct RootView: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 0.04, green: 0.09, blue: 0.18),
                    Color(red: 0.09, green: 0.17, blue: 0.33),
                    Color(red: 0.14, green: 0.26, blue: 0.49),
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing,
            )
            .ignoresSafeArea()

            VStack(spacing: 16) {
                Image("AppIconPreview", bundle: nil)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 112, height: 112)
                    .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .shadow(color: Color.black.opacity(0.18), radius: 24, y: 14)

                Text("MultiPlayer")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)

                Text("iOS foundation is ready. Feature modules can now grow from the app entry point.")
                    .font(.system(size: 16, weight: .medium, design: .rounded))
                    .foregroundStyle(Color.white.opacity(0.78))
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 320)
            }
            .padding(32)
        }
    }
}

#Preview {
    RootView()
}
