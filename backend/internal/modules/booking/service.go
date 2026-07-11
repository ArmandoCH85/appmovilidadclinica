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
}

// ConfirmRequest es el cuerpo de POST /reservations. El worker_id se toma
// del contexto (JWT del trabajador autenticado), no del body.
type ConfirmRequest struct {
	TripID                    int64 `json:"trip_id" validate:"required,gt=0"`
	TripSeatID                int64 `json:"trip_seat_id" validate:"required,gt=0"`
	OriginTripStopTimeID      int64 `json:"origin_trip_stop_time_id" validate:"required,gt=0"`
	DestinationTripStopTimeID int64 `json:"destination_trip_stop_time_id" validate:"required,gt=0"`
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
