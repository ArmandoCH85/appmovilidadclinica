# Seat Selection Unresponsive UI Fix ? Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Google Play rejection "Funcionalidad defectuosa: Elementos de la interfaz de usuario que no responden" en `SeatSelectionScreen`, haciendo que los toques en asientos disponibles actualicen el estado visual, habiliten el bot?n inferior con texto din?mico, y muestren feedback claro ante errores.

**Architecture:** Cambio m?nimo, defensivo y testeable. Tres archivos: (1) mapper con fallback seguro para `SeatAvailability`, (2) `SeatSelectionScreen.kt` con `LazyVerticalGrid` de altura fija y `SeatCell` con tama?o garantizado, (3) `SeatSelectionViewModel.kt` con logging filtrable por tag y state `userMessage` para Snackbar. Sin cambios al backend, DTO, navegaci?n, ni otras pantallas.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Material 3, Hilt, Kotlin Coroutines, `kotlinx.coroutines.flow.MutableStateFlow` + `update {}`.

## Global Constraints

- **Kotlin 2.x con Compose Compiler plugin** (proyecto ya configurado en `gradle/libs.versions.toml`)
- **No agregar dependencias nuevas** ? usar solo `androidx.compose.material3.Snackbar`, `androidx.compose.material3.SnackbarHost`, `androidx.compose.material3.SnackbarHostState` (ya importados v?a `material3`)
- **Strings en espa?ol hardcodeado** ? match el patr?n existente en el archivo
- **No tests automatizados nuevos** ? el proyecto no tiene infraestructura de tests configurada; validar con checklist manual
- **Logging con `Log.d` / `Log.e`** usando `android.util.Log` (ya importado transitivamente)
- **Convenciones del proyecto:** 4 spaces indent, `final` no usado (Kotlin idiom?tico del proyecto), import expl?cito
- **No build despu?s de cambios** (constraint del environment)

## File Structure

| Archivo | Responsabilidad | Cambio |
|---|---|---|
| `app/src/main/java/com/appmovilidadclinica/passenger/data/mapper/TripMapper.kt` | Mapear DTO de backend a modelo de dominio | Agregar funci?n `parseSeatAvailability` + reemplazar `valueOf` directo |
| `app/src/main/java/com/appappmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt` | UI Compose de selecci?n de asientos | Fix `LazyVerticalGrid` height + `SeatCell` size + bot?n din?mico + Snackbar |
| `app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionViewModel.kt` | State management de la pantalla | Agregar `userMessage`, `consumeUserMessage()`, logging |

---

## Task 1: Mapper defensivo (`TripMapper.kt`)

**Files:**
- Modify: `app/src/main/java/com/appmovilidadclinica/passenger/data/mapper/TripMapper.kt:80-87`

**Interfaces:**
- Consumes: `SeatResultDto.availability: String` (DTO del backend)
- Produces: `TripSeat` con `availability: SeatAvailability` nunca rompe la lista si backend manda valor desconocido

- [ ] **Step 1: Localizar la funci?n `SeatResultDto.toDomain()` existente**

Confirmar que est? en `TripMapper.kt` l?neas 82-87 con este contenido:

```kotlin
fun SeatResultDto.toDomain(): TripSeat = TripSeat(
    tripSeatId = tripSeatId,
    seatNumber = seatNumber,
    seatLabel = seatLabel,
    availability = SeatAvailability.valueOf(availability),
)
```

- [ ] **Step 2: Reemplazar la l?nea 86 por llamada a funci?n helper**

Cambiar:
```kotlin
    availability = SeatAvailability.valueOf(availability),
```

Por:
```kotlin
    availability = parseSeatAvailability(availability),
```

- [ ] **Step 3: Agregar funci?n helper `parseSeatAvailability` al final del archivo**

Agregar al final de `TripMapper.kt`:

```kotlin
private fun parseSeatAvailability(raw: String): SeatAvailability =
    runCatching { SeatAvailability.valueOf(raw) }
        .getOrElse { SeatAvailability.OCCUPIED_IN_REQUESTED_RANGE }
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/appmovilidadclinica/passenger/data/mapper/TripMapper.kt
git commit -m "fix(seat-selection): defensive SeatAvailability parsing

Prevents IllegalArgumentException when backend returns an unexpected
availability string (e.g. 'OCCUPIED' instead of 'OCCUPIED_IN_REQUESTED_RANGE').
Falls back to OCCUPIED_IN_REQUESTED_RANGE so the seat is treated as
non-selectable but the rest of the list still renders.

Ref: docs/superpowers/specs/2026-08-27-seat-selection-unresponsive-fix-design.md"
```

---

## Task 2: ViewModel ? state `userMessage` + logging

**Files:**
- Modify: `app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionViewModel.kt`

**Interfaces:**
- Consumes: nada nuevo (es extensi?n del state existente)
- Produces: 
  - `SeatSelectionUiState.userMessage: String?` (campo nuevo)
  - `SeatSelectionViewModel.consumeUserMessage(): Unit` (funci?n nueva)
  - `Log.d(TAG, ...)` y `Log.e(TAG, ...)` con `TAG = "SeatSelectionVM"`

- [ ] **Step 1: Agregar import de `android.util.Log`**

En `SeatSelectionViewModel.kt`, despu?s del ?ltimo import existente (l?nea 22, `javax.inject.Inject`), agregar:

```kotlin
import android.util.Log
```

- [ ] **Step 2: Agregar campo `userMessage` al data class `SeatSelectionUiState`**

Modificar el data class (l?neas 24-34). Cambiar:

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
    val confirmedReservationId: Long? = null,
)
```

Por:

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
    val userMessage: String? = null,
    val confirmedReservationId: Long? = null,
)
```

- [ ] **Step 3: Agregar logging a `selectSeat()`**

Modificar `selectSeat` (l?nea 90-92). Cambiar:

```kotlin
    fun selectSeat(tripSeatId: Long) {
        _uiState.update { it.copy(selectedSeatId = tripSeatId, errorMessage = null) }
    }
```

Por:

```kotlin
    fun selectSeat(tripSeatId: Long) {
        Log.d(TAG, "selectSeat($tripSeatId) current=${_uiState.value.selectedSeatId}")
        _uiState.update { it.copy(selectedSeatId = tripSeatId, errorMessage = null) }
    }
```

- [ ] **Step 4: Agregar funci?n `consumeUserMessage()` y companion `TAG`**

Al final de la clase `SeatSelectionViewModel` (antes del ?ltimo `}`), agregar:

```kotlin
    fun consumeUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private companion object {
        const val TAG = "SeatSelectionVM"
    }
```

- [ ] **Step 5: Agregar logging a `loadTripAndSeats()` en las ramas Failure**

Modificar las ramas Failure de `loadTripAndSeats()` (l?neas 62-86). Cambiar:

```kotlin
                is AppResult.Failure -> {
                    _uiState.update { it.copy(loading = false, errorMessage = "No se pudo cargar el viaje.") }
                    return@launch
                }
```

Por:

```kotlin
                is AppResult.Failure -> {
                    Log.e(TAG, "getDetail failed", result.error)
                    _uiState.update { it.copy(loading = false, errorMessage = "No se pudo cargar el viaje.") }
                    return@launch
                }
```

Y cambiar:

```kotlin
                        is AppResult.Failure -> _uiState.update {
                            it.copy(loading = false, errorMessage = "No se pudieron cargar los asientos.")
                        }
```

Por:

```kotlin
                        is AppResult.Failure -> {
                            Log.e(TAG, "listSeats failed", seatsResult.error)
                            _uiState.update {
                                it.copy(loading = false, errorMessage = "No se pudieron cargar los asientos.")
                            }
                        }
```

Y cambiar la validaci?n de paradas (l?nea 71-76):

```kotlin
                    if (origin == null || destination == null) {
                        _uiState.update {
                            it.copy(loading = false, errorMessage = "Las paradas elegidas no pertenecen a este viaje.")
                        }
                        return@launch
                    }
```

Por:

```kotlin
                    if (origin == null || destination == null) {
                        Log.e(TAG, "Origin or destination not in trip stops: originStopId=${route.originStopId} destinationStopId=${route.destinationStopId}")
                        _uiState.update {
                            it.copy(loading = false, errorMessage = "Las paradas elegidas no pertenecen a este viaje.")
                        }
                        return@launch
                    }
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionViewModel.kt
git commit -m "feat(seat-selection): add userMessage state and diagnostic logging

Adds userMessage field for Snackbar feedback, consumeUserMessage() helper
for one-shot message consumption, and Log.d/Log.e instrumentation tagged
'SeatSelectionVM' for adb logcat -s SeatSelectionVM diagnostics.

Ref: docs/superpowers/specs/2026-08-27-seat-selection-unresponsive-fix-design.md"
```

---

## Task 3: UI ? `LazyVerticalGrid` altura fija + `SeatCell` tama?o garantizado

**Files:**
- Modify: `app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt`

**Interfaces:**
- Consumes: `viewModel.uiState: StateFlow<SeatSelectionUiState>`, `viewModel.selectSeat()`, `viewModel.confirm()`, `viewModel.consumeUserMessage()`
- Produces: UI que renderiza la grilla con altura fija (no depende de `weight()`), celdas con tama?o garantizado tocable

- [ ] **Step 1: Modificar `LazyVerticalGrid` modifier (l?nea 112)**

Cambiar:
```kotlin
        modifier = Modifier.weight(1f, fill = false),
```

Por:
```kotlin
        modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 480.dp),
```

- [ ] **Step 2: Simplificar `onClick` de items (l?nea 118)**

Cambiar:
```kotlin
                        onClick = { if (seat.isSelectable) viewModel.selectSeat(seat.tripSeatId) },
```

Por:
```kotlin
                        onClick = { viewModel.selectSeat(seat.tripSeatId) },
```

- [ ] **Step 3: Modificar `SeatCell` modifier (l?neas 166-179)**

Cambiar el bloque del `Box`:

```kotlin
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = seat.isSelectable, onClick = onClick)
            .semantics { contentDescription = "Asiento ${seat.seatLabel}, $stateDescription" },
        contentAlignment = Alignment.Center,
    ) {
```

Por:

```kotlin
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = seat.isSelectable, onClick = onClick)
            .semantics { contentDescription = "Asiento ${seat.seatLabel}, $stateDescription" },
        contentAlignment = Alignment.Center,
    ) {
```

- [ ] **Step 4: Remover import de `aspectRatio`**

Quitar la l?nea `import androidx.compose.foundation.layout.aspectRatio` (l?nea 11). Ya no se usa.

- [ ] **Step 5: Commit parcial (solo grilla + SeatCell)**

```bash
git add app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
git commit -m "fix(seat-selection): guarantee LazyVerticalGrid and SeatCell touch targets

Replaces Modifier.weight(1f, fill = false) on LazyVerticalGrid (which
collapses to height 0 in Compose Column parents) with fixed
heightIn(280dp..480dp). Replaces SeatCell's aspectRatio(1f) with
fillMaxWidth() + heightIn(min = 56.dp) so each seat has a guaranteed
touchable area regardless of available space.

Also removes the redundant if (seat.isSelectable) guard in onClick -
clickable's enabled param already gates this.

Ref: docs/superpowers/specs/2026-08-27-seat-selection-unresponsive-fix-design.md"
```

---

## Task 4: UI ? bot?n din?mico + Snackbar + remover `Text` rojo

**Files:**
- Modify: `app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt`

**Interfaces:**
- Consumes: `state.selectedSeatId`, `state.seats`, `state.userMessage`
- Produces: bot?n con texto din?mico, `SnackbarHostState` ligado a `state.userMessage`

- [ ] **Step 1: Agregar imports para Snackbar**

Despu?s del ?ltimo import `material3` (l?nea 33), agregar:

```kotlin
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
```

- [ ] **Step 2: Agregar `SnackbarHostState` y `LaunchedEffect` para mensajes**

Despu?s de la l?nea `val state by viewModel.uiState.collectAsStateWithLifecycle()` (l?nea 57), agregar:

```kotlin
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeUserMessage()
        }
    }
```

- [ ] **Step 3: Agregar `snackbarHost` al `Scaffold`**

Modificar el `Scaffold` (l?neas 63-73). Cambiar:

```kotlin
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Selecci?n de asiento") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
```

Por:

```kotlin
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Selecci?n de asiento") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
```

- [ ] **Step 4: Reemplazar `Text` rojo con nada (Snackbar lo maneja)**

Eliminar el bloque completo (l?neas 125-131):

```kotlin
            if (state.errorMessage != null) {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

```

(El `errorMessage` se mantiene en el state para diagn?stico, pero ya no se renderiza en UI ? los errores ahora van por Snackbar via `userMessage`.)

- [ ] **Step 5: Texto din?mico del bot?n**

Cambiar el bloque del bot?n (l?neas 133-147):

```kotlin
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
                    Text("Confirmar reserva", style = MaterialTheme.typography.labelLarge)
                }
            }
```

Por:

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

- [ ] **Step 6: Commit final del UI**

```bash
git add app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt
git commit -m "feat(seat-selection): dynamic confirm button + Snackbar error feedback

Replaces static 'Confirmar reserva' with dynamic text showing the
selected seat label (or 'Seleccione un asiento' when none selected).
Adds SnackbarHost for transient error feedback via state.userMessage,
consumed via viewModel.consumeUserMessage() after display.

Removes inline red Text error in favor of the Snackbar pattern (Material 3
standard, better UX for transient errors).

Ref: docs/superpowers/specs/2026-08-27-seat-selection-unresponsive-fix-design.md"
```

---

## Task 5: Wire `userMessage` en el ViewModel para errores

**Files:**
- Modify: `app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionViewModel.kt`

**Interfaces:**
- Consumes: `AppError` de `loadTripAndSeats()` y `confirm()`
- Produces: errores se propagan a `state.userMessage` para que la UI los muestre en Snackbar

- [ ] **Step 1: Modificar rama Failure de `getDetail` para emitir userMessage**

Cambiar:
```kotlin
                is AppResult.Failure -> {
                    Log.e(TAG, "getDetail failed", result.error)
                    _uiState.update { it.copy(loading = false, errorMessage = "No se pudo cargar el viaje.") }
                    return@launch
                }
```

Por:
```kotlin
                is AppResult.Failure -> {
                    Log.e(TAG, "getDetail failed", result.error)
                    _uiState.update { 
                        it.copy(
                            loading = false, 
                            errorMessage = "No se pudo cargar el viaje.",
                            userMessage = "No se pudo cargar el viaje.",
                        )
                    }
                    return@launch
                }
```

- [ ] **Step 2: Modificar rama Failure de `listSeats` para emitir userMessage**

Cambiar:
```kotlin
                        is AppResult.Failure -> {
                            Log.e(TAG, "listSeats failed", seatsResult.error)
                            _uiState.update {
                                it.copy(loading = false, errorMessage = "No se pudieron cargar los asientos.")
                            }
                        }
```

Por:
```kotlin
                        is AppResult.Failure -> {
                            Log.e(TAG, "listSeats failed", seatsResult.error)
                            _uiState.update {
                                it.copy(
                                    loading = false, 
                                    errorMessage = "No se pudieron cargar los asientos.",
                                    userMessage = "No se pudieron cargar los asientos.",
                                )
                            }
                        }
```

- [ ] **Step 3: Modificar rama Failure de validaci?n de paradas para emitir userMessage**

Cambiar:
```kotlin
                    if (origin == null || destination == null) {
                        Log.e(TAG, "Origin or destination not in trip stops: originStopId=${route.originStopId} destinationStopId=${route.destinationStopId}")
                        _uiState.update {
                            it.copy(loading = false, errorMessage = "Las paradas elegidas no pertenecen a este viaje.")
                        }
                        return@launch
                    }
```

Por:
```kotlin
                    if (origin == null || destination == null) {
                        Log.e(TAG, "Origin or destination not in trip stops: originStopId=${route.originStopId} destinationStopId=${route.destinationStopId}")
                        _uiState.update {
                            it.copy(
                                loading = false, 
                                errorMessage = "Las paradas elegidas no pertenecen a este viaje.",
                                userMessage = "Las paradas elegidas no pertenecen a este viaje.",
                            )
                        }
                        return@launch
                    }
```

- [ ] **Step 4: Modificar rama Failure de `confirm` para emitir userMessage**

Cambiar:
```kotlin
                is AppResult.Failure -> _uiState.update {
                    it.copy(confirming = false, errorMessage = messageFor(result.error))
                }
```

Por:
```kotlin
                is AppResult.Failure -> {
                    val msg = messageFor(result.error)
                    _uiState.update {
                        it.copy(confirming = false, errorMessage = msg, userMessage = msg)
                    }
                }
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionViewModel.kt
git commit -m "feat(seat-selection): route error messages through Snackbar

All AppResult.Failure paths now set both errorMessage (for logging) and
userMessage (for Snackbar display). The UI consumes userMessage one-shot
via consumeUserMessage() to avoid re-showing on recomposition.

Ref: docs/superpowers/specs/2026-08-27-seat-selection-unresponsive-fix-design.md"
```

---

## Task 6: Validaci?n manual con checklist

**Files:** ninguno (es ejecuci?n)

**Interfaces:**
- Consumes: app instalada en device con los cambios
- Produces: confirmaci?n de que los 9 items del checklist pasan

- [ ] **Step 1: Instalar la app en device**

```bash
./gradlew installDebug
```

(o equivalente ? el environment del proyecto tiene la regla "No build despu?s de cambios", as? que el usuario lo hace manualmente cuando quiera)

- [ ] **Step 2: Abrrir pantalla de selecci?n de asientos**

Navegar desde `TripSearchScreen` ? seleccionar un viaje ? elegir origen y destino ? tap "Ver asientos"

- [ ] **Step 3: Ejecutar checklist completo**

Validar cada item del checklist en `docs/superpowers/specs/2026-08-27-seat-selection-unresponsive-fix-design.md` secci?n "Testing ? Checklist de aceptaci?n".

- [ ] **Step 4: Capturar logcat durante el tap**

En una terminal paralela:
```bash
adb logcat -s SeatSelectionVM
```

Tap un asiento disponible. Esperado:
```
D/SeatSelectionVM: selectSeat(<tripSeatId>) current=null
```

- [ ] **Step 5: Reportar resultado**

Si alg?n item falla ? abrir issue o volver a brainstorm.
Si todos pasan ? proceder al release.

- [ ] **Step 6: Commit del release (cuando se suba a Play Console)**

```bash
git tag v<version>-seat-fix
```

---

## Self-Review

**1. Spec coverage:**

| Secci?n del spec | Task |
|---|---|
| Cambio 1 ? Mapper defensivo | Task 1 |
| Cambio 2 ? LazyVerticalGrid altura | Task 3 (Step 1) |
| Cambio 3 ? SeatCell tama?o | Task 3 (Step 3) |
| Cambio 4 ? onClick simplificado | Task 3 (Step 2) |
| Cambio 5 ? Bot?n din?mico | Task 4 (Step 5) |
| Cambio 6 ? Snackbar + state userMessage | Task 4 (Steps 1-4) + Task 2 (Steps 1-4) + Task 5 |
| Logging | Task 2 (Step 5) + Task 5 (Steps 1-4) |
| Files Changed table | Tasks 1, 2, 3, 4 (UI dividido en 2 commits) |
| Testing checklist | Task 6 |
| Out of Scope | No tocado (correcto) |
| Risks | Mitigaciones incorporadas en Tasks 3, 4 |
| Done When | Tasks 1-5 + Task 6 |

**2. Placeholder scan:** Busqu? "TBD", "TODO", "implement later", "appropriate", "edge cases", "similar to". No hay placeholders. ?

**3. Type consistency:**
- `userMessage: String?` definido en Task 2 Step 2, usado en Task 4 Step 2 y Task 5 ?
- `consumeUserMessage()` definido en Task 2 Step 4, usado en Task 4 Step 2 ?
- `TAG = "SeatSelectionVM"` definido en Task 2 Step 4, usado en Task 2 Step 5 y Task 5 ?
- `state.seats`, `state.selectedSeatId`, `state.userMessage` accesos consistentes ?

**4. Forward references:** Task 5 referencia a `userMessage` que se introduce en Task 2. Task 4 referencia a `consumeUserMessage()` que se introduce en Task 2. **Orden de tasks es correcto:** Task 1 ? Task 2 ? Task 3 ? Task 4 ? Task 5.

**5. Gaps encontrados y corregidos:**
- Inicialmente el plan no ten?a Task 5 separado para wire de userMessage en errores ? lo agregu? porque sin ?l el Snackbar nunca se dispara. ?
- Task 4 Step 5 usa `selectedLabel` antes definido en el bloque ? confirmado el orden. ?

---

## Done When

- [x] 6 tasks definidos con steps concretos y c?digo exacto
- [x] Cada task termina con un commit incremental
- [x] Self-review ejecutado y sin gaps
- [x] Constraint del environment respetada ("No build despu?s de cambios" ? Task 6 Step 1 lo deja al usuario)
