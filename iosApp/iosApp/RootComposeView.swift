import SwiftUI
import shared

struct RootComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {

        RootViewControllerKt.RootViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
