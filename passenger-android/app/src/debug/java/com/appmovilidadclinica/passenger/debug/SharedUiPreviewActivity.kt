package com.appmovilidadclinica.passenger.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.appmovilidadclinica.passenger.shared.di.PassengerSharedGraph
import com.appmovilidadclinica.passenger.shared.ui.PassengerApp

/**
 * SOLO DEBUG. Monta la UI que vive en `shared` (login + sesion + home), la
 * misma que va a correr en iOS, para poder verla en Android mientras la app
 * real sigue con su propia navegacion (migracion por etapas).
 *
 * Lanzar con:
 * ```
 * adb shell am start -n com.sitech.clinica.empleados/com.appmovilidadclinica.passenger.debug.SharedUiPreviewActivity
 * ```
 */
class SharedUiPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // El grafo usa SharedAndroidContext (lo inicializa PassengerApp.onCreate)
        // y el mismo SessionStore de la app: la sesion que se ve aca es la real.
        val graph = PassengerSharedGraph.create()

        setContent {
            PassengerApp(graph)
        }
    }
}
