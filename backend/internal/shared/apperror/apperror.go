// Package apperror centraliza los errores de dominio del backend y su
// traducción a respuestas HTTP JSON.
//
// Se mantiene TODO en un solo archivo (decisión del ponytail-audit): seis tipos
// de error cortos más un helper de escritura. Separarlos en dos archivos
// (errors.go + http.go) agregaba indireccion sin valor para un MVP.
package apperror

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
)

// appError es el contrato común: cada error de dominio implementa error +
// HTTPStatus() para que WriteJSONError pueda despachar sin un switch enorme
// de tipos en cada handler.
type appError interface {
	error
	HTTPStatus() int
}

// NotFoundError indica que una entidad no existe. Lleva Entity + ID para
// producir mensajes accionables ("usuario 12 no encontrado").
type NotFoundError struct {
	Entity string
	ID     any
}

func (e NotFoundError) Error() string {
	return fmt.Sprintf("%s no encontrado con id %v", e.Entity, e.ID)
}
func (e NotFoundError) HTTPStatus() int { return http.StatusNotFound }

// ConflictError modela violaciones de unicidad o de reglas de negocio que
// chocan con estado existente (asiento ya reservado, plantilla duplicada).
type ConflictError struct {
	Msg string
}

func (e ConflictError) Error() string   { return e.Msg }
func (e ConflictError) HTTPStatus() int { return http.StatusConflict }

// ValidationError representa un campo individual que no pasó la validación.
// El detalle (Field + Reason) permite al cliente mostrar el error junto al
// input correspondiente; validator/v10 produce slices de éstos.
type ValidationError struct {
	Field  string
	Reason string
}

func (e ValidationError) Error() string {
	return fmt.Sprintf("campo %s invalido: %s", e.Field, e.Reason)
}
func (e ValidationError) HTTPStatus() int { return http.StatusUnprocessableEntity }

// UnauthorizedError se usa cuando falta o es inválida la credencial/JWT.
type UnauthorizedError struct {
	Reason string
}

func (e UnauthorizedError) Error() string   { return e.Reason }
func (e UnauthorizedError) HTTPStatus() int { return http.StatusUnauthorized }

// ForbiddenError indica que el JWT es válido pero el rol no puede ejecutar
// la operación (un WORKER intentando CRUD de administración, por ejemplo).
type ForbiddenError struct {
	Reason string
}

func (e ForbiddenError) Error() string   { return e.Reason }
func (e ForbiddenError) HTTPStatus() int { return http.StatusForbidden }

// InternalError envuelve un error inesperado (caída de BD, panic recuperado).
// El error original se loguea en el handler; al cliente sólo llega un mensaje
// genérico para no filtrar detalles internos.
type InternalError struct {
	Err error
}

func (e InternalError) Error() string {
	if e.Err != nil {
		return fmt.Sprintf("error interno: %v", e.Err)
	}
	return "error interno"
}
func (e InternalError) HTTPStatus() int { return http.StatusInternalServerError }

// errorBody es la forma JSON canónica de toda respuesta de error del API:
// {"error": {"code": 404, "message": "..."}}
type errorBody struct {
	Error struct {
		Code    int    `json:"code"`
		Message string `json:"message"`
	} `json:"error"`
}

// genericInternalMessage es el único texto que un InternalError (o cualquier
// error no tipado) puede exponer al cliente. El detalle real se loguea en
// slog, nunca en el cuerpo de la respuesta.
const genericInternalMessage = "error interno del servidor"

// WriteJSONError despacha err a su estado HTTP correspondiente y escribe el
// cuerpo JSON. Si err no es un appError conocido se asume 500 Internal Server
// Error, que es el fallback seguro para no dejar sin responder al cliente.
//
// Es el único punto donde los handlers traducen dominio→HTTP, evitando que
// cada handler repita w.WriteHeader + json.NewEncoder. También es el único
// punto que decide qué mensaje es seguro exponer: InternalError y cualquier
// error no tipado se logean server-side y responden con un mensaje genérico
// fijo — nunca err.Error(), que podría contener detalles de driver/DB/host.
func WriteJSONError(w http.ResponseWriter, err error) {
	status := http.StatusInternalServerError
	message := genericInternalMessage

	if ie, ok := err.(InternalError); ok {
		slog.Error("error interno", "error", ie.Err)
	} else if ae, ok := err.(appError); ok {
		status = ae.HTTPStatus()
		message = ae.Error()
	} else {
		slog.Error("error no tipado en WriteJSONError", "error", err)
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)

	body := errorBody{}
	body.Error.Code = status
	body.Error.Message = message

	_ = json.NewEncoder(w).Encode(body) // Si falla la serialización ya enviamos el status.
}
