package com.appmovilidadclinica.driver.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.appmovilidadclinica.driver.di.AppModule
import com.appmovilidadclinica.driver.presentation.dashboard.DashboardScreen
import com.appmovilidadclinica.driver.presentation.incident.IncidentScreen
import com.appmovilidadclinica.driver.presentation.login.LoginScreen
import com.appmovilidadclinica.driver.presentation.profile.ProfileScreen
import com.appmovilidadclinica.driver.presentation.qrscan.QrScanScreen
import com.appmovilidadclinica.driver.presentation.tripdetail.TripDetailScreen

private const val ROUTE_LOGIN = "login"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_TRIP_DETAIL = "trip/{tripId}"
private const val ROUTE_QR_SCAN = "trip/{tripId}/qr"
private const val ROUTE_INCIDENT = "trip/{tripId}/incident"
private const val ROUTE_PROFILE = "profile"

/**
 * Raiz de navegacion — arranca siempre en Login (mismo patron probado en la
 * app pasajero: un gate de isLoading antes del NavHost rompia la navegacion,
 * asi que la sesion guardada se resuelve con un LaunchedEffect que redirige).
 */
@Composable
fun DriverNavHost(navController: NavHostController = rememberNavController()) {
    val sessionViewModel: SessionViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SessionViewModel(AppModule.provideAuthRepository()) }
        },
    )
    val isLoggedIn by sessionViewModel.isLoggedIn.collectAsStateWithLifecycle()

    LaunchedEffect(isLoggedIn) {
        val loggedIn = isLoggedIn ?: return@LaunchedEffect
        val onLogin = navController.currentDestination?.route == ROUTE_LOGIN
        if (loggedIn && onLogin) {
            navController.navigate(ROUTE_DASHBOARD) {
                popUpTo(ROUTE_LOGIN) { inclusive = true }
            }
        } else if (!loggedIn && !onLogin) {
            // popUpTo(0) (el entero) es ambiguo en Navigation Compose y con
            // mas de una pantalla apilada (ej. Dashboard + Perfil) corrompia
            // el arbol de composicion (IndexOutOfBoundsException en
            // Composer.endRoot al cerrar sesion desde Perfil). popUpTo con el
            // id real del grafo es la forma correcta de vaciar todo el stack.
            navController.navigate(ROUTE_LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = ROUTE_LOGIN) {
        composable(ROUTE_LOGIN) {
            LoginScreen()
        }

        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                onTripSelected = { tripId -> navController.navigate("trip/$tripId") },
                onOpenProfile = { navController.navigate(ROUTE_PROFILE) },
            )
        }

        composable(
            route = ROUTE_TRIP_DETAIL,
            arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            TripDetailScreen(
                tripId = tripId,
                onBack = { navController.popBackStack() },
                onScanQr = { navController.navigate("trip/$it/qr") },
                onReportIncident = { navController.navigate("trip/$it/incident") },
            )
        }

        composable(
            route = ROUTE_QR_SCAN,
            arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            QrScanScreen(tripId = tripId, onBack = { navController.popBackStack() })
        }

        composable(
            route = ROUTE_INCIDENT,
            arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            IncidentScreen(tripId = tripId, onBack = { navController.popBackStack() })
        }

        composable(ROUTE_PROFILE) {
            // Cerrar sesion no navega aca — el LaunchedEffect(isLoggedIn) de
            // arriba reacciona solo al cambio de sesion. Ver comentario en
            // ProfileScreen.kt: navegar en los dos lugares a la vez rompia
            // Nav Compose.
            ProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
