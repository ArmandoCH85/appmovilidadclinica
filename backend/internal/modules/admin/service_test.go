package admin

import (
	"context"
	"errors"
	"testing"

	"github.com/go-chi/jwtauth/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/crypto/bcrypt"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/types"
)

// mockAdminRepo cumple AdminRepository para tests. Mock a mano, sin mockery.
// Solo se rellenan los campos que cada test consume; el resto devuelve cero.
type mockAdminRepo struct {
	// Contadores para afirmar que el servicio corta antes de llamar al repo
	// cuando el guard de rol falla.
	createStopCalls       int
	updateTripStatusCalls int
	createUserCalls       int
	updateUserCalls       int

	createStopResult    Stop
	createStopErr       error
	updateTripStatusErr error

	// receivedUserCreate/receivedUserUpdate capturan lo que el servicio
	// efectivamente le pasa al repositorio, para afirmar que el password ya
	// llega hasheado (nunca en texto plano) en este punto.
	receivedUserCreate UserCreateParams
	receivedUserUpdate UserUpdateParams
}

func (m *mockAdminRepo) ListStops(_ context.Context, _ types.PaginationParams) ([]Stop, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateStop(_ context.Context, p StopCreateParams) (Stop, error) {
	m.createStopCalls++
	return m.createStopResult, m.createStopErr
}
func (m *mockAdminRepo) UpdateStop(_ context.Context, _ int64, _ StopUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) ListUsers(_ context.Context, _ types.PaginationParams) ([]User, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateUser(_ context.Context, p UserCreateParams) (User, error) {
	m.createUserCalls++
	m.receivedUserCreate = p
	return User{ID: 1, EmployeeCode: p.EmployeeCode, DocumentNumber: p.DocumentNumber}, nil
}
func (m *mockAdminRepo) UpdateUser(_ context.Context, _ int64, p UserUpdateParams) error {
	m.updateUserCalls++
	m.receivedUserUpdate = p
	return nil
}
func (m *mockAdminRepo) ListVehicles(_ context.Context, _ types.PaginationParams) ([]Vehicle, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateVehicle(_ context.Context, _ VehicleCreateParams) (Vehicle, error) {
	return Vehicle{}, nil
}
func (m *mockAdminRepo) UpdateVehicle(_ context.Context, _ int64, _ VehicleUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) ListRoutes(_ context.Context, _ types.PaginationParams) ([]Route, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateRoute(_ context.Context, _ RouteCreateParams) (Route, error) {
	return Route{}, nil
}
func (m *mockAdminRepo) UpdateRoute(_ context.Context, _ int64, _ RouteUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) ListRouteStops(_ context.Context, _ int64, _ types.PaginationParams) ([]RouteStop, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateRouteStop(_ context.Context, _ RouteStopCreateParams) (RouteStop, error) {
	return RouteStop{}, nil
}
func (m *mockAdminRepo) UpdateRouteStop(_ context.Context, _ int64, _ RouteStopUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) ListTemplates(_ context.Context, _ types.PaginationParams) ([]Template, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateTemplate(_ context.Context, _ TemplateCreateParams) (Template, error) {
	return Template{}, nil
}
func (m *mockAdminRepo) UpdateTemplate(_ context.Context, _ int64, _ TemplateUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) GetCalendar(_ context.Context, _ int64) (Calendar, error) {
	return Calendar{}, nil
}

func (m *mockAdminRepo) ListCalendars(_ context.Context, _ types.PaginationParams) ([]Calendar, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateCalendar(_ context.Context, _ CalendarCreateParams) (Calendar, error) {
	return Calendar{}, nil
}
func (m *mockAdminRepo) UpdateCalendar(_ context.Context, _ int64, _ CalendarUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) ListRouteSegments(_ context.Context, _ types.PaginationParams) ([]RouteSegment, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateRouteSegment(_ context.Context, _ RouteSegmentCreateParams) (RouteSegment, error) {
	return RouteSegment{}, nil
}
func (m *mockAdminRepo) UpdateRouteSegment(_ context.Context, _ int64, _ RouteSegmentUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) ListTravelTimeProfiles(_ context.Context, _ types.PaginationParams) ([]TravelTimeProfile, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateTravelTimeProfile(_ context.Context, _ TravelTimeProfileCreateParams) (TravelTimeProfile, error) {
	return TravelTimeProfile{}, nil
}
func (m *mockAdminRepo) UpdateTravelTimeProfile(_ context.Context, _ int64, _ TravelTimeProfileUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) ListRouteSegmentTravelTimes(_ context.Context, _ types.PaginationParams) ([]RouteSegmentTravelTime, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateRouteSegmentTravelTime(_ context.Context, _ RouteSegmentTravelTimeCreateParams) (RouteSegmentTravelTime, error) {
	return RouteSegmentTravelTime{}, nil
}
func (m *mockAdminRepo) UpdateRouteSegmentTravelTime(_ context.Context, _ int64, _ RouteSegmentTravelTimeUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) UpdateTripStatus(_ context.Context, _ int64, _ string) error {
	m.updateTripStatusCalls++
	return m.updateTripStatusErr
}
func (m *mockAdminRepo) TriggerManualGeneration(_ context.Context, _ int64, _ string) error {
	return nil
}
func (m *mockAdminRepo) GetScheduleConflicts(_ context.Context) ([]Conflict, error) {
	return nil, nil
}
func (m *mockAdminRepo) GetRouteTimeMatrix(_ context.Context) ([]MatrixEntry, error) {
	return nil, nil
}
func (m *mockAdminRepo) GetTripSeatAvailability(_ context.Context, _ int64) ([]SeatAvail, error) {
	return nil, nil
}
func (m *mockAdminRepo) ListVehicleSeats(_ context.Context, _ int64, _ types.PaginationParams) ([]VehicleSeat, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateVehicleSeat(_ context.Context, _ VehicleSeatCreateParams) (VehicleSeat, error) {
	return VehicleSeat{}, nil
}
func (m *mockAdminRepo) UpdateVehicleSeat(_ context.Context, _ int64, _ VehicleSeatUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) ListCalendarExceptions(_ context.Context, _ int64, _ types.PaginationParams) ([]CalendarException, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) CreateCalendarException(_ context.Context, _ CalendarExceptionCreateParams) (CalendarException, error) {
	return CalendarException{}, nil
}
func (m *mockAdminRepo) UpdateCalendarException(_ context.Context, _ int64, _ CalendarExceptionUpdateParams) error {
	return nil
}
func (m *mockAdminRepo) ListTrips(_ context.Context, _, _ string, _ int64, _ types.PaginationParams) ([]TripInstance, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) ListIncidents(_ context.Context, _ string, _ types.PaginationParams) ([]TripIncident, int, error) {
	return nil, 0, nil
}
func (m *mockAdminRepo) ListGenerationRuns(_ context.Context, _ types.PaginationParams) ([]GenerationRun, int, error) {
	return nil, 0, nil
}

// ctxWithRole construye un context que simula un JWT valido con el rol dado,
// tal como lo dejaria jwtauth.Verifier en produccion.
func ctxWithRole(t *testing.T, role string) context.Context {
	t.Helper()
	ja := jwtauth.New("HS256", []byte("admin-test-secret"), nil)
	claims := map[string]any{
		"user_id": float64(1),
		"role":    role,
	}
	token, _, err := ja.Encode(claims)
	require.NoError(t, err)
	return jwtauth.NewContext(context.Background(), token, nil)
}

func TestCreateStop_AdminRole_Success(t *testing.T) {
	repo := &mockAdminRepo{
		createStopResult: Stop{ID: 9, Code: "STP009", Name: "Paradero Norte", StopType: "PARADERO", Active: true},
	}
	svc := NewService(repo)

	got, err := svc.CreateStop(ctxWithRole(t, RoleADMIN), StopCreateParams{
		Code: "STP009", Name: "Paradero Norte", StopType: "PARADERO", Active: true,
	})
	require.NoError(t, err)
	assert.Equal(t, int64(9), got.ID)
	assert.Equal(t, "STP009", got.Code)
	assert.Equal(t, 1, repo.createStopCalls, "el servicio debe delegar al repositorio cuando el rol es ADMIN")
}

func TestCreateStop_NonAdminRole_ReturnsForbidden(t *testing.T) {
	repo := &mockAdminRepo{}
	svc := NewService(repo)

	_, err := svc.CreateStop(ctxWithRole(t, "WORKER"), StopCreateParams{
		Code: "STP010", Name: "X", StopType: "PARADERO", Active: true,
	})
	require.Error(t, err)
	var fe apperror.ForbiddenError
	require.True(t, errors.As(err, &fe), "rol no ADMIN debe mapear a ForbiddenError")
	assert.Equal(t, 0, repo.createStopCalls, "el repositorio no debe invocarse cuando el guard de rol falla")
}

func TestUpdateTripStatus_Success(t *testing.T) {
	repo := &mockAdminRepo{}
	svc := NewService(repo)

	err := svc.UpdateTripStatus(ctxWithRole(t, RoleADMIN), 33, "CANCELLED")
	require.NoError(t, err)
	assert.Equal(t, 1, repo.updateTripStatusCalls)
}

func TestUpdateTripStatus_NonAdminRole_ReturnsForbidden(t *testing.T) {
	repo := &mockAdminRepo{}
	svc := NewService(repo)

	err := svc.UpdateTripStatus(ctxWithRole(t, "DRIVER"), 33, "CANCELLED")
	require.Error(t, err)
	var fe apperror.ForbiddenError
	require.True(t, errors.As(err, &fe), "rol no ADMIN debe mapear a ForbiddenError")
	assert.Equal(t, 0, repo.updateTripStatusCalls)
}

func TestCreateUser_HashesPasswordBeforePersisting(t *testing.T) {
	repo := &mockAdminRepo{}
	svc := NewService(repo)

	plain := "Clave123"
	_, err := svc.CreateUser(ctxWithRole(t, RoleADMIN), UserCreateParams{
		EmployeeCode: "EMP001", DocumentNumber: "12345678", Password: plain,
		FullName: "Ana Torres", Role: "WORKER", Active: true,
	})
	require.NoError(t, err)
	require.Equal(t, 1, repo.createUserCalls)

	got := repo.receivedUserCreate.Password
	assert.NotEqual(t, plain, got, "el repositorio no debe recibir la password en texto plano")
	assert.NoError(t, bcrypt.CompareHashAndPassword([]byte(got), []byte(plain)),
		"el hash recibido por el repositorio debe verificar contra la password original")
}

func TestUpdateUser_PasswordProvidedOrOmitted(t *testing.T) {
	tests := []struct {
		name     string
		password string
	}{
		{name: "password provista se hashea", password: "NuevaClave1"},
		{name: "password vacia no se toca", password: ""},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			repo := &mockAdminRepo{}
			svc := NewService(repo)

			err := svc.UpdateUser(ctxWithRole(t, RoleADMIN), 7, UserUpdateParams{
				EmployeeCode: "EMP002", DocumentNumber: "87654321", Password: tt.password,
				FullName: "Luis Rios", Role: "DRIVER", Active: true,
			})
			require.NoError(t, err)
			require.Equal(t, 1, repo.updateUserCalls)

			got := repo.receivedUserUpdate.Password
			if tt.password == "" {
				assert.Empty(t, got, "sin password nueva, el repositorio no debe recibir hash")
				return
			}
			assert.NotEqual(t, tt.password, got, "el repositorio no debe recibir la password en texto plano")
			assert.NoError(t, bcrypt.CompareHashAndPassword([]byte(got), []byte(tt.password)),
				"el hash recibido por el repositorio debe verificar contra la password original")
		})
	}
}

func TestCreateUser_NonAdminRole_ReturnsForbidden(t *testing.T) {
	repo := &mockAdminRepo{}
	svc := NewService(repo)

	_, err := svc.CreateUser(ctxWithRole(t, "WORKER"), UserCreateParams{
		EmployeeCode: "EMP003", DocumentNumber: "11111111", Password: "Clave123",
		FullName: "X", Role: "WORKER", Active: true,
	})
	require.Error(t, err)
	var fe apperror.ForbiddenError
	require.True(t, errors.As(err, &fe), "rol no ADMIN debe mapear a ForbiddenError")
	assert.Equal(t, 0, repo.createUserCalls, "el repositorio no debe invocarse cuando el guard de rol falla")
}

var _ AdminRepository = (*mockAdminRepo)(nil)
