package booking

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/authctx"
)

// BookingService define las operaciones de dominio del modulo.
type BookingService interface {
	Confirm(ctx context.Context, req ConfirmRequest) (ConfirmResponse, error)
	Cancel(ctx context.Context, reservationID, actorUserID int64) error
	VerifyQR(ctx context.Context, token string) (Reservation, error)
	// SelfCheckin confirma el abordaje del propio trabajador desde la app.
	// El worker_id viene del JWT (ownership real server-side), no del body.
	// La ventana de tiempo y el resto de las reglas viven en el SP
	// (sp_mark_reservation_boarded_self) — aca solo resolvemos la identidad
	// del caller. Ver `desarrollo_pasajero.md` §5.1.
	SelfCheckin(ctx context.Context, reservationID int64) (SelfCheckinResult, error)
	// ListForWorker devuelve todas las reservas (activas e historicas) del
	// worker_id tomado del contexto (JWT). Es el endpoint que la app llama
	// en "Mis reservas" para sincronizar con el backend — sin esto, una
	// reserva creada en otro dispositivo o sesion no aparece en la app.
	ListForWorker(ctx context.Context) ([]ReservationListItem, error)
	// ReportPassengerIncident registra una incidencia reportada por el
	// pasajero sobre su propia reserva activa. El worker_id sale del JWT.
	// Devuelve el id de la fila creada en trip_incidents.
	ReportPassengerIncident(ctx context.Context, reservationID int64, req ReportIncidentRequest) (int64, error)
	GetJourney(ctx context.Context, reservationID int64) (JourneyState, error)
	Extend(ctx context.Context, reservationID int64, req ExtendRequest) (ExtendResult, error)
}

// ExtendRequest es el cuerpo de POST /reservations/{id}/extend.
// trip_seat_id es opcional: si no viene, se mantiene el asiento actual.
type ExtendRequest struct {
	NewDestinationTripStopTimeID int64 `json:"new_destination_trip_stop_time_id" validate:"required,gt=0"`
	TripSeatID                   int64 `json:"trip_seat_id" validate:"omitempty,gt=0"`
}

// ConfirmRequest es el cuerpo de POST /reservations. El worker_id se toma
// del contexto (JWT del trabajador autenticado), no del body.
type ConfirmRequest struct {
	TripID                    int64 `json:"trip_id" validate:"required,gt=0"`
	TripSeatID                int64 `json:"trip_seat_id" validate:"required,gt=0"`
	OriginTripStopTimeID      int64 `json:"origin_trip_stop_time_id" validate:"required,gt=0"`
	DestinationTripStopTimeID int64 `json:"destination_trip_stop_time_id" validate:"required,gt=0"`
}

// ReportIncidentRequest es el cuerpo de POST /reservations/{id}/incidents.
// El reservation_id viaja en el path y el worker_id en el JWT; el trip_id
// se deriva server-side desde la reserva.
type ReportIncidentRequest struct {
	IncidentType string `json:"incident_type" validate:"required,oneof=BREAKDOWN DELAY ACCIDENT OTHER"`
	Description  string `json:"description" validate:"required,min=10,max=1000"`
}

// ConfirmResponse devuelve el token QR crudo (para que el movil lo muestre)
// y los datos minimos de la reserva creada.
type ConfirmResponse struct {
	ReservationID   int64  `json:"reservation_id"`
	ReservationCode string `json:"reservation_code"`
	QRToken         string `json:"qr_token"`
	Status          string `json:"status"`
}

// bookingService es la implementacion concreta.
type bookingService struct {
	repo BookingRepository
}

// NewService construye el servicio con su repositorio inyectado.
func NewService(repo BookingRepository) BookingService {
	return &bookingService{repo: repo}
}

// Confirm aplica las reglas de negocio previas a la llamada al SP:
//  1. Obtiene worker_id del contexto (JWT). El usuario debe ser un WORKER.
//  2. Verifica la regla 1-reserva-por-viaje-por-trabajador con SELECT EXISTS.
//  3. Genera el booking_group_uuid (UUIDv4 con crypto/rand, sin dependencias).
//  4. Llama al repositorio que ejecuta sp_confirm_reservation.
//
// IDA+VUELTA no es atomico: el cliente hace dos POST /reservations
// independientes; booking_group_uuid es solo informativo para agruparlas.
func (s *bookingService) Confirm(ctx context.Context, req ConfirmRequest) (ConfirmResponse, error) {
	workerID, err := authctx.UserIDFromContext(ctx)
	if err != nil {
		return ConfirmResponse{}, apperror.UnauthorizedError{Reason: "token sin identidad de trabajador"}
	}

	active, err := s.repo.CheckActiveReservation(ctx, workerID, req.TripID)
	if err != nil {
		return ConfirmResponse{}, apperror.InternalError{Err: err}
	}
	if active {
		return ConfirmResponse{}, apperror.ConflictError{Msg: "el trabajador ya tiene una reserva activa en este viaje"}
	}

	groupUUID := newUUIDv4()
	result, err := s.repo.ConfirmReservation(ctx, ConfirmParams{
		TripID:                    req.TripID,
		WorkerID:                  workerID,
		TripSeatID:                req.TripSeatID,
		OriginTripStopTimeID:      req.OriginTripStopTimeID,
		DestinationTripStopTimeID: req.DestinationTripStopTimeID,
		BookingGroupUUID:          groupUUID,
	})
	if err != nil {
		return ConfirmResponse{}, err
	}
	return ConfirmResponse{
		ReservationID:   result.ReservationID,
		ReservationCode: result.ReservationCode,
		QRToken:         result.QRToken,
		Status:          result.Status,
	}, nil
}

// Cancel delega al repositorio. El actor_user_id viene del contexto (JWT).
func (s *bookingService) Cancel(ctx context.Context, reservationID, actorUserID int64) error {
	return s.repo.CancelReservation(ctx, reservationID, actorUserID)
}

// VerifyQR hashea el token escaneado con SHA-256 (inline, sin dependencias)
// y busca la reserva por qr_token_hash. El SP sp_confirm_reservation
// almacena SHA2(v_qr_token, 256) que es identico a SHA-256 hex.
func (s *bookingService) VerifyQR(ctx context.Context, token string) (Reservation, error) {
	if token == "" {
		return Reservation{}, apperror.ValidationError{Field: "token", Reason: "token QR vacio"}
	}
	sum := sha256.Sum256([]byte(token))
	hash := hex.EncodeToString(sum[:])
	return s.repo.VerifyQRToken(ctx, hash)
}

// SelfCheckin obtiene el worker_id del contexto (JWT) y delega al repo, que
// llama a sp_mark_reservation_boarded_self. El SP es quien valida:
//   - ownership real (reservation.worker_id == p_worker_id)
//   - status = CONFIRMED (si no, 409)
//   - ventana de tiempo ±30 min alrededor de origin_stop.scheduled_departure_at
//
// No validamos nada en Go para no duplicar reglas — el SP es la unica fuente
// de verdad (mismo patron que Confirm/Cancel).
func (s *bookingService) SelfCheckin(ctx context.Context, reservationID int64) (SelfCheckinResult, error) {
	workerID, err := authctx.UserIDFromContext(ctx)
	if err != nil {
		return SelfCheckinResult{}, apperror.UnauthorizedError{Reason: "token sin identidad de trabajador"}
	}
	return s.repo.SelfCheckin(ctx, reservationID, workerID)
}

// ListForWorker resuelve el worker_id del JWT y devuelve todas sus reservas.
// Sin paginar: un WORKER tiene a lo sumo unos pocos viajes por dia, asi que
// el catalogo total es chico. Ordenadas por confirmed_at DESC para que
// la mas reciente salga primero (UX directa en la lista).
func (s *bookingService) ListForWorker(ctx context.Context) ([]ReservationListItem, error) {
	workerID, err := authctx.UserIDFromContext(ctx)
	if err != nil {
		return nil, apperror.UnauthorizedError{Reason: "token sin identidad de trabajador"}
	}
	return s.repo.ListReservationsByWorker(ctx, workerID)
}

// GetJourney devuelve el estado de polling del pasajero dueño de la reserva.
func (s *bookingService) GetJourney(ctx context.Context, reservationID int64) (JourneyState, error) {
	workerID, err := requireWorker(ctx)
	if err != nil {
		return JourneyState{}, err
	}
	return s.repo.GetJourneyState(ctx, reservationID, workerID)
}

// Extend estira la reserva del pasajero a un nuevo destino.
func (s *bookingService) Extend(ctx context.Context, reservationID int64, req ExtendRequest) (ExtendResult, error) {
	workerID, err := requireWorker(ctx)
	if err != nil {
		return ExtendResult{}, err
	}
	return s.repo.ExtendReservation(ctx, ExtendParams{
		ReservationID:                reservationID,
		WorkerID:                     workerID,
		NewDestinationTripStopTimeID: req.NewDestinationTripStopTimeID,
		TripSeatID:                   req.TripSeatID,
	})
}

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
		return 0, apperror.ForbiddenError{Reason: "solo el rol WORKER puede acceder a las reservas"}
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

// newUUIDv4 genera un UUID v4 (RFC 4122) usando crypto/rand. Sin dependencia
// externa (google/uuid estaria permitido pero crypto/rand basta y mantiene
// el arbol de deps en 5+bcrypt).
func newUUIDv4() string {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		// rand.Read en Linux/Mac/Windows nunca devuelve error en practica
		// (usa /dev/urandom o BCryptGenRandom); si fallara, usar un UUID
		// debil es peor que panicar, mejor mantener el contrato.
		panic(fmt.Errorf("generando bytes aleatorios: %w", err))
	}
	// Version 4 y variante RFC 4122 (10xx).
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

// compile-time guard.
var _ BookingService = (*bookingService)(nil)
