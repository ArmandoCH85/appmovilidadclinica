import SwiftUI
import Shared // framework estatico generado por el modulo :shared (baseName = "Shared")

/// Monta la UI de Compose Multiplatform (la misma que usa Android) dentro de
/// SwiftUI. El punto de entrada Kotlin es `MainViewController()`, que se ve
/// desde Swift como `MainViewControllerKt.MainViewController()`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Compose maneja el teclado: dejamos que la vista ocupe todo.
            .ignoresSafeArea(.keyboard)
    }
}
