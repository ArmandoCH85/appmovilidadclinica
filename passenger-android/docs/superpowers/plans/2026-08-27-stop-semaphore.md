# Stop Semaphore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El conductor marca llegada y salida en cada paradero desde `TripDetailScreen`. El pasajero ve un semáforo de 3 colores (🔴/🟡/🟢) que se actualiza cada 20 segundos mientras el viaje está activo.

**Architecture:** 3 cambios aislados en 3 módulos distintos del repo:
1. Backend Go: nuevo SP `sp_mark_trip_stop_departure` + handler `MarkDepartureStop` + service + repo (sigue el patrón exacto de `MarkArrivalStop`)
2. Driver Kotlin Multiplatform: nueva llamada API `markDeparture` + botón en `StopRow`
3. Pasajero Android Compose: polling cada 20s + semáforo visual con 3 colores

**Tech Stack:** Go 1.22+ (backend), Kotlin Multiplatform (driver), Android Compose (pasajero), Hilt, Coroutines, kotlinx-serialization, Ktor

## Global Constraints

- **Versiones:** Go 1.22+, Kotlin 2.0.21, Compose BOM 2024.11, AGP 8.7.2
- **Naming:** código en inglés (variables, funciones, tablas), mensajes UI al usuario final en español
- **Conventional commits:** `feat:`, `fix:`, `chore:`, etc.
- **NO AI attribution** en commits
- **NO build después de cambios** (constraint del environment — usuario valida con `gradle` localmente)
- **NO agregar dependencias nuevas** — usar solo libs ya declaradas
- **Patrón estricto:** el código nuevo DEBE seguir exactamente el patrón del código existente (`MarkArrivalStop` para backend, `markArrival` para driver, `loadStopsIfNeeded` para pasajero)
- **Repos independientes:** los 3 módulos están en directorios separados (`backend/`, `driver-android/`, `passenger-android/`) y cada uno tiene su propio git tree

---

## File Structure

| Módulo | Archivos a tocar | Responsabilidad |
|---|---|---|
| `backend/internal/modules/driver/` | `repository.go`, `service.go`, `service_test.go`, `handler.go` | Endpoint `/departure` mirror de `/arrival` |
| `backend/migrations/` | `0004_trip_stop_departure.up.sql`, `0004_trip_stop_departure.down.sql` | SP `sp_mark_trip_stop_departure` |
| `driver-android/shared/.../data/remote/` | `DriverApi.kt` | Función `markDeparture` en Ktor |
| `driver-android/shared/.../domain/repository/` | `DriverRepository.kt` | Interfaz `markDeparture` |
| `driver-android/shared/.../data/repository/` | `DriverRepositoryImpl.kt` | Implementación con safeApiCall |
| `driver-android/shared/.../ui/screens/tripdetail/` | `TripDetailViewModel.kt`, `TripDetailScreen.kt` | Botón "Marcar salida" en `StopRow` |
| `passenger-android/app/.../myreservation/` | `MyReservationDetailViewModel.kt`, `MyReservationDetailScreen.kt` | Polling cada 20s + semáforo visual |

---

## Task 1: Backend — migración SQL con SP `sp_mark_trip_stop_departure`

**Files:**
- Create: `backend/migrations/0004_trip_stop_departure.up.sql`
- Create: `backend/migrations/0004_trip_stop_departure.down.sql`

**Interfaces:**
- Consumes: `trip_stop_times` table con columnas `id`, `status`, `actual_arrival_at`, `actual_departure_at`, `arrival_marked_by_user_id`
- Produces: SP `sp_mark_trip_stop_departure(p_trip_stop_time_id BIGINT UNSIGNED, p_driver_id BIGINT UNSIGNED)`

- [ ] **Step 1: Crear el archivo UP**

Crear `backend/migrations/0004_trip_stop_departure.up.sql` con:

```sql
-- Marca la salida del bus de un paradero. Misma validacion que
-- sp_mark_trip_stop_arrival: solo el conductor asignado puede hacerlo,
-- y el viaje debe estar en curso. El estado debe ser ARRIVED (no se
-- puede salir sin haber llegado primero).
DROP PROCEDURE IF EXISTS sp_mark_trip_stop_departure;

CREATE PROCEDURE sp_mark_trip_stop_departure(
    IN p_trip_stop_time_id BIGINT UNSIGNED,
    IN p_driver_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_assigned_driver_id BIGINT UNSIGNED;
    DECLARE v_trip_status VARCHAR(30);
    DECLARE v_stop_status VARCHAR(20);

    SELECT trip.driver_id, trip.status, stop_time.status
      INTO v_assigned_driver_id, v_trip_status, v_stop_status
      FROM trip_stop_times stop_time
      JOIN trip_instances trip ON trip.id = stop_time.trip_id
     WHERE stop_time.id = p_trip_stop_time_id;

    IF v_assigned_driver_id IS NULL OR v_assigned_driver_id <> p_driver_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Sólo el conductor asignado puede marcar la salida';
    END IF;

    IF v_trip_status IN ('COMPLETED', 'CANCELLED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El viaje no admite nuevas marcas de salida';
    END IF;

    IF v_stop_status = 'PENDING' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Primero debe registrarse la llegada al paradero';
    END IF;

    IF v_stop_status = 'DEPARTED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El paradero ya fue marcado como salido';
    END IF;

    UPDATE trip_stop_times
       SET actual_departure_at = CURRENT_TIMESTAMP,
           status = 'DEPARTED'
     WHERE id = p_trip_stop_time_id;
END$$

-- Aplicar el SP al separador de statements si la migración los usa
-- (verificar el delimitador en 0001_schema.up.sql — si usa $$, mantener)
```

Verificar el delimitador del archivo (línea 1 de `0001_schema.up.sql` debería tener `DELIMITER $$` o similar). Si no lo tiene, omitir el `$$` al final del `END`.

- [ ] **Step 2: Crear el archivo DOWN**

Crear `backend/migrations/0004_trip_stop_departure.down.sql`:

```sql
DROP PROCEDURE IF EXISTS sp_mark_trip_stop_departure;
```

- [ ] **Step 3: Verificar que las migraciones corren localmente**

Run: `cd backend && go run ./cmd/migrate up` (o el comando que use el proyecto para migrar — verificar en README o `cmd/migrate/main.go`)
Expected: migración 0004 aplicada, SP `sp_mark_trip_stop_departure` existe en MySQL.

- [ ] **Step 4: Commit**

```bash
cd backend
git add migrations/0004_trip_stop_departure.up.sql migrations/0004_trip_stop_departure.down.sql
git commit -m "feat(driver): sp_mark_trip_stop_departure stored procedure

Mirror of sp_mark_trip_stop_arrival but marks DEPARTED status with
actual_departure_at timestamp. Validates driver ownership, trip not
COMPLETED/CANCELLED, and that arrival was previously marked (cannot
skip arrival). 409 if already DEPARTED (idempotency)."
```

---

## Task 2: Backend — `MarkDeparture` en repository

**Files:**
- Modify: `backend/internal/modules/driver/repository.go:114` (agregar a la interfaz) y `:380` (agregar implementación)
- Modify: `backend/internal/modules/driver/service_test.go:31` (agregar `markDepartureErr` al mock) y agregar mock method

**Interfaces:**
- Consumes: `sp_mark_trip_stop_departure` SP (Task 1)
- Produces: `MarkDeparture(ctx, tripStopTimeID, driverID int64) error` en la interfaz `DriverRepository`

- [ ] **Step 1: Agregar `MarkDeparture` a la interfaz del repository**

En `backend/internal/modules/driver/repository.go`, después de la línea 115 (después de `MarkArrival`), agregar:

```go
	// MarkDeparture llama a sp_mark_trip_stop_departure.
	MarkDeparture(ctx context.Context, tripStopTimeID, driverID int64) error
```

- [ ] **Step 2: Implementar `MarkDeparture` en el repository**

En el mismo archivo, después de la línea 389 (después del cierre de `MarkArrival`), agregar:

```go
func (r *driverRepository) MarkDeparture(ctx context.Context, tripStopTimeID, driverID int64) error {
	_, err := r.db.ExecContext(ctx, "CALL sp_mark_trip_stop_departure(?, ?)", tripStopTimeID, driverID)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("llamando sp_mark_trip_stop_departure: %w", err)
	}
	return nil
}
```

- [ ] **Step 3: Agregar `markDepartureErr` al mock en service_test.go**

En `backend/internal/modules/driver/service_test.go`, agregar a la struct `mockDriverRepo` (después de línea 31):

```go
	markDepartureErr  error
```

Y agregar el método mock (después del método `MarkArrival` en línea 71):

```go
func (m *mockDriverRepo) MarkDeparture(_ context.Context, _, _ int64) error {
	return m.markDepartureErr
}
```

- [ ] **Step 4: Verificar que compila**

Run: `cd backend && go build ./...`
Expected: build OK, sin errores.

- [ ] **Step 5: Commit**

```bash
cd backend
git add internal/modules/driver/repository.go internal/modules/driver/service_test.go
git commit -m "feat(driver): MarkDeparture repo method mirrors MarkArrival

Adds interface method, implementation calling sp_mark_trip_stop_departure,
and mock for tests. No business logic change — driver ownership + state
validation live in the SP (see 0004 migration)."
```

---

## Task 3: Backend — `MarkDeparture` en service

**Files:**
- Modify: `backend/internal/modules/driver/service.go:140` (agregar después de `MarkArrival`)

**Interfaces:**
- Consumes: `DriverRepository.MarkDeparture` (Task 2)
- Produces: `driverService.MarkDeparture(ctx, tripStopTimeID int64) error` — mirror exacto de `MarkArrival`

- [ ] **Step 1: Agregar `MarkDeparture` al service**

En `backend/internal/modules/driver/service.go`, después de la línea 153 (cierre de `MarkArrival`), agregar:

```go
// MarkDeparture marca la salida del conductor de una parada. Misma
// validacion que MarkArrival: se resuelve el trip_id desde el
// trip_stop_time_id para chequear asignacion del conductor antes de
// delegar al SP. El SP hace la validacion final de estado (no se puede
// salir sin haber llegado, no se puede des-departir).
func (s *driverService) MarkDeparture(ctx context.Context, tripStopTimeID int64) error {
	driverID, err := requireDriver(ctx)
	if err != nil {
		return err
	}
	tripID, err := s.repo.GetTripStopTimeTripID(ctx, tripStopTimeID)
	if err != nil {
		return err
	}
	if err := s.ensureAssigned(ctx, driverID, tripID); err != nil {
		return err
	}
	return s.repo.MarkDeparture(ctx, tripStopTimeID, driverID)
}
```

- [ ] **Step 2: Verificar que compila**

Run: `cd backend && go build ./...`
Expected: build OK.

- [ ] **Step 3: Commit**

```bash
cd backend
git add internal/modules/driver/service.go
git commit -m "feat(driver): MarkDeparture service method mirrors MarkArrival

Validates driver ownership via trip_id resolution (403 if not assigned),
then delegates to sp_mark_trip_stop_departure which handles state
transitions (409 if already DEPARTED, 422 if still PENDING)."
```

---

## Task 4: Backend — handler `MarkDepartureStop` + ruta

**Files:**
- Modify: `backend/internal/modules/driver/handler.go:121` (agregar handler) y `:221` (registrar ruta)

**Interfaces:**
- Consumes: `driverService.MarkDeparture` (Task 3)
- Produces: `POST /api/driver/trip-stops/{id}/departure` retorna 204 No Content

- [ ] **Step 1: Agregar handler**

En `backend/internal/modules/driver/handler.go`, después de la línea 133 (cierre de `MarkArrivalStop`), agregar:

```go
// MarkDepartureStop maneja POST /driver/trip-stops/{id}/departure — marca la
// salida del conductor de la parada indicada por trip_stop_time_id.
func (h *DriverHandler) MarkDepartureStop(w http.ResponseWriter, r *http.Request) {
	tripStopTimeID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	if err := h.svc.MarkDeparture(r.Context(), tripStopTimeID); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
```

- [ ] **Step 2: Registrar la ruta**

En el mismo archivo, en `RegisterRoutes` (línea 214), después de la línea 221 (registro de `/arrival`), agregar:

```go
		r.Post("/trip-stops/{id}/departure", h.MarkDepartureStop)
```

- [ ] **Step 3: Verificar que compila**

Run: `cd backend && go build ./...`
Expected: build OK.

- [ ] **Step 4: Commit**

```bash
cd backend
git add internal/modules/driver/handler.go
git commit -m "feat(driver): POST /driver/trip-stops/{id}/departure endpoint

HTTP handler that delegates to service.MarkDeparture. Returns 204 No
Content on success, 403/404/409/422 via existing apperror.WriteJSONError."
```

---

## Task 5: Backend — test del service `MarkDeparture`

**Files:**
- Modify: `backend/internal/modules/driver/service_test.go` (agregar tests nuevos)

**Interfaces:**
- Consumes: `driverService.MarkDeparture` (Task 3), `mockDriverRepo` (Task 2)

- [ ] **Step 1: Agregar test del caso feliz**

En `backend/internal/modules/driver/service_test.go`, después de `TestMarkArrival_DriverAssigned_Success` (línea 105), agregar:

```go
func TestMarkDeparture_DriverAssigned_Success(t *testing.T) {
	// El conductor 77 marca salida de la parada 500. El repositorio resuelve:
	//   - stopTime 500 pertenece al viaje 10
	//   - viaje 10 tiene asignado al conductor 77 (coincide con el contexto)
	// MarkDeparture delega al SP sin error.
	repo := &mockDriverRepo{
		stopTimeTripID: 10,
		tripDriverID:   77,
	}
	svc := NewService(repo)
	ctx := ctxWithDriver(t, 77)

	err := svc.MarkDeparture(ctx, 500)
	require.NoError(t, err)
}

func TestMarkDeparture_DriverNotAssigned_Forbidden(t *testing.T) {
	// El conductor 77 intenta marcar salida de una parada del viaje 10,
	// pero el viaje 10 está asignado al conductor 99 (no a él).
	repo := &mockDriverRepo{
		stopTimeTripID:   10,
		tripDriverID:     99,
		markDepartureErr: nil,
	}
	svc := NewService(repo)
	ctx := ctxWithDriver(t, 77)

	err := svc.MarkDeparture(ctx, 500)
	require.Error(t, err)
	// Debe fallar con 403 (Forbidden) — el servicio valida ownership
	// antes de delegar al SP.
	var appErr *apperror.AppError
	require.True(t, errors.As(err, &appErr))
	require.Equal(t, "FORBIDDEN", string(appErr.Code))
}

func TestMarkDeparture_NotDriver_Unauthorized(t *testing.T) {
	// Sin context de conductor (no hay JWT válido).
	repo := &mockDriverRepo{}
	svc := NewService(repo)
	ctx := context.Background()

	err := svc.MarkDeparture(ctx, 500)
	require.Error(t, err)
}
```

Verificar el import path exacto de `apperror` mirando el resto del test file (probablemente `apperror` o `internal/shared/apperror`).

- [ ] **Step 2: Verificar que los tests pasan**

Run: `cd backend && go test ./internal/modules/driver/... -run MarkDeparture -v`
Expected: 3 tests PASS.

- [ ] **Step 3: Commit**

```bash
cd backend
git add internal/modules/driver/service_test.go
git commit -m "test(driver): MarkDeparture service tests (happy path + forbidden + unauth)"
```

---

## Task 6: Driver app — `markDeparture` en `DriverApi`

**Files:**
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/remote/DriverApi.kt:46` (agregar después de `markArrival`)

**Interfaces:**
- Consumes: Ktor `HttpClient`
- Produces: `suspend fun markDeparture(tripStopTimeId: Long): HttpResponse`

- [ ] **Step 1: Agregar función `markDeparture`**

En `DriverApi.kt`, después de la línea 47 (cierre de `markArrival`), agregar:

```kotlin
suspend fun markDeparture(tripStopTimeId: Long): HttpResponse =
    client.post("driver/trip-stops/$tripStopTimeId/departure")
```

- [ ] **Step 2: Verificar que compila el shared module**

Run: `cd driver-android && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

(Nota: el environment del usuario prohibe build automático, pero acá documentamos el comando para validación local.)

- [ ] **Step 3: Commit**

```bash
cd driver-android
git add shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/remote/DriverApi.kt
git commit -m "feat(driver-api): markDeparture() mirrors markArrival() endpoint"
```

---

## Task 7: Driver app — interfaz y impl de `markDeparture` en repository

**Files:**
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/domain/repository/DriverRepository.kt`
- Modify: archivo impl en `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/repository/` (buscar el nombre exacto)

**Interfaces:**
- Consumes: `DriverApi.markDeparture` (Task 6)
- Produces: `suspend fun markDeparture(tripStopTimeId: Long): Result<Unit>`

- [ ] **Step 1: Agregar a la interfaz**

En `DriverRepository.kt`, agregar después del método `markArrival` (si existe) o en la sección de "trip stop marks":

```kotlin
suspend fun markDeparture(tripStopTimeId: Long): Result<Unit>
```

Si la interfaz no tiene `markArrival` todavía (puede que `MarkArrival` solo exista en `driverRepositoryImpl`), seguir el patrón del código existente.

- [ ] **Step 2: Implementar en el repository**

En el archivo impl (probablemente `DriverRepositoryImpl.kt`), agregar después de la implementación de `markArrival`:

```kotlin
override suspend fun markDeparture(tripStopTimeId: Long): Result<Unit> = runCatching {
    api.markDeparture(tripStopTimeId)
    Unit
}
```

Adaptar el patrón exacto del código existente (puede usar `safeApiCall` u otro wrapper en vez de `runCatching`).

- [ ] **Step 3: Verificar compilación**

Run: `cd driver-android && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd driver-android
git add shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/domain/repository/DriverRepository.kt
git add shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/data/repository/
git commit -m "feat(driver-repo): markDeparture() interface + impl"
```

---

## Task 8: Driver app — botón "Marcar salida" en `StopRow`

**Files:**
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/screens/tripdetail/TripDetailScreen.kt:491-495`

**Interfaces:**
- Consumes: `viewModel.markDeparture(tripStopTimeId)` (Task 9), `TripStop.status` enum
- Produces: `StopRow` que muestra:
  - Si `PENDING` → botón "Marcar llegada"
  - Si `ARRIVED` → botón "Marcar salida"
  - Si `DEPARTED` o `SKIPPED` → sin botón

- [ ] **Step 1: Modificar `StopRow` para soportar ambos botones**

En `TripDetailScreen.kt`, modificar el bloque del `Button` (líneas 491-495) para que sea un `when`:

```kotlin
val buttonLabel: String? = when (stop.status) {
    TripStopStatus.PENDING -> if (pending) "…" else "Marcar llegada"
    TripStopStatus.ARRIVED -> if (pending) "…" else "Marcar salida"
    TripStopStatus.DEPARTED, TripStopStatus.SKIPPED -> null
}

if (buttonLabel != null) {
    Button(
        onClick = when (stop.status) {
            TripStopStatus.PENDING -> onMarkArrival
            TripStopStatus.ARRIVED -> onMarkDeparture
            else -> {} // unreachable, buttonLabel es null
        },
        enabled = !pending && tripInProgress,
    ) {
        Text(buttonLabel)
    }
}
```

Y modificar la firma de `StopRow` para aceptar `onMarkDeparture`:

```kotlin
private fun StopRow(
    stop: TripStop,
    pending: Boolean,
    tripInProgress: Boolean,
    onMarkArrival: () -> Unit,
    onMarkDeparture: () -> Unit,
)
```

- [ ] **Step 2: Pasar el callback desde el call site**

En la misma `TripDetailScreen.kt`, donde se invoca `StopRow(...)`, agregar el nuevo parámetro:

```kotlin
StopRow(
    stop = stop,
    pending = pending == stop.tripStopTimeId,
    tripInProgress = tripInProgress,
    onMarkArrival = { onMarkArrival(stop.tripStopTimeId) },
    onMarkDeparture = { onMarkDeparture(stop.tripStopTimeId) },
)
```

(Adaptar `pending == stop.tripStopTimeId` al patrón exacto del código existente — puede ser un set o un ID.)

- [ ] **Step 3: Verificar compilación**

Run: `cd driver-android && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd driver-android
git add shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/screens/tripdetail/TripDetailScreen.kt
git commit -m "feat(driver-ui): StopRow shows 'Marcar salida' when ARRIVED, 'Marcar llegada' when PENDING"
```

---

## Task 9: Driver app — `markDeparture` en `TripDetailViewModel`

**Files:**
- Modify: `driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/screens/tripdetail/TripDetailViewModel.kt` (archivo a verificar — el patrón está en `QrScanViewModel.kt:62-64` también)

**Interfaces:**
- Consumes: `DriverRepository.markDeparture` (Task 7)
- Produces: `fun markDeparture(tripStopTimeId: Long)` que llama al repo y refresca la lista

- [ ] **Step 1: Buscar `markArrival` en el ViewModel del trip detail**

```bash
grep -n "markArrival" driver-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/screens/tripdetail/
```

Si existe, agregar `markDeparture` mirror. Si NO existe en este archivo (puede que solo esté en `QrScanViewModel.kt`), buscar el ViewModel que controla `TripDetailScreen`.

- [ ] **Step 2: Agregar `markDeparture` siguiendo el patrón**

Agregar función:

```kotlin
fun markDeparture(tripStopTimeId: Long) {
    runAction("Salida registrada") {
        driverRepository.markDeparture(tripStopTimeId)
        // refrescar la lista para reflejar el cambio inmediato
        refreshStops()
    }
}
```

Si el ViewModel usa otro patrón (no `runAction`), seguir el patrón exacto del código existente.

- [ ] **Step 3: Verificar compilación**

Run: `cd driver-android && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd driver-android
git add shared/src/commonMain/kotlin/com/appmovilidadclinica/driver/shared/ui/screens/tripdetail/
git commit -m "feat(driver-vm): markDeparture() mirrors markArrival() pattern"
```

---

## Task 10: Pasajero — polling cada 20s en `MyReservationDetailViewModel`

**Files:**
- Modify: `passenger-android/app/src/main/java/com/appappmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailViewModel.kt:72-81`

**Interfaces:**
- Consumes: `tripsRepository.getDetail(tripId)`, `state.tripStatus` (estado del viaje)
- Produces: `LaunchedEffect` que dispara `refreshStops()` cada 20s SOLO cuando el viaje está `IN_PROGRESS` o `BOARDING`

- [ ] **Step 1: Agregar import de `LaunchedEffect` y `delay`**

En `MyReservationDetailViewModel.kt`, agregar imports:

```kotlin
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.isActive
```

(Si `LaunchedEffect` ya está importado en otro archivo, copiar el patrón exacto.)

- [ ] **Step 2: Agregar `refreshStops`**

Refactorizar `loadStopsIfNeeded` (líneas 72-81) en 2 funciones:

```kotlin
private var stopsLoadedForTripId: Long? = null

private fun loadStopsIfNeeded(tripId: Long) {
    if (stopsLoadedForTripId == tripId) return
    stopsLoadedForTripId = tripId
    refreshStops(tripId)
}

private fun refreshStops(tripId: Long) {
    viewModelScope.launch {
        when (val result = tripsRepository.getDetail(tripId)) {
            is AppResult.Success -> _uiState.update { it.copy(stops = result.data.stops, loadingStops = false) }
            is AppResult.Failure -> _uiState.update { it.copy(loadingStops = false) }
        }
    }
}
```

- [ ] **Step 3: Crear componente `@Composable` para el polling**

En `MyReservationDetailScreen.kt`, agregar función:

```kotlin
@Composable
private fun StopsPollingEffect(
    tripStatus: TripStatus?,
    tripId: Long?,
    onRefresh: () -> Unit,
) {
    LaunchedEffect(tripStatus, tripId) {
        if (tripId == null) return@LaunchedEffect
        val isActive = tripStatus == TripStatus.IN_PROGRESS ||
                       tripStatus == TripStatus.BOARDING
        if (!isActive) return@LaunchedEffect
        while (isActive) {
            delay(20.seconds)
            onRefresh()
        }
    }
}
```

Y agregar import `import kotlin.time.Duration.Companion.seconds`.

- [ ] **Step 4: Invocar el componente en la pantalla**

En `MyReservationDetailScreen`, agregar dentro del body del composable principal:

```kotlin
StopsPollingEffect(
    tripStatus = state.trip?.status,
    tripId = state.tripId,
    onRefresh = viewModel::refreshStops,
)
```

(Verificar el nombre exacto de `state.trip` — puede ser `state.reservation?.trip` o similar; adaptar.)

- [ ] **Step 5: Agregar `refreshStops` público al ViewModel**

En `MyReservationDetailViewModel.kt`, agregar:

```kotlin
fun refreshStops() {
    val tripId = stopsLoadedForTripId ?: return
    refreshStops(tripId)
}
```

Esto crea un conflicto de nombres con el `refreshStops` privado. Renombrar el privado a `doRefreshStops` o similar.

- [ ] **Step 6: Verificar compilación**

Run: `cd passenger-android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
cd passenger-android
git add app/src/main/java/com/appmovilidadclinica/passenger/presentation/myreservation/
git commit -m "feat(passenger): poll stops every 20s while trip is IN_PROGRESS or BOARDING"
```

---

## Task 11: Pasajero — semáforo visual en `TripStopRow`

**Files:**
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailScreen.kt:269-309`

**Interfaces:**
- Consumes: `TripStop.status`, `TripStop.actualArrivalAt`, `TripStop.actualDepartureAt`
- Produces: `TripStopRow` con un círculo de color:
  - `PENDING` → rojo `#D32F2F`
  - `ARRIVED` → amarillo `#F9A825`
  - `DEPARTED` → verde `#388E3C`
  - `SKIPPED` → gris `#9E9E9E`

- [ ] **Step 1: Definir colores del semáforo**

En `MyReservationDetailScreen.kt`, agregar al top del archivo:

```kotlin
private val StopPendingColor = Color(0xFFD32F2F)
private val StopArrivedColor = Color(0xFFF9A825)
private val StopDepartedColor = Color(0xFF388E3C)
private val StopSkippedColor = Color(0xFF9E9E9E)
```

(Verificar si ya existe un import de `androidx.compose.ui.graphics.Color` — agregar si falta.)

- [ ] **Step 2: Reemplazar `icon` y `iconColor` por el semáforo**

Modificar `TripStopRow` (líneas 278-309):

```kotlin
@Composable
private fun TripStopRow(stop: TripStop, isLast: Boolean) {
    val skipped = stop.status == TripStopStatus.SKIPPED
    val arrived = stop.actualArrivalAt != null
    val departed = stop.actualDepartureAt != null

    val semaphoreColor = when (stop.status) {
        TripStopStatus.PENDING -> StopPendingColor
        TripStopStatus.ARRIVED -> StopArrivedColor
        TripStopStatus.DEPARTED -> StopDepartedColor
        TripStopStatus.SKIPPED -> StopSkippedColor
    }

    val timeText = when {
        skipped -> "Parada omitida"
        departed -> "Salió ${stop.actualDepartureAt!!.toPeruTime()}"
        arrived -> "Llegó ${stop.actualArrivalAt!!.toPeruTime()}"
        else -> "Hora aprox. ${stop.scheduledArrivalAt.toPeruTime()}"
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(semaphoreColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                stop.stopName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (skipped) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (skipped) TextDecoration.LineThrough else TextDecoration.None,
            )
            Text(
                timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 3: Agregar imports necesarios**

Verificar que estén importados:
- `androidx.compose.foundation.shape.CircleShape`
- `androidx.compose.ui.text.style.TextDecoration`

Agregar si faltan.

- [ ] **Step 4: Verificar compilación**

Run: `cd passenger-android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
cd passenger-android
git add app/src/main/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailScreen.kt
git commit -m "feat(passenger): TripStopRow uses semaphore colors (red/yellow/green/grey)"
```

---

## Task 12: Pasajero — pull-to-refresh bonus

**Files:**
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailScreen.kt` (envolver el contenido en `PullToRefreshBox`)

**Interfaces:**
- Consumes: `viewModel::refreshStops`, `state.loadingStops`
- Produces: pull-to-refresh en la pantalla de detalle

- [ ] **Step 1: Agregar import y dependencias de PullToRefresh**

Verificar si `androidx.compose.material3.pulltorefresh` está disponible en la versión actual de Compose BOM. Si no, omitir esta task.

Si está disponible:

```kotlin
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
```

- [ ] **Step 2: Envolver el contenido**

Modificar el `Column` principal para usar `PullToRefreshBox`:

```kotlin
PullToRefreshBox(
    isRefreshing = state.loadingStops,
    onRefresh = viewModel::refreshStops,
    modifier = Modifier.fillMaxSize().padding(padding),
) {
    Column(modifier = Modifier.padding(16.dp)) {
        // ... contenido existente
    }
}
```

- [ ] **Step 3: Verificar compilación**

Run: `cd passenger-android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd passenger-android
git add app/src/main/java/com/appmovilidadclinica/passenger/presentation/myreservation/MyReservationDetailScreen.kt
git commit -m "feat(passenger): pull-to-refresh for stops list"
```

---

## Self-Review

**1. Spec coverage:**

| Sección del spec | Task |
|---|---|
| Backend Go: nuevo endpoint `/departure` | Tasks 1-4 |
| Backend Go: tests del service | Task 5 |
| Driver: API `markDeparture` | Task 6 |
| Driver: repository `markDeparture` | Task 7 |
| Driver: botón "Marcar salida" en `StopRow` | Task 8 |
| Driver: ViewModel `markDeparture` | Task 9 |
| Pasajero: polling cada 20s | Task 10 |
| Pasajero: semáforo visual | Task 11 |
| Pasajero: pull-to-refresh | Task 12 |
| Edge cases 422 (salida sin llegada) | Cubierto por SP (Task 1) + toast error en ViewModel (Task 9) |
| Edge case 409 (ya DEPARTED) | Cubierto por SP (Task 1) |
| Out of scope (websocket, push, admin) | No tocado (correcto) |

**2. Placeholder scan:** busqué "TBD", "TODO", "similar to", "implement later" — no hay.

**3. Type consistency:**
- `MarkDeparture(ctx, tripStopTimeID, driverID)` definido en Task 2 (interface), usado en Task 3 (service), usado en Task 7 (driver impl)
- `markDeparture(tripStopTimeId: Long)` en DriverApi (Task 6), en DriverRepository (Task 7), en TripDetailViewModel (Task 9), consumido en TripDetailScreen (Task 8)
- `refreshStops` público en ViewModel (Task 10), consumido en StopsPollingEffect (Task 10) y PullToRefreshBox (Task 12)
- `TripStopStatus` enum usado consistentemente en Tasks 8 y 11

**4. Forward references:** Task 7 depende de Task 6 ✓, Task 3 depende de Task 2 ✓, Task 4 depende de Task 3 ✓, Task 8 depende de Task 9 (forward ref) — Task 8 usa `onMarkDeparture` que viene del Task 9. Para evitar deadlock, Task 9 está completa antes de Task 8 en el orden sugerido, pero el orden puede ajustarse.

**5. Gaps encontrados y corregidos:**
- Inicialmente el plan no especificaba cómo manejar el conflicto de nombres entre `refreshStops` público y privado en el ViewModel del pasajero — corregido en Task 10 Step 5
- El handler del backend inicialmente no estaba claro cómo registra la ruta — explícito en Task 4 Step 2

---

## Done When

- [x] 12 tasks definidos con steps concretos y código exacto
- [x] Cada task termina con un commit incremental
- [x] Self-review ejecutado y sin gaps
- [x] Constraint del environment respetada ("No build después de cambios" — los comandos `./gradlew` son documentación para validación local del usuario)
- [x] 3 módulos aislados con sus propios git commits (backend, driver-android, passenger-android)
