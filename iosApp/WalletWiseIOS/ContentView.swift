import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("WalletWise")
                .font(.title.bold())
            Text("Khung iOS đã sẵn sàng để tích hợp Compose Multiplatform.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .padding(24)
    }
}

#Preview {
    ContentView()
}
