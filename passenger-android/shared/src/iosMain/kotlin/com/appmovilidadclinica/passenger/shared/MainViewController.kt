package com.appmovilidadclinica.passenger.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.appmovilidadclinica.passenger.shared.di.PassengerSharedGraph
import com.appmovilidadclinica.passenger.shared.ui.PassengerApp
import platform.UIKit.UIViewController

/**
 * Punto de entrada para iOS: el proyecto `iosApp` (Xcode) llama a esta funcion
 * y monta el controller dentro de su jerarquia de UIKit.
 *
 * Desde Swift se ve como `MainViewControllerKt.MainViewController()`.
 * El framework se llama `Shared` y es estatico (ver `shared/build.gradle.kts`).
 */
fun MainViewController(): UIViewController {
    val graph = PassengerSharedGraph.create()
    return ComposeUIViewController {
        PassengerApp(graph)
    }
}
