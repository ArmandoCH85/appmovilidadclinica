// Tests de UNIQUE constraint violation para los INSERT/UPDATE del repositorio
// admin. Garantiza que un choque contra una UNIQUE constraint (MySQL error
// 1062) se traduzca a apperror.ConflictError (HTTP 409) en vez del 500
// generico que devolveria el fmt.Errorf sin envolver.
//
// Set up: requiere que el dev agregue la dep go-sqlmock antes de correr:
//
//	go get github.com/DATA-DOG/go-sqlmock@latest
//	go mod tidy
//	go test ./internal/modules/admin/...
//
// Cobertura: 18 INSERT/UPDATE del repositorio admin que tocan tablas con
// UNIQUE constraints (stops, users, vehicles, routes, route_stops,
// templates, travel_profiles, vehicle_seats, route_segments,
// route_segment_travel_times, trip_instances).
package admin

import (
	"context"
	"errors"
	"regexp"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/go-sql-driver/mysql"
	"github.com/stretchr/testify/require"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

// duplicateEntry es el MySQLError que devuelve el driver cuando un INSERT/
// UPDATE choca contra una UNIQUE constraint (ER_DUP_ENTRY, error 1062).
var duplicateEntry = &mysql.MySQLError{
	Number:  1062,
	Message: "Duplicate entry 'X1' for key 'uq_transport_stops_code'",
}

// TestRepository_DuplicateKey_MapsToConflict recorre TODOS los INSERT/UPDATE
// del repositorio admin que tocan tablas con UNIQUE constraints. Cada caso
// configura sqlmock para que el INSERT/UPDATE falle con error 1062 y verifica
// que el metodo devuelva apperror.ConflictError (no un error generico que el
// handler traduciria a 500).
//
// Antes del fix: TODOS los casos fallan — los metodos envuelven con
// fmt.Errorf("creando X: %w", err) que no es un ConflictError.
// Despues del fix: TODOS pasan — usan dberr.TranslatePlainSQL(err, ...)
// que mapea 1062 a ConflictError.
//
// Nota: usamos sqlmock.AnyArg() para los argumentos del ExecContext porque
// el foco del test es el mapeo de error, no los parametros exactos de cada
// INSERT/UPDATE (esos ya estan cubiertos por integracion contra MariaDB).
func TestRepository_DuplicateKey_MapsToConflict(t *testing.T) {
	ctx := context.Background()

	cases := []struct {
		name  string
		sqlRe string
		call  func(*adminRepository) error
	}{
		// ---- CREATE ----
		{
			name:  "CreateStop",
			sqlRe: `INSERT INTO transport_stops`,
			call: func(r *adminRepository) error {
				_, err := r.CreateStop(ctx, StopCreateParams{
					Code: "S1", Name: "Stop 1", StopType: "SEDE", Active: true,
				})
				return err
			},
		},
		{
			name:  "CreateUser",
			sqlRe: `INSERT INTO users`,
			call: func(r *adminRepository) error {
				_, err := r.CreateUser(ctx, UserCreateParams{
					EmployeeCode: "EMP-1", DocumentNumber: "90000010", Password: "hash",
					FullName: "Name", Role: "WORKER", Active: true,
				})
				return err
			},
		},
		{
			name:  "CreateVehicle",
			sqlRe: `INSERT INTO vehicles`,
			call: func(r *adminRepository) error {
				_, err := r.CreateVehicle(ctx, VehicleCreateParams{
					InternalCode: "V1", Plate: "ABC-123", SeatCapacity: 12, Active: true,
				})
				return err
			},
		},
		{
			name:  "CreateRoute",
			sqlRe: `INSERT INTO transport_routes`,
			call: func(r *adminRepository) error {
				_, err := r.CreateRoute(ctx, RouteCreateParams{
					Code: "R1", Name: "Route 1", Direction: "IDA", Active: true,
				})
				return err
			},
		},
		{
			name:  "CreateRouteStop",
			sqlRe: `INSERT INTO route_stops`,
			call: func(r *adminRepository) error {
				_, err := r.CreateRouteStop(ctx, RouteStopCreateParams{
					RouteID: 1, StopID: 2, StopOrder: 1, DwellMinutes: 5,
					PickupAllowed: true, DropoffAllowed: true,
				})
				return err
			},
		},
		{
			name:  "CreateTemplate",
			sqlRe: `INSERT INTO trip_templates`,
			call: func(r *adminRepository) error {
				_, err := r.CreateTemplate(ctx, TemplateCreateParams{
					Code: "TPL-1", Name: "Template 1", RouteID: 1, ServiceCalendarID: 1,
					DepartureTime: "07:00:00",
					DefaultVehicleID: 1, DefaultDriverID: 2,
					ProfileReferenceMode:      "TRIP_DEPARTURE",
					BookingOpenDaysBefore:     7,
					BookingCloseMinutesBefore: 30,
					NoShowToleranceMinutes:    5,
					Active:                    true,
				})
				return err
			},
		},
		{
			name:  "CreateTravelTimeProfile",
			sqlRe: `INSERT INTO travel_time_profiles`,
			call: func(r *adminRepository) error {
				_, err := r.CreateTravelTimeProfile(ctx, TravelTimeProfileCreateParams{
					Code: "P1", Name: "Profile 1", Active: true,
				})
				return err
			},
		},
		{
			name:  "CreateVehicleSeat",
			sqlRe: `INSERT INTO vehicle_seats`,
			call: func(r *adminRepository) error {
				_, err := r.CreateVehicleSeat(ctx, VehicleSeatCreateParams{
					VehicleID: 1, SeatNumber: 1, SeatLabel: "1", Status: "ACTIVE",
				})
				return err
			},
		},
		{
			name:  "CreateRouteSegment",
			sqlRe: `INSERT INTO route_segments`,
			call: func(r *adminRepository) error {
				_, err := r.CreateRouteSegment(ctx, RouteSegmentCreateParams{
					RouteID: 1, SegmentOrder: 1,
					FromRouteStopID: 1, ToRouteStopID: 2, Active: true,
				})
				return err
			},
		},
		{
			name:  "CreateRouteSegmentTravelTime",
			sqlRe: `INSERT INTO route_segment_travel_times`,
			call: func(r *adminRepository) error {
				_, err := r.CreateRouteSegmentTravelTime(ctx, RouteSegmentTravelTimeCreateParams{
					RouteSegmentID: 1, ProfileID: 1, TravelMinutes: 15,
				})
				return err
			},
		},
		// ---- UPDATE ----
		{
			name:  "UpdateStop",
			sqlRe: `UPDATE transport_stops`,
			call: func(r *adminRepository) error {
				return r.UpdateStop(ctx, 1, StopUpdateParams{
					Code: "S1", Name: "Stop 1", StopType: "SEDE", Active: true,
				})
			},
		},
		{
			name:  "UpdateUser",
			sqlRe: `UPDATE users`,
			call: func(r *adminRepository) error {
				return r.UpdateUser(ctx, 1, UserUpdateParams{
					EmployeeCode: "EMP-1", DocumentNumber: "90000010",
					FullName: "Name", Role: "WORKER", Active: true,
				})
			},
		},
		{
			name:  "UpdateVehicle",
			sqlRe: `UPDATE vehicles`,
			call: func(r *adminRepository) error {
				return r.UpdateVehicle(ctx, 1, VehicleUpdateParams{
					InternalCode: "V1", Plate: "ABC-123", SeatCapacity: 12, Active: true,
				})
			},
		},
		{
			name:  "UpdateRoute",
			sqlRe: `UPDATE transport_routes`,
			call: func(r *adminRepository) error {
				return r.UpdateRoute(ctx, 1, RouteUpdateParams{
					Code: "R1", Name: "Route 1", Direction: "IDA", Active: true,
				})
			},
		},
		{
			name:  "UpdateRouteStop",
			sqlRe: `UPDATE route_stops`,
			call: func(r *adminRepository) error {
				return r.UpdateRouteStop(ctx, 1, RouteStopUpdateParams{
					RouteID: 1, StopID: 2, StopOrder: 1, DwellMinutes: 5,
					PickupAllowed: true, DropoffAllowed: true,
				})
			},
		},
		{
			name:  "UpdateTemplate",
			sqlRe: `UPDATE trip_templates`,
			call: func(r *adminRepository) error {
				return r.UpdateTemplate(ctx, 1, TemplateUpdateParams{
					Code: "TPL-1", Name: "Template 1", RouteID: 1, ServiceCalendarID: 1,
					DepartureTime: "07:00:00",
					DefaultVehicleID: 1, DefaultDriverID: 2,
					ProfileReferenceMode:      "TRIP_DEPARTURE",
					BookingOpenDaysBefore:     7,
					BookingCloseMinutesBefore: 30,
					NoShowToleranceMinutes:    5,
					Active:                    true,
				})
			},
		},
		{
			name:  "UpdateTravelProfile",
			sqlRe: `UPDATE travel_time_profiles`,
			call: func(r *adminRepository) error {
				return r.UpdateTravelTimeProfile(ctx, 1, TravelTimeProfileUpdateParams{
					Code: "P1", Name: "Profile 1", Active: true,
				})
			},
		},
		{
			name:  "UpdateVehicleSeat",
			sqlRe: `UPDATE vehicle_seats`,
			call: func(r *adminRepository) error {
				return r.UpdateVehicleSeat(ctx, 1, VehicleSeatUpdateParams{
					VehicleID: 1, SeatNumber: 1, SeatLabel: "1", Status: "ACTIVE",
				})
			},
		},
		{
			name:  "UpdateRouteSegment",
			sqlRe: `UPDATE route_segments`,
			call: func(r *adminRepository) error {
				return r.UpdateRouteSegment(ctx, 1, RouteSegmentUpdateParams{
					RouteID: 1, SegmentOrder: 1,
					FromRouteStopID: 1, ToRouteStopID: 2, Active: true,
				})
			},
		},
		{
			name:  "UpdateRouteSegmentTravelTime",
			sqlRe: `UPDATE route_segment_travel_times`,
			call: func(r *adminRepository) error {
				return r.UpdateRouteSegmentTravelTime(ctx, 1, RouteSegmentTravelTimeUpdateParams{
					RouteSegmentID: 1, ProfileID: 1, TravelMinutes: 15,
				})
			},
		},
		{
			name:  "UpdateTripStatus",
			sqlRe: `UPDATE trip_instances`,
			call: func(r *adminRepository) error {
				return r.UpdateTripStatus(ctx, 1, "PUBLISHED", 1)
			},
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			db, mock, err := sqlmock.New()
			require.NoError(t, err)
			defer db.Close()

			// Sin WithArgs: sqlmock acepta cualquier lista de argumentos. El
			// WithArgs(sqlmock.AnyArg()) anterior exigia exactamente UN
			// argumento, asi que todos los INSERT/UPDATE con mas de uno
			// fallaban con "arguments do not match" y el test nunca pasaba.
			mock.ExpectExec(tc.sqlRe).
				WillReturnError(duplicateEntry)

			repo := &adminRepository{db: db}
			gotErr := tc.call(repo)

			require.Error(t, gotErr, "metodo deberia devolver error (1062 UNIQUE violation)")
			var conflict apperror.ConflictError
			require.True(t,
				errors.As(gotErr, &conflict),
				"se esperaba apperror.ConflictError (HTTP 409); se obtuvo %T: %v",
				gotErr, gotErr,
			)

			require.NoError(t, mock.ExpectationsWereMet())
		})
	}
}

// TestRepository_GetTripIncidents_DateFieldSelector verifica que el rango de
// fechas del reporte #27 se aplique a la columna correcta: la fecha de
// servicio del viaje (service_date) o la de carga del ticket (reported_at).
// Regresion del reporte que devolvia vacio al filtrar por la fecha del viaje.
func TestRepository_GetTripIncidents_DateFieldSelector(t *testing.T) {
	ctx := context.Background()

	cases := []struct {
		name      string
		dateField string
		wantCol   string
	}{
		{"default usa reported_at", "", "DATE(reported_at)"},
		{"service_date explicito", "service_date", "DATE(service_date)"},
		{"reported_at explicito", "reported_at", "DATE(reported_at)"},
	}

	cols := []string{
		"incident_id", "trip_id", "trip_code", "service_date", "route_id",
		"route_code", "route_name", "direction", "incident_type", "description",
		"status", "reported_by_user_id", "reported_by_name", "reported_by_role",
		"reported_at", "resolved_at", "resolution_notes",
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			db, mock, err := sqlmock.New()
			require.NoError(t, err)
			defer db.Close()

			mock.ExpectQuery(regexp.QuoteMeta(tc.wantCol)).
				WithArgs("2026-10-05", "2026-10-05").
				WillReturnRows(sqlmock.NewRows(cols))

			repo := &adminRepository{db: db}
			out, err := repo.GetTripIncidents(ctx, 0, "", "", "", tc.dateField, "2026-10-05", "2026-10-05")
			require.NoError(t, err)
			require.Empty(t, out)
			require.NoError(t, mock.ExpectationsWereMet())
		})
	}
}