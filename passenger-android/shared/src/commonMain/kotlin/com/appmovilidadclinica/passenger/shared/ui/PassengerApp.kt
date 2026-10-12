package com.appmovilidadclinica.passenger.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.appmovilidadclinica.passenger.shared.di.PassengerSharedGraph
import com.appmovilidadclinica.passenger.shared.ui.home.HomeScreen
import com.appmovilidadclinica.passenger.shared.ui.login.LoginScreen
import com.appmovilidadclinica.passenger.shared.ui.theme.PassengerAppTheme

/**
 * Raiz de la app compartida (Android + iOS).
 *
 * Decide que mostrar segun la sesion persistida: si hay sesion, la pantalla
 * autenticada; si no, el login. Como la sesion se guarda en `SessionStore`
 * (SharedPreferences / NSUserDefaults), al reabrir la app el usuario sigue
 * adentro, y "Cerrar sesión" la borra y vuelve al login.
 */
@Composable
fun PassengerApp(graph: PassengerSharedGraph) {
    PassengerAppTheme {
        val session by graph.sessionStore.sessionState.collectAsState()
        val current = session

        if (current == null) {
            LoginScreen(
                sessionStore = graph.sessionStore,
                signIn = graph::signIn,
            )
        } else {
            HomeScreen(
                session = current,
                onLogout = { graph.sessionStore.clear() },
            )
        }
    }
}
