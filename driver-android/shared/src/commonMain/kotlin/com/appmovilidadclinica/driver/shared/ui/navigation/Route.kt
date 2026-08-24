package com.appmovilidadclinica.driver.shared.ui.navigation

/**
 * Rutas de la app del conductor (multiplatform). State-based navigation:
 * cada screen vive o muere segun [Route] aca.
 *
 * Wrapper simple (no androidx.navigation.compose) para que el NavGraph sea
 * KMP-friendly. Compose Multiplatform no tiene NavHost equivalente; cuando
 * `org.jetbrains.androidx.navigation:navigation-compose` resuelva
 * `navArgument` en commonMain, se reemplaza por ese DSL.
 */
sealed class Route {
    data object Login : Route()
    data object Dashboard : Route()
    data class TripDetail(val tripId: Long) : Route()
    data class Incident(val tripId: Long) : Route()
    data object QrScan : Route() // el tripId se obtiene del trip activo via DriverNavState
    data object Profile : Route()
}
