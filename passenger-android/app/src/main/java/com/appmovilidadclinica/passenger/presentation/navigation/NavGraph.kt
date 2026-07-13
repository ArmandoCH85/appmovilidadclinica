package com.appmovilidadclinica.passenger.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.appmovilidadclinica.passenger.presentation.auth.LoginScreen
import com.appmovilidadclinica.passenger.presentation.common.SessionExpiredDialog
import com.appmovilidadclinica.passenger.presentation.common.SessionViewModel
import com.appmovilidadclinica.passenger.presentation.myreservation.MyReservationDetailScreen
import com.appmovilidadclinica.passenger.presentation.myreservation.MyReservationsScreen
import com.appmovilidadclinica.passenger.presentation.seatselection.SeatSelectionScreen
import com.appmovilidadclinica.passenger.presentation.tripsearch.TripSearchScreen

/**
 * Raiz de navegacion — decide Login vs. resto de la app segun la sesion
 * (`SessionViewModel.user`), mismo espiritu que el guard de rutas del panel
 * admin (`router.ts`, `beforeEach`). Ademas muestra el modal de sesion
 * expirada de forma global, sin que cada pantalla lo repita.
 */
@Composable
fun PassengerNavGraph(navController: NavHostController = rememberNavController()) {
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val user by sessionViewModel.user.collectAsStateWithLifecycle()
    val isLoading by sessionViewModel.isLoading.collectAsStateWithLifecycle()
    val sessionExpiredVisible by sessionViewModel.sessionExpiredDialogVisible.collectAsStateWithLifecycle()

    LaunchedEffect(user) {
        val isLoggedIn = user != null
        val onLoginScreen = navController.currentDestination?.route?.contains("Login") == true
        if (isLoggedIn && onLoginScreen) {
            navController.navigate(Screen.TripSearch) {
                popUpTo(Screen.Login) { inclusive = true }
            }
        } else if (!isLoggedIn && !onLoginScreen) {
            navController.navigate(Screen.Login) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Mientras DataStore no haya emitido el primer valor de sesion, no
    // decidimos la pantalla inicial — mostramos un splash Centrado en
    // vez de parpadear Login -> TripSearch (o viceversa).
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (user != null) Screen.TripSearch else Screen.Login,
    ) {
        composable<Screen.Login> { LoginScreen() }

        composable<Screen.TripSearch> {
            TripSearchScreen(
                onTripSelected = { tripId, originStopId, destinationStopId ->
                    navController.navigate(Screen.SeatSelection(tripId, originStopId, destinationStopId))
                },
                onOpenReservations = { navController.navigate(Screen.MyReservations) },
                onLogout = { sessionViewModel.logout() },
            )
        }

        composable<Screen.SeatSelection> {
            SeatSelectionScreen(
                onReservationConfirmed = { reservationId ->
                    navController.navigate(Screen.MyReservationDetail(reservationId)) {
                        popUpTo(Screen.TripSearch)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Screen.MyReservations> {
            MyReservationsScreen(
                onReservationSelected = { navController.navigate(Screen.MyReservationDetail(it)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Screen.MyReservationDetail> {
            MyReservationDetailScreen(onBack = { navController.popBackStack() })
        }
    }

    if (sessionExpiredVisible) {
        SessionExpiredDialog(onConfirm = sessionViewModel::dismissSessionExpiredDialog)
    }
}
