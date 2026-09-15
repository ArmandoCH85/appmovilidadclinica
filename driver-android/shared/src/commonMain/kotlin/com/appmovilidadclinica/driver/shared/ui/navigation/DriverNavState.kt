package com.appmovilidadclinica.driver.shared.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.appmovilidadclinica.driver.shared.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de navegacion multiplatform del conductor. Maneja el back stack
 * manualmente: cada [push] apila; [pop] desapila y vuelve al anterior.
 *
 * Estado expuesto via [current] para que el composable [DriverNavGraph]
 * lo observe. Persistencia via rememberSaveable en niveles mas altos
 * para sobrevivir recompositions y config changes.
 */
class DriverNavState(initial: Route) {
    private val _stack = MutableStateFlow<List<Route>>(listOf(initial))
    val stack: StateFlow<List<Route>> = _stack.asStateFlow()

    val current: Route get() = _stack.value.last()

    fun push(route: Route) {
        _stack.value = _stack.value + route
    }

    fun pop(): Boolean {
        val s = _stack.value
        return if (s.size > 1) {
            _stack.value = s.dropLast(1)
            true
        } else false
    }

    fun replace(route: Route) {
        _stack.value = listOf(route)
    }
}

/**
 * Saver para que el back stack sobreviva recompositions/process death.
 * Solo persiste la primera ruta (origen) y la ultima (current); para
 * sobrevivir back stack completos seria suficiente con serializar el stack
 * a Bundle. Para un back stack shallow esta heuristica es OK.
 */
private val navStateSaver: Saver<DriverNavState, String> = Saver(
    save = { state ->
        // Solo guardamos la ruta actual (la primera + ultima; en este caso
        // la primera puesto que replace la reinicia).
        state.stack.value.firstOrNull()?.toString() ?: "Login"
    },
    restore = { saved ->
        DriverNavState(parseRoute(saved) ?: Route.Login)
    },
)

private fun parseRoute(serialized: String): Route? = when {
    serialized.contains("Login") -> Route.Login
    serialized.contains("Dashboard") -> Route.Dashboard
    serialized.contains("Profile") -> Route.Profile
    else -> null
}

/**
 * Composable que crea el state y observa el stack actual.
 * Devuelve el state y una composable function para navegar.
 */
@Composable
fun rememberDriverNavState(initial: Route = Route.Login): DriverNavState {
    return remember(initial) {
        DriverNavState(initial)
    }
}
