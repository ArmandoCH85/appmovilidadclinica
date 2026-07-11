// Package validate expone una instancia singleton de validator/v10 para todo
// el backend. validator/v10 cachea metadatos de structs, asi que reusar la
// misma instancia evita recostos por parsing de tags en cada peticion.
package validate

import (
	"errors"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/go-playground/validator/v10"
)

// Default es la instancia singleton. WithRequiredStructEnabled activa la
// validacion de campos requeridos sin tag explicito (mejor practica v10).
var Default = validator.New(validator.WithRequiredStructEnabled())

// ToAppError traduce un error de validator a apperror.ValidationError. Si el
// error no es del validador (p.ej. tipo no soportado) se envuelve como
// InternalError. Se reporta solo el primer campo invalido: para un MVP con
// <50 usuarios la granularidad por campo no aporta valor operativo.
func ToAppError(err error) error {
	if err == nil {
		return nil
	}
	var invalid *validator.InvalidValidationError
	if errors.As(err, &invalid) {
		return apperror.InternalError{Err: err}
	}
	var errs validator.ValidationErrors
	if errors.As(err, &errs) {
		if len(errs) > 0 {
			e := errs[0]
			return apperror.ValidationError{
				Field:  e.Field(),
				Reason: reasonFor(e),
			}
		}
	}
	return apperror.InternalError{Err: err}
}

// reasonFor arma un mensaje legible para el primer FieldError. Tag y Param
// bastan para que el cliente sepa que regla fallo y contra que valor.
func reasonFor(e validator.FieldError) string {
	if e.Param() == "" {
		return "fallo la regla " + e.Tag()
	}
	return "fallo la regla " + e.Tag() + " (valor esperado: " + e.Param() + ")"
}
