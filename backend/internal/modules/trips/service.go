package trips

import "context"

// TripsService define las operaciones de dominio del modulo trips. El
// modulo es de solo lectura, por lo que el servicio es fino: las reglas
// de filtrado viven en los SPs.
type TripsService interface {
	Search(ctx context.Context, serviceDate, direction string, originStopID, destStopID int64) ([]TripSearchResult, error)
	ListSeats(ctx context.Context, tripID, originStopTimeID, destStopTimeID int64) ([]SeatResult, error)
	GetDetail(ctx context.Context, tripID int64) (TripDetail, []TripStopDetail, error)
	// ListStops devuelve el catalogo de paradas para cualquier caller
	// autenticado. No exige rol especifico — el guard de JWT vive en el
	// router, no aca.
	ListStops(ctx context.Context) ([]Stop, error)
}

// tripsService es la implementacion concreta.
type tripsService struct {
	repo TripsRepository
}

// NewService construye el servicio con su repositorio inyectado.
func NewService(repo TripsRepository) TripsService {
	return &tripsService{repo: repo}
}

// Search delega al repositorio. La validacion de parametros (direction solo
// IDA/VUELTA, fechas validas) se hace en el handler con validator/v10 antes
// de llegar aqui.
func (s *tripsService) Search(ctx context.Context, serviceDate, direction string, originStopID, destStopID int64) ([]TripSearchResult, error) {
	return s.repo.SearchTrips(ctx, serviceDate, direction, originStopID, destStopID)
}

// ListSeats delega al repositorio.
func (s *tripsService) ListSeats(ctx context.Context, tripID, originStopTimeID, destStopTimeID int64) ([]SeatResult, error) {
	return s.repo.ListTripSeats(ctx, tripID, originStopTimeID, destStopTimeID)
}

// GetDetail delega al repositorio.
func (s *tripsService) GetDetail(ctx context.Context, tripID int64) (TripDetail, []TripStopDetail, error) {
	return s.repo.GetTripDetail(ctx, tripID)
}

// ListStops delega al repositorio. Lectura publica para cualquier JWT
// valido — la autorizacion por rol no aplica (ver `desarrollo_pasajero.md`
// §5.2: WORKER no tenia acceso a `/admin/stops` y necesitaba un endpoint
// equivalente sin guard de rol ADMIN).
func (s *tripsService) ListStops(ctx context.Context) ([]Stop, error) {
	return s.repo.ListStops(ctx)
}
