# Reporte de incidentes del pasajero Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El pasajero reporta un incidente ligado a una reserva activa desde el menú de perfil; el reporte cae en `trip_incidents` vía un endpoint WORKER nuevo.

**Architecture:** Backend: endpoint `POST /api/reservations/{id}/incidents` en el módulo `booking` (dueño de las reservas), con guard de rol WORKER + ownership + estado válido, insertando en `trip_incidents`. App: fila nueva en `ProfileMenu` → pantalla `ReportIncident` (selector de reserva activa + tipo + descripción) → `ReservationsRepository.reportIncident`. Se reusan los patrones de change-password (DTO + KtorApi + `safeApiCall`, ViewModel con errores inline, toast + back).

**Tech Stack:** Go (chi, `validator/v10`, `database/sql`), Kotlin + Jetpack Compose Material3 + Hilt + Ktor + `kotlinx.serialization`.

## Global Constraints

- No migrar la base de datos: reusar tabla `trip_incidents` y enum `BREAKDOWN | DELAY | ACCIDENT | OTHER`.
- No tocar el flujo del conductor ni el panel admin.
- Mock a mano en tests Go, sin mockery (patrón de `auth/service_test.go` y `booking/service_test.go`).
- La app no tiene infraestructura de tests UI: verificación manual en emulador.
- Commits convencionales, sin atribución a IA.
- Desplegar el backend ANTES o junto con la app (si no, la app recibe 404).

---

## File Structure

| Archivo | Responsabilidad |
|---|---|
| `backend/internal/modules/booking/repository.go` (modificar) | `GetReservationIdentity` + `InsertIncident` + métodos en interfaz |
| `backend/internal/modules/booking/service.go` (modificar) | `requireWorker` + `ReportPassengerIncident` + método en interfaz + `ReportIncidentRequest` |
| `backend/internal/modules/booking/service_test.go` (modificar) | Extender `mockBookingRepo` + tests del servicio |
| `backend/internal/modules/booking/handler.go` (modificar) | `ReportIncident` + ruta `POST /reservations/{id}/incidents` |
| `passenger-android/shared/.../dto/ReservationDto.kt` (modificar) | `ReportIncidentRequestDto` + `ReportIncidentResponseDto` |
| `passenger-android/app/.../data/remote/KtorReservationsApi.kt` (modificar) | `reportIncident(reservationId, body)` |
| `passenger-android/app/.../domain/repository/ReservationsRepository.kt` (modificar) | `reportIncident(...)` en interfaz |
| `passenger-android/app/.../data/repository/ReservationsRepositoryImpl.kt` (modificar) | Implementación con `safeApiCall` |
| `passenger-android/app/.../tripsearch/ProfileMenu.kt` (modificar) | Fila "Reportar incidente" + param `onOpenReportIncident` |
| `passenger-android/app/.../tripsearch/TripSearchScreen.kt` (modificar) | Pasar `onOpenReportIncident` al menú |
| `passenger-android/app/.../navigation/Screen.kt` (modificar) | `Screen.ReportIncident` |
| `passenger-android/app/.../navigation/NavGraph.kt` (modificar) | Ruta + navegación desde TripSearch |
| `passenger-android/app/.../reportincident/ReportIncidentViewModel.kt` (crear) | Estado, validación, submit |
| `passenger-android/app/.../reportincident/ReportIncidentScreen.kt` (crear) | UI: selector reserva + tipo + descripción |

---

### Task 1: Repositorio backend — identidad de reserva e inserción de incidencia

**Files:**
- Modify: `backend/internal/modules/booking/repository.go`

**Interfaces:**
- Consumes: `dberr.NotFound(err, entity, id)` (ya importado en el archivo), tabla `reservations(id, worker_id, trip_id, status)`, tabla `trip_incidents(trip_id, reported_by_user_id, incident_type, description)`.
- Produces: `ReservationIdentity{WorkerID, TripID, Status}`, `GetReservationIdentity(ctx, reservationID)`, `InsertIncident(ctx, tripID, reporterUserID, incidentType, description) (int64, error)` — los usa la Task 2.

- [ ] **Step 1: Agregar métodos a la interfaz `BookingRepository`**

En `backend/internal/modules/booking/repository.go`, después de `ListReservationsByWorker` (línea ~79), agregar:

```go
	// GetReservationIdentity devuelve worker_id, trip_id y status de una
	// reserva. Usado por ReportPassengerIncident para validar ownership y
	// estado activo sin cargar toda la fila.
	GetReservationIdentity(ctx context.Context, reservationID int64) (ReservationIdentity, error)
	// InsertIncident inserta una incidencia de pasajero en trip_incidents y
	// devuelve su id. El trip_id ya viene resuelto por el servicio desde la
	// reserva (el cliente nunca lo manda).
	InsertIncident(ctx context.Context, tripID, reporterUserID int64, incidentType, description string) (int64, error)
```

Y el struct, junto a `Reservation` (línea ~41-52):

```go
// ReservationIdentity es lo mínimo de una reserva para validar ownership y
// estado en ReportPassengerIncident.
type ReservationIdentity struct {
	WorkerID int64
	TripID   int64
	Status   string
}
```

- [ ] **Step 2: Implementar ambos métodos** (al final del archivo, junto al resto de métodos con receptor `*bookingRepository`):

```go
// GetReservationIdentity lee worker_id, trip_id y status de una reserva.
// Reserva inexistente → dberr.NotFound ("reserva ..."), igual que
// GetUserByIdentifier en el módulo auth.
func (r *bookingRepository) GetReservationIdentity(ctx context.Context, reservationID int64) (ReservationIdentity, error) {
	const q = `SELECT worker_id, trip_id, status FROM reservations WHERE id = ?`
	var ident ReservationIdentity
	err := r.db.QueryRowContext(ctx, q, reservationID).Scan(&ident.WorkerID, &ident.TripID, &ident.Status)
	if err != nil {
		if nfErr := dberr.NotFound(err, "reserva", reservationID); nfErr != err {
			return ReservationIdentity{}, nfErr
		}
		return ReservationIdentity{}, fmt.Errorf("buscando reserva por id: %w", err)
	}
	return ident, nil
}

// InsertIncident inserta en trip_incidents. Mismo INSERT que
// driverRepository.ReportIncident (módulo driver), pero el trip_id llega
// resuelto desde la reserva del pasajero.
func (r *bookingRepository) InsertIncident(ctx context.Context, tripID, reporterUserID int64, incidentType, description string) (int64, error) {
	const q = `INSERT INTO trip_incidents (trip_id, reported_by_user_id, incident_type, description) VALUES (?, ?, ?, ?)`
	res, err := r.db.ExecContext(ctx, q, tripID, reporterUserID, incidentType, description)
	if err != nil {
		return 0, fmt.Errorf("insertando incidencia de pasajero: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return 0, fmt.Errorf("leyendo id de incidencia: %w", err)
	}
	return id, nil
}
```

- [ ] **Step 3: Verificar que compila**

Run: `cd backend && go build ./internal/modules/booking/`
Expected: sin errores (los tests fallarán hasta la Task 2 porque el mock no implementa la interfaz extendida — eso es lo esperado).

- [ ] **Step 4: Commit**

```bash
git add backend/internal/modules/booking/repository.go
git commit -m "feat(booking): identidad de reserva e insercion de incidencias en repositorio"
```

---

### Task 2: Servicio backend — `ReportPassengerIncident` con TDD

**Files:**
- Modify: `backend/internal/modules/booking/service.go`
- Modify: `backend/internal/modules/booking/service_test.go` (mock + tests)

**Interfaces:**
- Consumes: `GetReservationIdentity`, `InsertIncident` (Task 1); `authctx.UserIDFromContext`, `authctx.RoleFromContext`; `apperror.{UnauthorizedError,ForbiddenError,NotFoundError,ConflictError}`.
- Produces: `ReportIncidentRequest{IncidentType, Description}`, `ReportPassengerIncident(ctx, reservationID, req) (int64, error)` — los usa la Task 3.

- [ ] **Step 1: Extender el mock con los métodos nuevos** (falla la compilación del test hasta implementarlos — ese es el "rojo")

En `backend/internal/modules/booking/service_test.go`, agregar campos y métodos al `mockBookingRepo`:

```go
	identity    ReservationIdentity
	identityErr error
	incidentID  int64
	incidentErr error
```

```go
func (m *mockBookingRepo) GetReservationIdentity(_ context.Context, _ int64) (ReservationIdentity, error) {
	return m.identity, m.identityErr
}

func (m *mockBookingRepo) InsertIncident(_ context.Context, _, _ int64, _, _ string) (int64, error) {
	return m.incidentID, m.incidentErr
}
```

Y un helper de contexto con rol parametrizable (el existente `ctxWithWorker` hardcodea `"WORKER"`):

```go
// ctxWithRole igual que ctxWithWorker pero con rol dado, para probar el
// rechazo a roles no-WORKER.
func ctxWithRole(t *testing.T, userID int64, role string) context.Context {
	t.Helper()
	ja := jwtauth.New("HS256", []byte("booking-test-secret"), nil)
	claims := map[string]any{
		"user_id": float64(userID), // JSON deserializa enteros a float64
		"role":    role,
	}
	token, _, err := ja.Encode(claims)
	require.NoError(t, err)
	return jwtauth.NewContext(context.Background(), token, nil)
}
```

- [ ] **Step 2: Escribir los tests que fallan**

Agregar al final de `service_test.go`:

```go
func TestReportPassengerIncident_Success_ReturnsID(t *testing.T) {
	repo := &mockBookingRepo{
		identity:   ReservationIdentity{WorkerID: 77, TripID: 5, Status: "CONFIRMED"},
		incidentID: 900,
	}
	svc := NewService(repo)

	id, err := svc.ReportPassengerIncident(ctxWithWorker(t, 77), 42, ReportIncidentRequest{
		IncidentType: "DELAY",
		Description:  "El bus nunca pasó por mi parada",
	})
	require.NoError(t, err)
	assert.Equal(t, int64(900), id)
}

func TestReportPassengerIncident_ForeignReservation_ReturnsNotFound(t *testing.T) {
	// La reserva es de otro worker: 404 genérico, sin filtrar que existe.
	repo := &mockBookingRepo{
		identity: ReservationIdentity{WorkerID: 99, TripID: 5, Status: "CONFIRMED"},
	}
	svc := NewService(repo)

	_, err := svc.ReportPassengerIncident(ctxWithWorker(t, 77), 42, ReportIncidentRequest{
		IncidentType: "DELAY",
		Description:  "El bus nunca pasó por mi parada",
	})
	require.Error(t, err)
	var nf apperror.NotFoundError
	require.True(t, errors.As(err, &nf), "reserva ajena debe mapear a NotFound")
	assert.Empty(t, repo.incidentID, "no debe insertar nada si el ownership falla")
}

func TestReportPassengerIncident_CancelledReservation_ReturnsConflict(t *testing.T) {
	repo := &mockBookingRepo{
		identity: ReservationIdentity{WorkerID: 77, TripID: 5, Status: "CANCELLED"},
	}
	svc := NewService(repo)

	_, err := svc.ReportPassengerIncident(ctxWithWorker(t, 77), 42, ReportIncidentRequest{
		IncidentType: "DELAY",
		Description:  "El bus nunca pasó por mi parada",
	})
	require.Error(t, err)
	var ce apperror.ConflictError
	require.True(t, errors.As(err, &ce), "reserva no activa debe mapear a Conflict")
}

func TestReportPassengerIncident_NonWorkerRole_ReturnsForbidden(t *testing.T) {
	repo := &mockBookingRepo{
		identity: ReservationIdentity{WorkerID: 77, TripID: 5, Status: "CONFIRMED"},
	}
	svc := NewService(repo)

	_, err := svc.ReportPassengerIncident(ctxWithRole(t, 77, "DRIVER"), 42, ReportIncidentRequest{
		IncidentType: "DELAY",
		Description:  "El bus nunca pasó por mi parada",
	})
	require.Error(t, err)
	var fe apperror.ForbiddenError
	require.True(t, errors.As(err, &fe), "rol no-WORKER debe mapear a Forbidden")
}
```

- [ ] **Step 3: Correr y confirmar el fallo**

Run: `cd backend && go test ./internal/modules/booking/ -run TestReportPassengerIncident -v`
Expected: FAIL — `ReportPassengerIncident` y/o `ReportIncidentRequest` no definidos.

- [ ] **Step 4: Implementación mínima en `service.go`**

Agregar a la interfaz `BookingService` (después de `ListForWorker`):

```go
	// ReportPassengerIncident registra una incidencia reportada por el
	// pasajero sobre su propia reserva activa. El worker_id sale del JWT.
	// Devuelve el id de la fila creada en trip_incidents.
	ReportPassengerIncident(ctx context.Context, reservationID int64, req ReportIncidentRequest) (int64, error)
```

Agregar el request junto a `ConfirmRequest`:

```go
// ReportIncidentRequest es el cuerpo de POST /reservations/{id}/incidents.
// El reservation_id viaja en el path y el worker_id en el JWT; el trip_id
// se deriva server-side desde la reserva.
type ReportIncidentRequest struct {
	IncidentType string `json:"incident_type" validate:"required,oneof=BREAKDOWN DELAY ACCIDENT OTHER"`
	Description  string `json:"description" validate:"required,min=10,max=1000"`
}
```

Agregar el guard de rol (espejo de `requireDriver` en `driver/service.go:47`) y el método, al final del archivo antes de `newUUIDv4`:

```go
// RoleWORKER es el único rol que puede reportar incidencias de pasajero.
const RoleWORKER = "WORKER"

// requireWorker extrae y valida que el caller tenga rol WORKER. Espejo de
// requireDriver del módulo driver. UnauthorizedError sin claims/rol,
// ForbiddenError si el rol no es WORKER.
func requireWorker(ctx context.Context) (int64, error) {
	workerID, err := authctx.UserIDFromContext(ctx)
	if err != nil {
		return 0, apperror.UnauthorizedError{Reason: "token sin identidad de trabajador"}
	}
	role, err := authctx.RoleFromContext(ctx)
	if err != nil {
		return 0, apperror.UnauthorizedError{Reason: "token sin rol"}
	}
	if role != RoleWORKER {
		return 0, apperror.ForbiddenError{Reason: "solo el rol WORKER puede reportar incidencias de pasajero"}
	}
	return workerID, nil
}

// ReportPassengerIncident valida ownership + estado activo e inserta en
// trip_incidents con reported_by_user_id = caller. Reserva ajena → 404
// genérico (no filtrar existencia); estado no activo → 409.
func (s *bookingService) ReportPassengerIncident(ctx context.Context, reservationID int64, req ReportIncidentRequest) (int64, error) {
	workerID, err := requireWorker(ctx)
	if err != nil {
		return 0, err
	}
	ident, err := s.repo.GetReservationIdentity(ctx, reservationID)
	if err != nil {
		return 0, err
	}
	if ident.WorkerID != workerID {
		return 0, apperror.NotFoundError{Entity: "reserva", ID: reservationID}
	}
	if ident.Status != "CONFIRMED" && ident.Status != "BOARDED" {
		return 0, apperror.ConflictError{Msg: "solo puedes reportar incidentes sobre viajes activos (reservas confirmadas o abordadas)"}
	}
	return s.repo.InsertIncident(ctx, ident.TripID, workerID, req.IncidentType, req.Description)
}
```

- [ ] **Step 5: Correr tests**

Run: `cd backend && go test ./internal/modules/booking/ -v`
Expected: PASS todos (nuevos + existentes).

- [ ] **Step 6: Commit**

```bash
git add backend/internal/modules/booking/service.go backend/internal/modules/booking/service_test.go
git commit -m "feat(booking): servicio de reporte de incidencias del pasajero"
```

---

### Task 3: Handler backend + ruta

**Files:**
- Modify: `backend/internal/modules/booking/handler.go`

**Interfaces:**
- Consumes: `ReportPassengerIncident` (Task 2); patrón de `Cancel` para parsear `{id}` con `strconv.ParseInt` + `chi.URLParam`.
- Produces: `POST /api/reservations/{id}/incidents` → 201 `{"id"}` — lo consume la Task 4.

- [ ] **Step 1: Agregar handler y ruta**

En `handler.go`, después de `SelfCheckin` y antes de `ListMine` (o junto a los otros POST), agregar:

```go
// reportIncidentResponse es la respuesta de POST /reservations/{id}/incidents.
type reportIncidentResponse struct {
	ID int64 `json:"id"`
}

// ReportIncident maneja POST /reservations/{id}/incidents — registra una
// incidencia reportada por el pasajero sobre su propia reserva. El
// reservation_id autoritativo es el del path; el worker_id sale del JWT.
// Patrón idéntico a Cancel para el parseo del id.
func (h *BookingHandler) ReportIncident(w http.ResponseWriter, r *http.Request) {
	reservationID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "id", Reason: "debe ser un entero positivo"})
		return
	}
	var req ReportIncidentRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	id, err := h.svc.ReportPassengerIncident(r.Context(), reservationID, req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	_ = json.NewEncoder(w).Encode(reportIncidentResponse{ID: id})
}
```

En `RegisterRoutes`, agregar (sin conflicto con las rutas existentes: el sufijo `/incidents` no colisiona con `/cancel`, `/self-checkin` ni con la ruta estática `/verify-qr`):

```go
	r.Post("/reservations/{id}/incidents", h.ReportIncident)
```

- [ ] **Step 2: Verificar compilación + tests del paquete**

Run: `cd backend && go build ./... && go test ./internal/modules/booking/`
Expected: build OK, tests PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/internal/modules/booking/handler.go
git commit -m "feat(booking): endpoint POST /reservations/{id}/incidents para pasajeros"
```

---

### Task 4: Capa data Android — DTO, API y repositorio

**Files:**
- Modify: `passenger-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/passenger/shared/data/remote/dto/ReservationDto.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/data/remote/KtorReservationsApi.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/domain/repository/ReservationsRepository.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/data/repository/ReservationsRepositoryImpl.kt`

**Interfaces:**
- Consumes: `safeApiCall`, `AppResult.map` (`shared.domain.error.map`), `io.ktor.client.call.body`.
- Produces: `ReservationsRepository.reportIncident(reservationId, incidentType, description): AppResult<Long>` — lo usa la Task 6.

- [ ] **Step 1: DTOs** — al final de `ReservationDto.kt`:

```kotlin
@Serializable
data class ReportIncidentRequestDto(
    @SerialName("incident_type") val incidentType: String,
    val description: String,
)

@Serializable
data class ReportIncidentResponseDto(val id: Long)
```

- [ ] **Step 2: Método en `KtorReservationsApi`** (junto a `cancel`/`selfCheckin`):

```kotlin
    suspend fun reportIncident(reservationId: Long, body: ReportIncidentRequestDto): HttpResponse =
        client.post("reservations/$reservationId/incidents") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
```

- [ ] **Step 3: Método en la interfaz `ReservationsRepository`** (después de `selfCheckin`):

```kotlin
    /**
     * POST /api/reservations/{id}/incidents ” 201 {id}. La reserva debe ser
     * propia y estar activa (CONFIRMED/BOARDED); si no, el backend responde
     * 404/409 con mensaje accionable.
     */
    suspend fun reportIncident(reservationId: Long, incidentType: String, description: String): AppResult<Long>
```

- [ ] **Step 4: Implementación en `ReservationsRepositoryImpl`** (después de `selfCheckin`, líneas ~67-80). Agregar imports de `ReportIncidentRequestDto`, `ReportIncidentResponseDto` y `com.appmovilidadclinica.passenger.shared.domain.error.map`:

```kotlin
    override suspend fun reportIncident(
        reservationId: Long,
        incidentType: String,
        description: String,
    ): AppResult<Long> {
        val result = safeApiCall<ReportIncidentResponseDto>(
            errorMapper = errorMapper,
            call = {
                apiClient.reservationsApi.reportIncident(
                    reservationId,
                    ReportIncidentRequestDto(incidentType, description),
                )
            },
            parseBody = { it.body() },
        )
        return result.map { it.id }
    }
```

- [ ] **Step 5: Commit**

```bash
git add passenger-android/shared/src/commonMain/kotlin/com/appmovilidadclinica/passenger/shared/data/remote/dto/ReservationDto.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/data/remote/KtorReservationsApi.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/domain/repository/ReservationsRepository.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/data/repository/ReservationsRepositoryImpl.kt
git commit -m "feat(passenger): capa data para reporte de incidencias"
```

---

### Task 5: Navegación — fila en menú perfil + ruta

**Files:**
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/ProfileMenu.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchScreen.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/navigation/Screen.kt`
- Modify: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: nada nuevo.
- Produces: `Screen.ReportIncident`, param `onOpenReportIncident` en `ProfileMenuButton` y `TripSearchScreen` — los usa la Task 6.

- [ ] **Step 1: `Screen.ReportIncident`** — en `Screen.kt`, junto a `ChangePassword`:

```kotlin
    @Serializable
    data object ReportIncident : Screen
```

- [ ] **Step 2: Fila en `ProfileMenuButton`** — agregar param `onOpenReportIncident: () -> Unit` a la firma, importar `Icons.Filled.ReportProblem`, y después de la fila "Cambiar clave" + su `HorizontalDivider`, agregar:

```kotlin
                    ProfileMenuRow(
                        icon = Icons.Default.ReportProblem,
                        label = "Reportar incidente",
                        color = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            close()
                            onOpenReportIncident()
                        },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
```

(La fila va entre "Cambiar clave" y "Cerrar sesión", cada una separada por su divisor, como el diseño aprobado.)

- [ ] **Step 3: `TripSearchScreen`** — agregar param `onOpenReportIncident: () -> Unit` a la firma y pasarlo a `ProfileMenuButton`.

- [ ] **Step 4: `NavGraph`** — en el `composable<Screen.TripSearch>`, pasar `onOpenReportIncident = { navController.navigate(Screen.ReportIncident) }`; agregar ruta:

```kotlin
        composable<Screen.ReportIncident> {
            ReportIncidentScreen(onBack = { navController.popBackStack() })
        }
```

(junto al `composable<Screen.ChangePassword>`. `ReportIncidentScreen` aún no existe: el proyecto no compilará hasta la Task 6 — eso es lo esperado.)

- [ ] **Step 5: Commit**

```bash
git add passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/ProfileMenu.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchScreen.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/navigation/Screen.kt passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/navigation/NavGraph.kt
git commit -m "feat(passenger): entrada a reporte de incidentes desde menu perfil"
```

---

### Task 6: Pantalla `ReportIncident` + ViewModel

**Files:**
- Create: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/reportincident/ReportIncidentViewModel.kt`
- Create: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/reportincident/ReportIncidentScreen.kt`

**Interfaces:**
- Consumes: `ReservationsRepository.reportIncident` + `observeReservations()` (Task 4); `ReservationStatus` (`shared.domain.model`); `Screen.ReportIncident` + `onBack` (Task 5).
- Produces: pantalla funcional verificable en la Task 7.

Plantilla: copiar la estructura de `presentation/changepassword/ChangePasswordViewModel.kt` y `ChangePasswordScreen.kt` (mismo Scaffold + TopAppBar con ←, botón píldora `MintButton`/`DarkGreenText`, `TextButton` Cancelar, toast + `onBack()` en éxito, errores inline sin limpiar campos).

- [ ] **Step 1: ViewModel** — crear `ReportIncidentViewModel.kt`:

```kotlin
package com.appmovilidadclinica.passenger.presentation.reportincident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportIncidentUiState(
    val selectedReservationId: Long? = null,
    val incidentType: String = "",
    val description: String = "",
    val reservationError: String? = null,
    val typeError: String? = null,
    val descriptionError: String? = null,
    val formError: String? = null,
    val submitting: Boolean = false,
    val sent: Boolean = false,
)

@HiltViewModel
class ReportIncidentViewModel @Inject constructor(
    private val reservationsRepository: ReservationsRepository,
) : ViewModel() {

    /** Reservas activas (fuente: Room, igual que Mis reservas). */
    val activeReservations: StateFlow<List<Reservation>> = reservationsRepository.observeReservations()
        .map { list ->
            list.filter {
                it.status == ReservationStatus.CONFIRMED || it.status == ReservationStatus.BOARDED
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow(ReportIncidentUiState())
    val uiState: StateFlow<ReportIncidentUiState> = _uiState

    fun onReservationSelected(id: Long) {
        _uiState.update { it.copy(selectedReservationId = id, reservationError = null, formError = null) }
    }

    fun onTypeChange(value: String) {
        _uiState.update { it.copy(incidentType = value, typeError = null, formError = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value.take(1000), descriptionError = null, formError = null) }
    }

    fun submit() {
        val s = _uiState.value
        val reservationError = if (s.selectedReservationId == null) "Elige el viaje sobre el que reportas." else null
        val typeError = if (s.incidentType.isBlank()) "Elige el tipo de incidente." else null
        val descriptionError = when {
            s.description.isBlank() -> "Describe lo ocurrido."
            s.description.trim().length < 10 -> "Mínimo 10 caracteres."
            else -> null
        }
        if (reservationError != null || typeError != null || descriptionError != null) {
            _uiState.update {
                it.copy(
                    reservationError = reservationError,
                    typeError = typeError,
                    descriptionError = descriptionError,
                )
            }
            return
        }
        _uiState.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            when (
                val result = reservationsRepository.reportIncident(
                    s.selectedReservationId!!,
                    s.incidentType,
                    s.description.trim(),
                )
            ) {
                is AppResult.Success -> _uiState.update { it.copy(submitting = false, sent = true) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(submitting = false, formError = messageFor(result.error))
                }
            }
        }
    }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Conflict -> error.message.ifBlank { "Ese viaje ya no está activo para reportar." }
        is AppError.Validation -> error.message
        is AppError.NotFound -> error.message.ifBlank { "La reserva ya no existe." }
        is AppError.Network -> "No se pudo conectar con el servidor. Verifique su conexión."
        is AppError.Unauthorized -> error.message.ifBlank { "Sesión expirada. Inicie sesión nuevamente." }
        is AppError.Forbidden -> error.message
        is AppError.Unknown -> error.message.ifBlank { "Ocurrió un error inesperado. Intente nuevamente." }
    }
}
```

- [ ] **Step 2: Screen** — crear `ReportIncidentScreen.kt` con esta estructura exacta (detalles visuales del spec §5):

```kotlin
package com.appmovilidadclinica.passenger.presentation.reportincident

// Etiquetas legibles para el enum del backend (spec §3: se reusa el enum).
private val INCIDENT_TYPE_LABELS = listOf(
    "DELAY" to "Retraso / el bus no pasó",
    "BREAKDOWN" to "Problemas con la unidad",
    "ACCIDENT" to "Accidente",
    "OTHER" to "Otro",
)
```

- `Scaffold` + `TopAppBar(title = "Reportar incidente", navigationIcon = ← que llama `onBack`)`.
- `LaunchedEffect(state.sent)`: toast `"Reporte enviado con éxito"` + `onBack()` (patrón de `ChangePasswordScreen`).
- Selector de reserva: `ExposedDropdownMenuBox` + `OutlinedTextField(readOnly)` + `ExposedDropdownMenu` con `DropdownMenuItem` por reserva activa; etiqueta de cada item: `"${originName} → ${destinationName} · Asiento ${seatLabel}"` (campos del dominio `Reservation`: `originName`, `destinationName`, `seatLabel` — sin formatear fechas para no depender de `TimeFormat`). `isError` + `supportingText` con `reservationError`.
- Si `activeReservations` está vacía (y no cargando): texto `"No tienes viajes activos para reportar."` en lugar del selector.
- Selector de tipo: mismo patrón dropdown con `INCIDENT_TYPE_LABELS`; guarda el código (`"DELAY"`, etc.) vía `onTypeChange`; muestra la etiqueta.
- Descripción: `OutlinedTextField` multilínea (`minLines = 4`), `supportingText`: error en rojo, si no contador `"${description.length}/1000"` en gris; helper `"Mínimo 10 caracteres."` cuando no hay error ni texto.
- `formError` (mensaje del backend) en rojo debajo, sin limpiar campos.
- Botón "Enviar reporte": `Button` full-width 52dp, `shape = CircleShape`, `containerColor = Color(0xFFB2F2D5)`, `contentColor = Color(0xFF0B3D2E)`, texto bold; `enabled = !submitting && activeReservations.isNotEmpty()`; spinner cuando `submitting`.
- `TextButton("Cancelar")` → `onBack`.

- [ ] **Step 3: Commit**

```bash
git add passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/reportincident/
git commit -m "feat(passenger): pantalla de reporte de incidentes"
```

---

### Task 7: Verificación punta a punta

**Files:** ninguno (solo comandos).

- [ ] **Step 1: Tests backend**

Run: `cd backend && go test ./internal/modules/booking/ -v`
Expected: PASS (incluye `TestReportPassengerIncident_*`).

- [ ] **Step 2: Compilar app**

Run: `cd passenger-android && .\gradlew :app:assembleDebug -x test --console=plain -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: QA manual en emulador** (instalar APK debug, loguearse como WORKER):
  1. Abrir menú perfil → existe "Reportar incidente" entre "Cambiar clave" y "Cerrar sesión".
  2. Sin reservas activas → mensaje "No tienes viajes activos..." y botón deshabilitado.
  3. Con reserva activa → validaciones inline (sin reserva/tipo, descripción < 10) sin perder lo escrito.
  4. Envío OK → toast "Reporte enviado con éxito" + vuelve atrás.
  5. Verificar en `GET /api/admin/incidents` (o panel) que el reporte aparece con el pasajero como reportante.

- [ ] **Step 4: Commit final si hubo fixes** (solo si el QA obligó a cambios; si no, nada que commitear).

---

## Self-Review

1. **Spec coverage:** §4 backend → Tasks 1-3 (endpoint, ownership, estados, 201/404/409/422/403, tests). §5 app → Tasks 4-6 (entrada en perfil, selector de activas, tipo+descripción 10-1000, botón píldora, toast+back, errores inline). No-objetivos respetados (sin foto, sin panel, sin historial, enum reusado). Rollout → Global Constraints + Task 7.
2. **Placeholder scan:** sin TBD/TODO; cada paso trae código o comando con salida esperada. La etiqueta de reserva evita `TimeFormat` a propósito (campos verificados en `Reservation.kt`: `originName`, `destinationName`, `seatLabel`).
3. **Type consistency:** `ReportPassengerIncident(ctx, reservationID int64, req ReportIncidentRequest) (int64, error)` igual en interfaz, impl, handler y tests. `reportIncident(reservationId: Long, incidentType: String, description: String): AppResult<Long>` igual en interfaz, impl y ViewModel. `ReportIncidentRequestDto(incidentType, description)` coincide con el body que el handler decodifica. `Icons.Filled.ReportProblem` pertenece al set filled ya usado en las filas del menú (`Default.Person`, `Default.Lock`).
