// Tests del repositorio del conductor para StartTrip.
//
// StartTrip tiene que registrar actual_start_at ademas de pasar el viaje a
// IN_PROGRESS: los reportes vw_duration_deviation y vw_delays_by_route_day
// filtran por esa columna, y hasta ahora nadie la escribia, asi que salian
// vacios en produccion (0 de 928 viajes la tenian).
package driver

import (
	"context"
	"errors"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/stretchr/testify/require"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

// El UPDATE debe setear las dos cosas en la misma sentencia, y seguir
// restringido a PUBLISHED/BOARDING.
func TestStartTrip_SetsActualStartAt(t *testing.T) {
	ctx := context.Background()
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	// (?s) para que el regex cruce saltos de linea: se exige que la sentencia
	// incluya actual_start_at, no solo el UPDATE.
	mock.ExpectExec(`(?s)UPDATE trip_instances.*actual_start_at = COALESCE`).
		WithArgs(int64(33)).
		WillReturnResult(sqlmock.NewResult(0, 1))

	repo := &driverRepository{db: db}
	require.NoError(t, repo.StartTrip(ctx, 33))
	require.NoError(t, mock.ExpectationsWereMet())
}

// Si el UPDATE no afecta filas (el viaje no estaba PUBLISHED/BOARDING), se
// resuelve el estado actual y se devuelve ConflictError con ese dato.
func TestStartTrip_NoRows_ReturnsConflictWithStatus(t *testing.T) {
	ctx := context.Background()
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	mock.ExpectExec(`UPDATE trip_instances`).
		WithArgs(int64(33)).
		WillReturnResult(sqlmock.NewResult(0, 0))
	mock.ExpectQuery(`SELECT status FROM trip_instances`).
		WithArgs(int64(33)).
		WillReturnRows(sqlmock.NewRows([]string{"status"}).AddRow("COMPLETED"))

	repo := &driverRepository{db: db}
	gotErr := repo.StartTrip(ctx, 33)

	require.Error(t, gotErr)
	var conflict apperror.ConflictError
	require.True(t, errors.As(gotErr, &conflict),
		"se esperaba apperror.ConflictError (HTTP 409); se obtuvo %T: %v", gotErr, gotErr)
	require.Contains(t, conflict.Msg, "COMPLETED")
	require.NoError(t, mock.ExpectationsWereMet())
}
