package com.appmovilidadclinica.passenger.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.appmovilidadclinica.passenger.shared.di.PassengerSharedGraph
import com.appmovilidadclinica.passenger.shared.ui.changepassword.ChangePasswordController
import com.appmovilidadclinica.passenger.shared.ui.changepassword.ChangePasswordScreen
import com.appmovilidadclinica.passenger.shared.ui.home.HomeScreen
import com.appmovilidadclinica.passenger.shared.ui.login.LoginScreen
import com.appmovilidadclinica.passenger.shared.ui.navigation.PassengerNavState
import com.appmovilidadclinica.passenger.shared.ui.navigation.PassengerRoute
import com.appmovilidadclinica.passenger.shared.ui.reportincident.ReportIncidentController
import com.appmovilidadclinica.passenger.shared.ui.reportincident.ReportIncidentScreen
import com.appmovilidadclinica.passenger.shared.ui.reservations.ReservationDetailScreen
import com.appmovilidadclinica.passenger.shared.ui.reservations.ReservationsController
import com.appmovilidadclinica.passenger.shared.ui.reservations.ReservationsScreen
import com.appmovilidadclinica.passenger.shared.ui.seatselection.SeatSelectionController
import com.appmovilidadclinica.passenger.shared.ui.seatselection.SeatSelectionScreen
import com.appmovilidadclinica.passenger.shared.ui.theme.PassengerAppTheme
import com.appmovilidadclinica.passenger.shared.ui.tripsearch.TripSearchController
import com.appmovilidadclinica.passenger.shared.ui.tripsearch.TripSearchScreen

/**
 * Raiz de la app compartida (Android + iOS).
 *
 * Un nivel: si no hay sesion, el login; si hay, la navegacion autenticada por
 * estado (sin `androidx.navigation`), igual que la app del conductor. La sesion
 * vive en `SessionStore` (SharedPreferences / NSUserDefaults), asi que al
 * reabrir la app el usuario sigue adentro y "Cerrar sesión" vuelve al login.
 */
@Composable
fun PassengerApp(graph: PassengerSharedGraph) {
    PassengerAppTheme {
        val session by graph.sessionStore.sessionState.collectAsState()
        val navState = remember { PassengerNavState(PassengerRoute.Home) }
        val stack by navState.stack.collectAsState()

        // Una sola instancia de "Mis reservas" para la lista y el detalle:
        // asi el detalle encuentra la reserva ya cargada.
        val reservationsController = remember {
            ReservationsController(
                loadReservations = graph::loadReservations,
                cancelReservation = graph::cancelReservation,
            )
        }
        DisposableEffect(reservationsController) {
            onDispose { reservationsController.dispose() }
        }

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
                    onOpenReservations = {
                        reservationsController.load(force = true)
                        navState.push(PassengerRoute.Reservations)
                    },
                    onChangePassword = { navState.push(PassengerRoute.ChangePassword) },
                    onReportIncident = { navState.push(PassengerRoute.ReportIncident) },
                    onLogout = {
                        reservationsController.dispose()
                        graph.sessionStore.clear()
                    },
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
                        onBack = { navState.pop() },
                        onLogout = { graph.sessionStore.clear() },
                    )
                }

                is PassengerRoute.TripDetail -> {
                    val controller = remember(route) {
                        SeatSelectionController(
                            tripId = route.tripId,
                            originStopId = route.originStopId,
                            destinationStopId = route.destinationStopId,
                            loadTripDetail = graph::loadTripDetail,
                            listSeats = graph::listSeats,
                            confirmReservation = graph::confirmReservation,
                        )
                    }
                    SeatSelectionScreen(
                        controller = controller,
                        onBack = { navState.pop() },
                        onConfirmed = { reservationId ->
                            // Se refleja en "Mis reservas" y se abre el detalle.
                            reservationsController.load(force = true)
                            navState.replace(PassengerRoute.ReservationDetail(reservationId))
                        },
                    )
                }

                PassengerRoute.Reservations -> ReservationsScreen(
                    controller = reservationsController,
                    onOpenReservation = { reservationId ->
                        navState.push(PassengerRoute.ReservationDetail(reservationId))
                    },
                    onBack = { navState.pop() },
                )

                is PassengerRoute.ReservationDetail -> ReservationDetailScreen(
                    controller = reservationsController,
                    reservationId = route.reservationId,
                    onBack = { navState.pop() },
                )

                PassengerRoute.ChangePassword -> {
                    val controller = remember {
                        ChangePasswordController(changePassword = graph::changePassword)
                    }
                    ChangePasswordScreen(controller = controller, onBack = { navState.pop() })
                }

                PassengerRoute.ReportIncident -> {
                    val controller = remember {
                        ReportIncidentController(
                            loadReservations = graph::loadReservations,
                            reportIncident = graph::reportIncident,
                        )
                    }
                    ReportIncidentScreen(
                        controller = controller,
                        onBack = { navState.pop() },
                        onSent = { navState.pop() },
                    )
                }
            }
        }
    }
}
