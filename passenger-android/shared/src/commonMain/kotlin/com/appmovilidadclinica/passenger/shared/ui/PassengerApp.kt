package com.appmovilidadclinica.passenger.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.appmovilidadclinica.passenger.shared.di.PassengerSharedGraph
import com.appmovilidadclinica.passenger.shared.ui.common.ComingSoonScreen
import com.appmovilidadclinica.passenger.shared.ui.home.HomeScreen
import com.appmovilidadclinica.passenger.shared.ui.login.LoginScreen
import com.appmovilidadclinica.passenger.shared.ui.navigation.PassengerNavState
import com.appmovilidadclinica.passenger.shared.ui.navigation.PassengerRoute
import com.appmovilidadclinica.passenger.shared.ui.theme.PassengerAppTheme
import com.appmovilidadclinica.passenger.shared.ui.tripsearch.TripSearchController
import com.appmovilidadclinica.passenger.shared.ui.tripsearch.TripSearchScreen

/**
 * Raiz de la app compartida (Android + iOS).
 *
 * Dos niveles: si no hay sesion, el login; si hay, la navegacion de la app
 * autenticada (por estado, sin `androidx.navigation`), igual que el conductor.
 * La sesion vive en `SessionStore` (SharedPreferences / NSUserDefaults), asi
 * que al reabrir la app el usuario sigue adentro y "Cerrar sesión" vuelve al
 * login.
 */
@Composable
fun PassengerApp(graph: PassengerSharedGraph) {
    PassengerAppTheme {
        val session by graph.sessionStore.sessionState.collectAsState()
        val navState = remember { PassengerNavState(PassengerRoute.Home) }
        val stack by navState.stack.collectAsState()

        val current = session
        if (current == null) {
            LoginScreen(
                sessionStore = graph.sessionStore,
                signIn = graph::signIn,
            )
        } else {
            when (val route = stack.last()) {
                PassengerRoute.Home -> HomeScreen(
                    session = current,
                    onSearchTrips = { navState.push(PassengerRoute.TripSearch) },
                    onOpenReservations = { navState.push(PassengerRoute.Reservations) },
                    onChangePassword = { navState.push(PassengerRoute.ChangePassword) },
                    onReportIncident = { navState.push(PassengerRoute.ReportIncident) },
                    onLogout = { graph.sessionStore.clear() },
                )

                PassengerRoute.TripSearch -> {
                    val controller = remember {
                        TripSearchController(
                            loadStops = graph::loadStops,
                            searchTrips = graph::searchTrips,
                        )
                    }
                    DisposableEffect(controller) { onDispose { controller.dispose() } }

                    TripSearchScreen(
                        controller = controller,
                        onTripSelected = { tripId, originStopId, destinationStopId ->
                            navState.push(
                                PassengerRoute.TripDetail(tripId, originStopId, destinationStopId)
                            )
                        },
                        onLogout = { graph.sessionStore.clear() },
                    )
                }

                is PassengerRoute.TripDetail -> ComingSoonScreen(
                    title = "Selección de asiento",
                    onBack = { navState.pop() },
                )

                PassengerRoute.Reservations -> ComingSoonScreen(
                    title = "Mis reservas",
                    onBack = { navState.pop() },
                )

                is PassengerRoute.ReservationDetail -> ComingSoonScreen(
                    title = "Detalle de la reserva",
                    onBack = { navState.pop() },
                )

                PassengerRoute.ChangePassword -> ComingSoonScreen(
                    title = "Cambiar contraseña",
                    onBack = { navState.pop() },
                )

                PassengerRoute.ReportIncident -> ComingSoonScreen(
                    title = "Reportar incidencia",
                    onBack = { navState.pop() },
                )
            }
        }
    }
}
