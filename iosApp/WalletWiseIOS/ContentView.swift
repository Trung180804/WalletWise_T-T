import SwiftUI
import UIKit
import WalletWiseShared

struct ContentView: View {
    var body: some View {
        ComposeAuthView()
            .ignoresSafeArea()
    }
}

private struct ComposeAuthView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        WalletWiseComposeViewControllerKt.walletWiseComposeViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

#Preview {
    ContentView()
}
