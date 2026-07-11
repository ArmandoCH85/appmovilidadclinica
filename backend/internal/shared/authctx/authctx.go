// Package authctx extrae claims del contexto de peticion establecido por
// jwtauth.Verifier/Authenticator. Centraliza el parsing de tipos para no
// repetir type assertions en cada handler: jwtauth decodifica claims desde
// JSON, donde los numeros llegan como float64.
package authctx

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/go-chi/jwtauth/v5"
)

// UserIDFromContext extrae el claim user_id como int64. jwtauth lee claims
// desde el token JWT decodificado; los numericos llegan como float64 por la
// serializacion JSON. Se cubren los tipos que el driver puede devolver.
func UserIDFromContext(ctx context.Context) (int64, error) {
	_, claims, err := jwtauth.FromContext(ctx)
	if err != nil {
		return 0, fmt.Errorf("token sin claims: %w", err)
	}
	v, ok := claims["user_id"]
	if !ok {
		return 0, fmt.Errorf("claim user_id ausente")
	}
	switch val := v.(type) {
	case float64:
		return int64(val), nil
	case int64:
		return val, nil
	case int:
		return int64(val), nil
	case json.Number:
		return val.Int64()
	}
	return 0, fmt.Errorf("claim user_id tipo no soportado: %T", v)
}

// RoleFromContext extrae el claim role como string.
func RoleFromContext(ctx context.Context) (string, error) {
	_, claims, err := jwtauth.FromContext(ctx)
	if err != nil {
		return "", fmt.Errorf("token sin claims: %w", err)
	}
	v, ok := claims["role"]
	if !ok {
		return "", fmt.Errorf("claim role ausente")
	}
	s, ok := v.(string)
	if !ok {
		return "", fmt.Errorf("claim role tipo no soportado: %T", v)
	}
	return s, nil
}

// ClaimString extrae un claim string opcional (full_name, employee_code).
// Devuelve "" si no existe o no es string.
func ClaimString(ctx context.Context, key string) string {
	_, claims, _ := jwtauth.FromContext(ctx)
	v, ok := claims[key]
	if !ok {
		return ""
	}
	s, _ := v.(string)
	return s
}
