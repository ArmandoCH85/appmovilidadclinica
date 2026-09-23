# Fix Google Play Rejection — Seat Selection Screen Unresponsive UI

**Date:** 2026-08-27
**Status:** Approved
**Author:** Senior Architect
**Scope:** `passenger-android/app` (Android Compose UI + ViewModel + mapper)

## Goal

Resolver el rechazo de Google Play por **"Funcionalidad defectuosa: Elementos de la interfaz de usuario que no responden, como botones o iconos"** en la pantalla `SeatSelectionScreen`. Después del fix, tocar un asiento disponible debe:

1. Cambiar su estado visual a "Elegido" (color primario).
2. Actualizar el `selectedSeatId` en el `ViewModel`.
3. Habilitar el botón inferior con texto dinámico `Confirmar asiento X`.
4. Al confirmar, navegar a la pantalla de detalle de reserva.
5. Si algo falla (red, validación, backend), mostrar `Snackbar` claro y no congelar la UI.

## Background

Reporte de Google Play Console (política "Elementos UI que no responden"):

> Al tocar un asiento disponible ("Libre" del 1 al 12), la interfaz no reacciona, no cambia de color a "Elegido" ni actualiza la selección. El botón de acción inferior permanece bloqueado/deshabilitado y no procesa el clic.

El usuario confirmó en device: **"los asientos se ven, pero al tocarlos no cambian de color a 'Elegido' y el botón inferior permanece deshabilitado"**.

Síntoma = `selectedSeatId` nunca se setea, lo cual tiene 3 causas posibles (ordenadas por probabilidad):

| # | Causa | Probabilidad |
|---|---|---|
| A | `LazyVerticalGrid` con `Modifier.weight(1f, fill = false)` aprieta la grilla a altura 0 → sin hit-test real | 70% |
| B | `SeatAvailability.valueOf(availability)` tira `IllegalArgumentException` cuando el backend manda un valor no esperado (ej: `"OCCUPIED"` en vez de `"OCCUPIED_IN_REQUESTED_RANGE"`) → rompe el mapper y deja la lista vacía o mal parseada | 20% |
| C | `tripsRepository.getDetail()` se cuelga y deja `loading=true` para siempre | 10% |

El usuario no tiene acceso a logcat, así que el fix debe ser **defensivo**: cubrir las 3 hipótesis en una sola pasada.

## Design

### Cambio 1 — Mapper defensivo (`TripMapper.kt`)

**Archivo:** `app/src/main/java/com/appmovilidadclinica/passenger/data/mapper/TripMapper.kt`

Reemplazar la línea `availability = SeatAvailability.valueOf(availability)` por una función helper con fallback seguro:

```kotlin
fun SeatResultDto.toDomain(): TripSeat = TripSeat(
    tripSeatId = tripSeatId,
    seatNumber = seatNumber,
    seatLabel = seatLabel,
    availability = parseSeatAvailability(availability),
)

private fun parseSeatAvailability(raw: String): SeatAvailability =
    runCatching { SeatAvailability.valueOf(raw) }
        .getOrElse { SeatAvailability.OCCUPIED_IN_REQUESTED_RANGE }
```

**Razón:** Si el backend manda un valor no esperado, el asiento aparece como "ocupado" (gris, no seleccionable) en vez de tirar `IllegalArgumentException` que rompa toda la lista. Esto cubre la hipótesis B.

### Cambio 2 — `LazyVerticalGrid` con altura garantizada (`SeatSelectionScreen.kt`)

**Archivo:** `app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt`

Reemplazar `Modifier.weight(1f, fill = false)` por `Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 480.dp)`.

Y `SeatCell` con `fillMaxWidth().heightIn(min = 56.dp)` en vez de `aspectRatio(1f)`:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(background)
        .then(...)
        .clickable(enabled = seat.isSelectable, onClick = onClick)
        .semantics { contentDescription = "Asiento ${seat.seatLabel}, $stateDescription" },
    contentAlignment = Alignment.Center,
)
```

**Razón:** `LazyVerticalGrid` no respeta `Modifier.weight()` de un `Column` padre con `fill = false`. Esto causa que la grilla tenga altura 0 (o muy chica) y los asientos no tengan hit-test real. Esto cubre la hipótesis A.

### Cambio 3 — `onClick` simplificado

Reemplazar:
```kotlin
onClick = { if (seat.isSelectable) viewModel.selectSeat(seat.tripSeatId) },
```

Por:
```kotlin
onClick = { viewModel.selectSeat(seat.tripSeatId) },
```

El doble check es redundante: `SeatCell` ya tiene `enabled = seat.isSelectable` en el `clickable`, y `viewModel.selectSeat()` ya valida internamente.

### Cambio 4 — Botón inferior con texto dinámico

```kotlin
val selectedLabel = state.seats
    .find { it.tripSeatId == state.selectedSeatId }
    ?.seatLabel

Button(
    onClick = viewModel::confirm,
    enabled = state.selectedSeatId != null && !state.confirming,
    modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 16.dp),
) {
    if (state.confirming) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        Text(
            text = if (selectedLabel != null) "Confirmar asiento $selectedLabel" 
                   else "Seleccione un asiento",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
```

**Razón:** Feedback explícito al usuario. Cuando no hay selección: "Seleccione un asiento" (gris, deshabilitado). Cuando hay selección: "Confirmar asiento 5" (color primario, habilitado).

### Cambio 5 — `Snackbar` para errores (en vez de `Text` rojo)

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
LaunchedEffect(state.userMessage) {
    state.userMessage?.let {
        snackbarHostState.showSnackbar(it)
        viewModel.consumeUserMessage()
    }
}

Scaffold(
    topBar = { ... },
    snackbarHost = { SnackbarHost(snackbarHostState) },
) { ... }
```

Quitar el `Text` rojo actual del archivo original (líneas 125-131) y reemplazarlo con `Snackbar` ligado a `userMessage`.

**Razón:** Estándar Android para feedback transitorio. Google Play valora feedback claro y consistente.

### Cambio 6 — ViewModel con logging y state de Snackbar

**Archivo:** `app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionViewModel.kt`

```kotlin
data class SeatSelectionUiState(
    val loading: Boolean = true,
    val tripDetail: TripDetail? = null,
    val origin: TripStop? = null,
    val destination: TripStop? = null,
    val seats: List<TripSeat> = emptyList(),
    val selectedSeatId: Long? = null,
    val confirming: Boolean = false,
    val errorMessage: String? = null,
    val userMessage: String? = null,  // NUEVO
    val confirmedReservationId: Long? = null,
)

fun selectSeat(tripSeatId: Long) {
    Log.d(TAG, "selectSeat($tripSeatId) current=${_uiState.value.selectedSeatId}")
    _uiState.update { it.copy(selectedSeatId = tripSeatId, errorMessage = null) }
}

fun consumeUserMessage() {
    _uiState.update { it.copy(userMessage = null) }
}

private companion object {
    const val TAG = "SeatSelectionVM"
}
```

Y en `loadTripAndSeats()`, agregar `Log.e(TAG, "getDetail failed", error)` y `Log.e(TAG, "listSeats failed", error)` para diagnóstico futuro.

**Razón:** Logging filtrable por tag (`adb logcat -s SeatSelectionVM`) para que el usuario pueda diagnosticar futuros problemas sin recompilar.

## Files Changed

| Archivo | Líneas tocadas | Tipo |
|---|---|---|
| `app/src/main/java/com/appmovilidadclinica/passenger/data/mapper/TripMapper.kt` | 82-87 + nueva función `parseSeatAvailability` | Mapper |
| `app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt` | 108-121 (grilla), 153-195 (SeatCell), 125-147 (errores + botón) | UI |
| `app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionViewModel.kt` | 24-34 (UiState), 90-92 (selectSeat + logging), 60-88 (loadTripAndSeats logging) | ViewModel |

## Testing

Sin acceso a device, se valida con **checklist manual de aceptación** (definido abajo) que el usuario ejecutará cuando vuelva a tener device.

### Checklist de aceptación

- [ ] Abrir pantalla → grilla muestra asientos sin comprimir
- [ ] Tocar asiento "Libre" → cambia a "Elegido" (color primario)
- [ ] Tocar otro asiento "Libre" → el anterior vuelve a "Libre" (selección única)
- [ ] Tocar asiento ocupado → no reacciona (sin haptic ni nada raro)
- [ ] Botón dice "Seleccione un asiento" cuando no hay selección (gris, deshabilitado)
- [ ] Botón dice "Confirmar asiento X" cuando hay selección (habilitado, color primario)
- [ ] Tocar botón → spinner → navega a detalle de reserva
- [ ] Si backend falla → Snackbar con mensaje claro, UI no congelada
- [ ] Girar pantalla → selección se mantiene (gracias a `StateFlow`)

### Validación con logcat (cuando tenga device)

```bash
adb logcat -s SeatSelectionVM
```

Esperado al tocar un asiento disponible:
```
D/SeatSelectionVM: selectSeat(12345) current=null
```

Si no aparece ese log → el `clickable` no se está disparando (hipótesis A confirmada).

## Out of Scope

- Reescritura completa de la pantalla con arquitectura más limpia (Clean Architecture, Hilt modules, etc.)
- Tests unitarios / instrumentados (no hay infraestructura de tests configurada en el proyecto actualmente — verificar antes de agregar)
- Cambios al backend o al formato del DTO
- Cambios a otras pantallas (`TripSearchScreen`, `MyReservationDetailScreen`, etc.)
- Internacionalización (los strings siguen en español hardcodeado, igual que antes)

## Risks

| Riesgo | Mitigación |
|---|---|
| El cambio en el mapper introduce un bug nuevo | Fallback es `OCCUPIED_IN_REQUESTED_RANGE`, el más conservador (asiento no seleccionable). Si el backend manda un valor válido, comportamiento idéntico al actual. |
| El `heightIn(min = 280.dp, max = 480.dp)` se ve mal en pantallas pequeñas | Probado mentalmente con 4 columnas × 56dp = 224dp + padding = ~280dp. En pantallas <360dp podría haber scroll interno del LazyVerticalGrid. Aceptable. |
| El `Snackbar` no aparece porque `consumeUserMessage` se llama antes de mostrarse | `showSnackbar` suspende hasta que se dismiss; la asignación de `null` en el state viene después. Validar con device. |
| Google Play rechaza de nuevo por otra razón no identificada | El cambio documenta explícitamente el feedback al usuario, lo cual cubre la política de "UI que no responde". |

## Done When

- [ ] 3 archivos modificados según spec
- [ ] `gradle build` pasa sin errores ni warnings nuevos
- [ ] Checklist de aceptación ejecutado y todos los items pasan
- [ ] App subida a Play Console con notas de release mencionando el fix
