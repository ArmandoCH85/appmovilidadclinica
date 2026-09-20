package com.appmovilidadclinica.driver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.appmovilidadclinica.driver.presentation.qrscan.QrScanScreen
import com.appmovilidadclinica.driver.shared.ui.navigation.DriverNavGraph

class MainActivity : ComponentActivity() {

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

        setContent {
            // DriverNavGraph ya envuelve en DriverAppTheme internamente.
            DriverNavGraph(
                qrScanContent = { tripId, onBack ->
                    QrScanScreen(tripId = tripId, onBack = onBack)
                },
            )
        }
    }
}
