package com.appmovilidadclinica.driver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.appmovilidadclinica.driver.presentation.qrscan.QrScanScreen
import com.appmovilidadclinica.driver.shared.ui.navigation.DriverNavGraph
import com.appmovilidadclinica.driver.shared.ui.navigation.DriverNavState
import com.appmovilidadclinica.driver.shared.ui.navigation.Route

class MainActivity : ComponentActivity() {

    /**
     * Back stack de navegacion elevado a la Activity: el boton/gesto de volver
     * del sistema debe desapilar la pantalla actual en vez de cerrar la app.
     */
    private val navState = DriverNavState(Route.Login)

    private val locationPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Resultado ignorado: si se deniega, el tracking simplemente no arranca
        // (TripLocationService ya no crashea sin permiso).
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // El tracking del viaje (FGS tipo location) necesita este permiso en
        // runtime. Se pide una sola vez si aun no esta concedido.
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }

        // Volver atras: si hay pantallas apiladas (detalle de viaje, mapa de
        // asientos, incidencia, QR, perfil) se desapila la ultima. En la raiz
        // (login / lista de viajes) se deja el comportamiento por defecto para
        // que el conductor pueda salir de la app.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (navState.stack.value.size > 1) {
                        navState.pop()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )

        setContent {
            // DriverNavGraph ya envuelve en DriverAppTheme internamente.
            DriverNavGraph(
                navState = navState,
                qrScanContent = { tripId, onBack ->
                    QrScanScreen(tripId = tripId, onBack = onBack)
                },
            )
        }
    }
}
