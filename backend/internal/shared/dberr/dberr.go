// Package dberr traduce errores del driver mysql a errores de dominio
// apperror. Centraliza el mapeo porque todos los modulos que llaman SPs
// (booking, driver, admin) necesitan la misma traduccion.
package dberr

import (
	"database/sql"
	"errors"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/go-sql-driver/mysql"
)

// errSignal es el codigo de MySQL para SIGNAL SQLSTATE '45000'
// (ER_SIGNAL_EXCEPTION). Los SPs del schema usan este signal para rechazar
// operaciones de negocio (asiento ocupado, viaje no publicado, etc.).
const errSignal = 1644

// errDuplicateEntry es ER_DUP_ENTRY. Backstop del indice unico
// uq_reservations_active_per_trip_worker (migracion 0003): Go valida
// "1 reserva activa por trabajador por viaje" con un SELECT EXISTS antes de
// llamar a sp_confirm_reservation, pero esa validacion deja una ventana de
// carrera entre dos requests concurrentes. Si ambas pasan el chequeo Go, el
// INSERT del SP choca contra el indice unico y este mapeo evita que la
// carrera se filtre como 500 en vez del mismo ConflictError esperado.
const errDuplicateEntry = 1062

// TranslateSP mapea errores que provienen de CALL de SPs. Los SIGNAL '45000'
// del negocio y los choques del indice unico de reserva activa se convierten
// en ConflictError (409) porque son choques de reglas, no errores de
// programacion. Otros errores se devuelven tal cual para que el handler los
// trate como InternalError via WriteJSONError.
func TranslateSP(err error) error {
	if err == nil {
		return nil
	}
	var mysqlErr *mysql.MySQLError
	if errors.As(err, &mysqlErr) {
		switch mysqlErr.Number {
		case errSignal:
			return apperror.ConflictError{Msg: mysqlErr.Message}
		case errDuplicateEntry:
			return apperror.ConflictError{Msg: "el trabajador ya tiene una reserva activa en este viaje"}
		}
	}
	return err
}

// NotFound envuelve sql.ErrNoRows como NotFoundError. Uso tipico:
//
//	err := db.QueryRowContext(ctx, q, id).Scan(...)
//	if err != nil {
//	    return dberr.NotFound(err, "reserva", id)
//	}
//
// Evita repetir el chequeo de sql.ErrNoRows en cada repositorio.
func NotFound(err error, entity string, id any) error {
	if errors.Is(err, sql.ErrNoRows) {
		return apperror.NotFoundError{Entity: entity, ID: id}
	}
	return err
}
