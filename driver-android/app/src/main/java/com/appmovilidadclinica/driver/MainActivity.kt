package com.appmovilidadclinica.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.appmovilidadclinica.driver.presentation.qrscan.QrScanScreen
import com.appmovilidadclinica.driver.shared.ui.navigation.DriverNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
