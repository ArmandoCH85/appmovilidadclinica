// Tests del ruteo de UpdateTripStatus (0021).
//
// COMPLETED y CANCELLED no pueden escribirse con un UPDATE pelado: tienen que
// pasar por las SPs de dominio, que cierran reservas e invitados y liberan los
// segmentos de asiento. Antes del fix, cerrar o cancelar un viaje desde el panel
// dejaba reservas BOARDED y asientos OCCUPIED colgados (viajes 133 y 195).
//
// El resto de los estados sigue con el UPDATE simple, asi que tambien se cubre
// ese camino para que el switch no se lo coma.
package admin

import (
	"context"
	"errors"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/go-sql-driver/mysql"
	"github.com/stretchr/testify/require"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

// signalClosed simula el SIGNAL SQLSTATE '45000' de las SPs (MySQL 1644).
var signalClosed = &mysql.MySQLError{Number: 1644, Message: "El viaje ya esta cerrado"}

func TestUpdateTripStatus_Completed_CallsAdminCloseSP(t *testing.T) {
	ctx := context.Background()
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	mock.ExpectExec(`CALL sp_admin_close_trip`).
		WithArgs(int64(33), int64(77)).
		WillReturnResult(sqlmock.NewResult(0, 0))

	repo := &adminRepository{db: db}
	require.NoError(t, repo.UpdateTripStatus(ctx, 33, "COMPLETED", 77))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestUpdateTripStatus_Cancelled_CallsCancelTripSP(t *testing.T) {
	ctx := context.Background()
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	mock.ExpectExec(`CALL sp_cancel_trip`).
		WithArgs(int64(33), sqlmock.AnyArg(), int64(77)).
		WillReturnResult(sqlmock.NewResult(0, 0))

	repo := &adminRepository{db: db}
	require.NoError(t, repo.UpdateTripStatus(ctx, 33, "CANCELLED", 77))
	require.NoError(t, mock.ExpectationsWereMet())
}

func TestUpdateTripStatus_OtherStatus_KeepsPlainUpdate(t *testing.T) {
	ctx := context.Background()
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	mock.ExpectExec(`UPDATE trip_instances`).
		WithArgs("PUBLISHED", int64(33)).
		WillReturnResult(sqlmock.NewResult(0, 1))

	repo := &adminRepository{db: db}
	require.NoError(t, repo.UpdateTripStatus(ctx, 33, "PUBLISHED", 77))
	require.NoError(t, mock.ExpectationsWereMet())
}

// IN_PROGRESS registra actual_start_at: sin eso los reportes de duracion y de
// demora de salida quedan vacios (filtran por esa columna).
func TestUpdateTripStatus_InProgress_SetsActualStartAt(t *testing.T) {
	ctx := context.Background()
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	mock.ExpectExec(`(?s)UPDATE trip_instances.*actual_start_at = COALESCE`).
		WithArgs(int64(33)).
		WillReturnResult(sqlmock.NewResult(0, 1))

	repo := &adminRepository{db: db}
	require.NoError(t, repo.UpdateTripStatus(ctx, 33, "IN_PROGRESS", 77))
	require.NoError(t, mock.ExpectationsWereMet())
}

// Un SIGNAL de la SP ('El viaje ya esta cerrado') debe salir como
// ConflictError (HTTP 409) con el mensaje del SP, no como 500 generico.
func TestUpdateTripStatus_ClosedTrip_SPErrorMapsToConflict(t *testing.T) {
	ctx := context.Background()
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	mock.ExpectExec(`CALL sp_admin_close_trip`).
		WithArgs(int64(33), int64(77)).
		WillReturnError(signalClosed)

	repo := &adminRepository{db: db}
	gotErr := repo.UpdateTripStatus(ctx, 33, "COMPLETED", 77)

	require.Error(t, gotErr)
	var conflict apperror.ConflictError
	require.True(t, errors.As(gotErr, &conflict),
		"se esperaba apperror.ConflictError (HTTP 409); se obtuvo %T: %v", gotErr, gotErr)
	require.Contains(t, conflict.Msg, "El viaje ya esta cerrado")
	require.NoError(t, mock.ExpectationsWereMet())
}

// El UPDATE simple sigue devolviendo NotFound si no afecta filas.
func TestUpdateTripStatus_OtherStatus_NoRows_ReturnsNotFound(t *testing.T) {
	ctx := context.Background()
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	mock.ExpectExec(`UPDATE trip_instances`).
		WithArgs("DRAFT", int64(33)).
		WillReturnResult(sqlmock.NewResult(0, 0))

	repo := &adminRepository{db: db}
	gotErr := repo.UpdateTripStatus(ctx, 33, "DRAFT", 77)

	require.Error(t, gotErr)
	var notFound apperror.NotFoundError
	require.True(t, errors.As(gotErr, &notFound),
		"se esperaba apperror.NotFoundError (HTTP 404); se obtuvo %T: %v", gotErr, gotErr)
	require.NoError(t, mock.ExpectationsWereMet())
}
