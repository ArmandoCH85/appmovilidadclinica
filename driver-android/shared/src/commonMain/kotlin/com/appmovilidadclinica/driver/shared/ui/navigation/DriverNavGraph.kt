package com.appmovilidadclinica.driver.shared.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.appmovilidadclinica.driver.shared.domain.repository.AuthRepository
import com.appmovilidadclinica.driver.shared.ui.screens.dashboard.DashboardScreen
import com.appmovilidadclinica.driver.shared.ui.screens.incident.IncidentScreen
import com.appmovilidadclinica.driver.shared.ui.screens.login.LoginScreen
import com.appmovilidadclinica.driver.shared.ui.screens.profile.ProfileScreen
import com.appmovilidadclinica.driver.shared.ui.screens.seatmap.SeatMapScreen
import com.appmovilidadclinica.driver.shared.ui.screens.tripdetail.TripDetailScreen
import com.appmovilidadclinica.driver.shared.ui.theme.DriverAppTheme
import org.koin.compose.koinInject

/**
 * Raiz de navegacion multiplatform (state-based). Funciona en iOS sin
 * androidx.navigation.compose.
 *
 * Slot [qrScanContent]: Composable Android-only con CameraX+MLKit.
 * En iOS el caller puede pasar un stub o un AVFoundation+Vision binding.
 *
 * Si el usuario no esta logueado y la ruta actual no es Login, redirige a Login.
 * Si esta logueado y la ruta es Login, va a Dashboard.
 */
@Composable
fun DriverNavGraph(
    navState: DriverNavState = rememberDriverNavState(initial = Route.Login),
    authRepository: AuthRepository = koinInject(),
    qrScanContent: @Composable (tripId: Long, onBack: () -> Unit) -> Unit = { _, onBack -> onBack() },
) {
    DriverAppTheme {
        val sessionFlow = remember { authRepository.isLoggedIn() }
        val isLoggedIn by sessionFlow.collectAsState(initial = null)

        LaunchedEffect(isLoggedIn, navState.current) {
            val loggedIn = isLoggedIn
            if (loggedIn == null) return@LaunchedEffect
            val onLogin = navState.current is Route.Login
            if (loggedIn && onLogin) {
                navState.replace(Route.Dashboard)
            } else if (!loggedIn && !onLogin) {
                navState.replace(Route.Login)
            }
        }

        when (val current = navState.current) {
            Route.Login -> LoginScreen()
            Route.Dashboard -> DashboardScreen(
                onTripSelected = { tripId -> navState.push(Route.TripDetail(tripId)) },
                onOpenProfile = { navState.push(Route.Profile) },
            )
            is Route.TripDetail -> TripDetailScreen(
                tripId = current.tripId,
                onBack = { navState.pop() },
                onScanQr = { navState.push(Route.QrScan) },
                onReportIncident = { navState.push(Route.Incident(it)) },
                onOccupySeat = { navState.push(Route.SeatMap(it)) },
            )
            is Route.SeatMap -> SeatMapScreen(
                tripId = current.tripId,
                onBack = { navState.pop() },
                onRegistered = { navState.pop() },
            )
            is Route.Incident -> IncidentScreen(
                tripId = current.tripId,
                onBack = { navState.pop() },
            )
            Route.QrScan -> qrScanContent(
                navState.stack.value
                    .filterIsInstance<Route.TripDetail>()
                    .lastOrNull()
                    ?.tripId ?: 0L,
            ) { navState.pop() }
            Route.Profile -> ProfileScreen(onBack = { navState.pop() })
        }
    }
}
