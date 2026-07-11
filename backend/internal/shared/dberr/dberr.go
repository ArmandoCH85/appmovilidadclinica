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

// TranslateSP mapea errores que provienen de CALL de SPs. Los SIGNAL '45000'
// del negocio se convierten en ConflictError (409) porque son choques de
// reglas, no errores de programacion. Otros errores se devuelven tal cual
// para que el handler los trate como InternalError via WriteJSONError.
func TranslateSP(err error) error {
	if err == nil {
		return nil
	}
	var mysqlErr *mysql.MySQLError
	if errors.As(err, &mysqlErr) && mysqlErr.Number == errSignal {
		return apperror.ConflictError{Msg: mysqlErr.Message}
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
