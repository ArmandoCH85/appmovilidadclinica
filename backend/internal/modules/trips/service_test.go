package trips

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// mockTripsRepo cumple TripsRepository para tests. Mock a mano, sin mockery.
type mockTripsRepo struct {
	searchResults []TripSearchResult
	searchErr     error
	seats         []SeatResult
	seatsErr      error
	detail        TripDetail
	stops         []TripStopDetail
	detailErr     error
}

func (m *mockTripsRepo) SearchTrips(_ context.Context, _, _ string, _, _ int64) ([]TripSearchResult, error) {
	return m.searchResults, m.searchErr
}

func (m *mockTripsRepo) ListTripSeats(_ context.Context, _, _, _ int64) ([]SeatResult, error) {
	return m.seats, m.seatsErr
}

func (m *mockTripsRepo) GetTripDetail(_ context.Context, _ int64) (TripDetail, []TripStopDetail, error) {
	return m.detail, m.stops, m.detailErr
}

func TestSearch_ReturnsResults(t *testing.T) {
	expected := []TripSearchResult{
		{TripID: 1, TripCode: "T-001", RouteName: "Sede-Lima", AvailableSeats: 5},
		{TripID: 2, TripCode: "T-002", RouteName: "Lima-Sede", AvailableSeats: 8},
	}
	repo := &mockTripsRepo{searchResults: expected}
	svc := NewService(repo)

	got, err := svc.Search(context.Background(), "2026-07-15", "IDA", 1, 5)
	require.NoError(t, err)
	assert.Len(t, got, 2)
	assert.Equal(t, "T-001", got[0].TripCode)
	assert.Equal(t, 8, got[1].AvailableSeats)
}

func TestSearch_NoResults_ReturnsEmptySlice(t *testing.T) {
	// El SP devuelve 0 filas; el repositorio escanea un slice vacio (no nil).
	// El servicio propaga el slice vacio tal cual para que el handler answer
	// [] en el JSON en lugar de null.
	repo := &mockTripsRepo{searchResults: []TripSearchResult{}}
	svc := NewService(repo)

	got, err := svc.Search(context.Background(), "9999-01-01", "IDA", 1, 5)
	require.NoError(t, err)
	assert.Empty(t, got)
	assert.NotNil(t, got, "debe devolver slice vacio, no nil")
}

func TestGetDetail_Success(t *testing.T) {
	expectedDetail := TripDetail{ID: 7, TripCode: "T-007", Status: "PUBLISHED"}
	expectedStops := []TripStopDetail{
		{ID: 70, StopOrder: 1, StopName: "Sede"},
		{ID: 71, StopOrder: 2, StopName: "Paradero 1"},
	}
	repo := &mockTripsRepo{detail: expectedDetail, stops: expectedStops}
	svc := NewService(repo)

	detail, stops, err := svc.GetDetail(context.Background(), 7)
	require.NoError(t, err)
	assert.Equal(t, "T-007", detail.TripCode)
	assert.Equal(t, "PUBLISHED", detail.Status)
	assert.Len(t, stops, 2)
	assert.Equal(t, "Sede", stops[0].StopName)
}

var _ TripsRepository = (*mockTripsRepo)(nil)
