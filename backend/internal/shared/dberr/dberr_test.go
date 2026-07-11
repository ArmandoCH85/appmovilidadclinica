package dberr

import (
	"errors"
	"testing"

	"github.com/go-sql-driver/mysql"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

// TestTranslateSP_Signal45000_MapsToConflict cubre el mapeo existente de
// SIGNAL '45000' (errSignal=1644) de los SPs de negocio.
func TestTranslateSP_Signal45000_MapsToConflict(t *testing.T) {
	err := &mysql.MySQLError{Number: 1644, Message: "asiento ya reservado"}

	got := TranslateSP(err)

	var ce apperror.ConflictError
	require.True(t, errors.As(got, &ce))
	assert.Equal(t, "asiento ya reservado", ce.Msg)
}

// TestTranslateSP_DuplicateEntry_MapsToConflict cubre el backstop de BD para
// la regla 1-reserva-activa-por-trabajador-por-viaje (indice unico
// uq_reservations_active_per_trip_worker, migracion 0003). Si dos requests
// concurrentes pasan ambas el chequeo Go (CheckActiveReservation) antes de
// que cualquiera inserte, el INSERT de sp_confirm_reservation choca contra el
// indice unico con error 1062 (ER_DUP_ENTRY). TranslateSP debe mapearlo al
// mismo ConflictError amigable, no dejarlo caer como 500.
func TestTranslateSP_DuplicateEntry_MapsToConflict(t *testing.T) {
	err := &mysql.MySQLError{Number: 1062, Message: "Duplicate entry '5-77' for key 'uq_reservations_active_per_trip_worker'"}

	got := TranslateSP(err)

	var ce apperror.ConflictError
	require.True(t, errors.As(got, &ce), "error 1062 debe traducirse a ConflictError, no propagarse como 500")
	assert.Equal(t, "el trabajador ya tiene una reserva activa en este viaje", ce.Msg)
}

// TestTranslateSP_OtherMySQLError_PassesThrough confirma que errores no
// mapeados (ej. 1146 tabla no existe) siguen sin traducir, para que el
// handler los trate como InternalError generico.
func TestTranslateSP_OtherMySQLError_PassesThrough(t *testing.T) {
	err := &mysql.MySQLError{Number: 1146, Message: "Table 'x' doesn't exist"}

	got := TranslateSP(err)

	assert.Same(t, err, got)
}
