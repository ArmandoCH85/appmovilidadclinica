package apperror

import (
	"encoding/json"
	"errors"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestWriteJSONError_InternalError_HidesDetails verifica que un InternalError
// nunca filtra el texto del error envuelto al cliente. El bug original
// (fmt.Sprintf("error interno: %v", e.Err) usado directo como mensaje JSON)
// exponia detalles de driver/DB en la respuesta HTTP.
func TestWriteJSONError_InternalError_HidesDetails(t *testing.T) {
	secret := errors.New("dial tcp 10.0.0.5:3306: conexion rechazada, password=hunter2")
	w := httptest.NewRecorder()

	WriteJSONError(w, InternalError{Err: secret})

	assert.Equal(t, 500, w.Code)

	var body errorBody
	require.NoError(t, json.NewDecoder(w.Body).Decode(&body))
	assert.NotContains(t, body.Error.Message, "10.0.0.5", "no debe filtrar host/IP interno")
	assert.NotContains(t, body.Error.Message, "hunter2", "no debe filtrar secretos del error envuelto")
	assert.Equal(t, "error interno del servidor", body.Error.Message)
}

// TestWriteJSONError_KnownAppError_KeepsMessage verifica que los appError
// "seguros" (creados a proposito con mensaje accionable para el cliente) SI
// siguen llegando tal cual, solo InternalError se genericiza.
func TestWriteJSONError_KnownAppError_KeepsMessage(t *testing.T) {
	w := httptest.NewRecorder()

	WriteJSONError(w, ConflictError{Msg: "el trabajador ya tiene una reserva activa en este viaje"})

	assert.Equal(t, 409, w.Code)

	var body errorBody
	require.NoError(t, json.NewDecoder(w.Body).Decode(&body))
	assert.Equal(t, "el trabajador ya tiene una reserva activa en este viaje", body.Error.Message)
}
